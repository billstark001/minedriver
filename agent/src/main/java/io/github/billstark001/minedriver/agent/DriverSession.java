package io.github.billstark001.minedriver.agent;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import io.github.billstark001.minedriver.api.Driver;
import io.github.billstark001.minedriver.api.DriverException;
import io.github.billstark001.minedriver.api.DriverExtension;
import io.github.billstark001.minedriver.api.ExtensionRegistry;
import io.github.billstark001.minedriver.api.ProbeContext;
import io.github.billstark001.minedriver.api.Scenario;
import io.github.billstark001.minedriver.protocol.Json;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

final class DriverSession implements Driver, AutoCloseable {
  final AgentConfig config;
  final GameRuntime game;
  final CommandRegistry registry;
  final RunReport report;
  final ProbeContext context;
  final Duration timeout;
  private final Profiler profiler = new Profiler();
  private final URLClassLoader scenarios;
  private final AtomicBoolean executing = new AtomicBoolean();
  private volatile String scenarioStatus = "IDLE";
  private volatile String scenarioError;
  private volatile boolean closing;

  DriverSession(AgentConfig config, GameRuntime game, CommandRegistry registry, RunReport report)
      throws Exception {
    this.config = config;
    this.game = game;
    this.registry = registry;
    this.report = report;
    timeout = Duration.ofMillis(config.operationTimeoutMillis());
    context = new ProbeContext(this, timeout);
    URL[] urls = new URL[config.scenarioClasspath().size()];
    for (int i = 0; i < urls.length; i++)
      urls[i] = Path.of(config.scenarioClasspath().get(i)).toUri().toURL();
    // Minecraft comes from its loader; the API is always the agent's copy, even if the game
    // classpath happens to contain an API JAR. This prevents Scenario instanceof failures.
    ClassLoader parent =
        new ClassLoader(game.loader) {
          @Override
          protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("io.github.billstark001.minedriver.api."))
              return Driver.class.getClassLoader().loadClass(name);
            return super.loadClass(name, resolve);
          }
        };
    scenarios = new URLClassLoader(urls, parent);
    builtins();
    ExtensionRegistry extensionRegistry =
        new ExtensionRegistry() {
          @Override
          public void register(
              String command, String description, boolean readOnly, Handler handler) {
            if (!command.startsWith("custom."))
              throw new IllegalArgumentException("Extension commands must use custom.*");
            registry.register(
                command,
                description,
                readOnly,
                "extension",
                parameters -> {
                  Object result = handler.handle(context, parameters);
                  JsonValues.validate(result);
                  return result;
                });
          }

          @Override
          public void identify(Object widget, String stableId) {
            game.ui.identify(widget, stableId);
          }
        };
    for (String name : config.extensions())
      ((DriverExtension) instance(name)).register(extensionRegistry);
  }

  private void builtins() {
    register(
        "network.inspect",
        "Inspect play connection and native packet rate counters",
        true,
        "read",
        p -> game.client(() -> MinecraftNetwork.inspect(game), timeout));
    register(
        "network.command",
        "Send a normal client command packet (assert the server outcome separately)",
        false,
        "input",
        p -> game.client(() -> MinecraftNetwork.command(game, p), timeout));
    register(
        "world.connect",
        "Connect through the native multiplayer screen to an explicit server address",
        false,
        "network",
        p -> MinecraftNetwork.connect(game, p, duration(p)));
    register(
        "world.snapshot",
        "Read authoritative integrated-server blocks and player inventory",
        true,
        "read",
        p -> game.world.snapshot(p, timeout));
    register(
        "session.inspect",
        "Inspect game, loader, screen, player and render state",
        true,
        "read",
        p -> game.client(game::inspect, timeout));
    register(
        "session.commands",
        "List available commands and action channels",
        true,
        "read",
        p -> registry.descriptions());
    register(
        "session.capabilities",
        "Report supported and experimental capabilities",
        true,
        "read",
        p ->
            Map.of(
                "runtimeVersions",
                List.of("26.2", "26.3"),
                "frameBarrier",
                game.frames.installed(),
                "reflection",
                config.unsafeReflection(),
                "mod",
                false,
                "nativeFrameworks",
                List.of("fabric", "neoforge"),
                "productionExperimental",
                config.allowProduction()));
    register(
        "session.close",
        "Finish the session and stop this client",
        false,
        "lifecycle",
        p -> {
          if (executing.get())
            throw new DriverException("SCENARIO_BUSY", "Wait for the scenario before closing");
          closing = true;
          return true;
        });
    register(
        "ui.tree",
        "Inspect native screen widget tree",
        true,
        "read",
        p ->
            game.client(
                () -> game.ui.tree(Parameters.integer(p, "limit", 8192, 1, 8192)), timeout));
    register(
        "ui.activate",
        "Activate exactly one enabled widget using its semantic action",
        false,
        "semantic",
        p -> game.client(() -> game.ui.activate(p), timeout));
    register(
        "ui.text",
        "Set an edit box value through its semantic setter",
        false,
        "semantic",
        p -> game.client(() -> game.ui.text(p), timeout));
    register(
        "ui.settings",
        "Open Mod Menu, NeoForge or AutoConfig settings factory",
        false,
        "semantic",
        p -> game.client(() -> SettingsScreens.open(game, p), timeout));
    register(
        "ui.title",
        "Open the native title screen",
        false,
        "semantic",
        p ->
            game.client(
                () -> {
                  if (game.level() != null)
                    throw new DriverException("WORLD_ALREADY_OPEN", "Disconnect first");
                  game.screen(
                      Reflect.create(game.type("net.minecraft.client.gui.screens.TitleScreen")));
                  return true;
                },
                timeout));
    register(
        "input.click",
        "Dispatch native screen mouse press and release",
        false,
        "input",
        p -> game.client(() -> game.ui.click(p), timeout));
    register(
        "input.scroll",
        "Dispatch native screen scrolling",
        false,
        "input",
        p -> game.client(() -> game.ui.scroll(p), timeout));
    register(
        "input.key",
        "Dispatch keyboard handler press, hold completed frames, release",
        false,
        "input",
        p -> game.media.key(p, timeout));
    register(
        "input.text",
        "Dispatch keyboard handler text input",
        false,
        "input",
        p -> game.client(() -> game.media.inputText(p), timeout));
    register(
        "render.await",
        "Wait for completed render frames",
        true,
        "barrier",
        p -> {
          game.awaitFrames(Parameters.integer(p, "frames", 2, 1, 1200), duration(p));
          return true;
        });
    register(
        "screenshot.capture",
        "Capture and validate an asynchronously written PNG",
        true,
        "read",
        p -> game.media.screenshot(p, duration(p)));
    register(
        "screenshot.compare",
        "Compare pixels with crop, masks, tolerance and a diff artifact",
        true,
        "assertion",
        p -> VisualComparison.compare(p, config.output()));
    register(
        "resources.language",
        "Select language and await resource reload completion",
        false,
        "semantic",
        p -> game.media.language(p, duration(p)));
    register(
        "window.configure",
        "Inspect or change window size and GUI scale",
        false,
        "semantic",
        p -> game.client(() -> game.media.window(p), timeout));
    register(
        "world.create",
        "Create an isolated native singleplayer fixture",
        false,
        "semantic",
        p -> game.world.create(p, duration(p)));
    register(
        "world.disconnect",
        "Save and disconnect the integrated world",
        false,
        "semantic",
        p -> game.world.disconnect(duration(p)));
    register(
        "world.command",
        "Execute Brigadier command on the integrated server",
        false,
        "server",
        p -> game.world.command(Parameters.string(p, "command", null), timeout));
    register(
        "world.fixture",
        "Set authoritative position, camera, inventory, blocks and commands",
        false,
        "server",
        p -> game.world.fixture(p, duration(p)));
    register(
        "profile.start",
        "Begin frame/GC sampling and optional bounded JFR recording",
        false,
        "profile",
        profiler::start);
    register(
        "profile.stop",
        "Finish frame/GC/server timing and JFR recording",
        false,
        "profile",
        p -> profiler.stop(config.output(), p, game));
    register(
        "assert.state",
        "Require a JSON path in session state to equal a value",
        true,
        "assertion",
        p -> {
          requireState(p);
          return true;
        });
    register(
        "wait.state",
        "Await state value with a bounded deadline",
        true,
        "barrier",
        p -> {
          MinecraftWorld.await(() -> matches(p), duration(p), Parameters.string(p, "path", null));
          return true;
        });
    register(
        "assert.widget",
        "Require exactly one visible enabled widget",
        true,
        "assertion",
        p -> game.client(() -> game.ui.select(Parameters.object(p, "selector")).node(), timeout));
    register(
        "wait.widget",
        "Await exactly one visible enabled widget",
        true,
        "barrier",
        p -> {
          MinecraftWorld.await(
              () -> {
                try {
                  game.client(() -> game.ui.select(Parameters.object(p, "selector")), timeout);
                  return true;
                } catch (DriverException e) {
                  if (!List.of("WIDGET_NOT_FOUND", "WIDGET_DISABLED").contains(e.code())) throw e;
                  return false;
                }
              },
              duration(p),
              "widget");
          return true;
        });
    register(
        "scenario.status",
        "Inspect asynchronous scenario execution",
        true,
        "read",
        p ->
            Map.of(
                "status",
                executing.get() ? "RUNNING" : scenarioStatus,
                "running",
                executing.get(),
                "error",
                scenarioError == null ? "" : scenarioError));
    register(
        "scenario.run",
        "Start a configured Java scenario or a supplied JSON plan",
        false,
        "scenario",
        p -> {
          if (!executing.compareAndSet(false, true))
            throw new DriverException("SCENARIO_BUSY", "A scenario is already running");
          scenarioStatus = "RUNNING";
          Thread worker =
              new Thread(
                  () -> {
                    try {
                      run(
                          p.containsKey("plan")
                              ? Json.GSON.toJsonTree(p.get("plan")).getAsJsonObject()
                              : config.plan(),
                          p.containsKey("class")
                              ? List.of(Parameters.string(p, "class", null))
                              : config.scenarios());
                    } catch (Throwable error) {
                      scenarioError = error.toString();
                    } finally {
                      executing.set(false);
                    }
                  },
                  "MineDriver scenario");
          worker.setDaemon(true);
          worker.start();
          return Map.of("accepted", true);
        });
    if (config.unsafeReflection())
      register(
          "java.invoke",
          "Experimental explicit public static reflection",
          false,
          "unsafe",
          p ->
              game.client(
                  () ->
                      Reflect.call(
                          game.type(Parameters.string(p, "class", null)),
                          Parameters.string(p, "method", null),
                          Parameters.list(p, "arguments").toArray()),
                  timeout));
  }

  private void register(
      String name,
      String description,
      boolean readOnly,
      String channel,
      CommandRegistry.Handler handler) {
    registry.register(name, description, readOnly, channel, handler);
  }

  private Duration duration(Map<String, Object> p) {
    return Duration.ofMillis(
        Parameters.integer(
            p, "timeoutMillis", (int) config.operationTimeoutMillis(), 1, 3_600_000));
  }

  private boolean matches(Map<String, Object> p) {
    JsonElement state = Json.GSON.toJsonTree(game.client(game::inspect, timeout));
    for (String part : Parameters.string(p, "path", null).split("\\.")) {
      if (!state.isJsonObject() || !state.getAsJsonObject().has(part)) return false;
      state = state.getAsJsonObject().get(part);
    }
    if (!p.containsKey("equals")) throw Parameters.invalid("equals is required");
    return state.equals(Json.GSON.toJsonTree(p.get("equals")));
  }

  private void requireState(Map<String, Object> p) {
    if (!matches(p)) throw new DriverException("ASSERTION_FAILED", "State mismatch: " + p);
  }

  private Object instance(String name) throws Exception {
    return scenarios.loadClass(name).getConstructor().newInstance();
  }

  void ready() {
    MinecraftWorld.await(
        () ->
            game.client(
                () -> {
                  if (game.overlay() != null || game.screen() == null) return false;
                  String type = game.screen().getClass().getName();
                  if (type.endsWith(".TitleScreen")) return true;
                  if (type.endsWith(".AccessibilityOnboardingScreen")) {
                    game.ui.activate(Map.of("selector", Map.of("key", "gui.continue")));
                    return false;
                  }
                  if (type.endsWith(".LoadingErrorScreen") && config.allowStartupWarnings()) {
                    game.ui.activate(Map.of("selector", Map.of("key", "fml.button.proceed")));
                    return false;
                  }
                  return false;
                },
                timeout),
        Duration.ofMillis(config.timeoutMillis() == 0 ? 300_000 : config.timeoutMillis()),
        "title screen (startup dialogs are not silently accepted)");
  }

  void run(JsonObject plan, List<String> classes) throws Exception {
    scenarioStatus = "RUNNING";
    scenarioError = null;
    ClassLoader before = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(scenarios);
    try {
      if (plan.has("steps"))
        for (JsonElement item : plan.getAsJsonArray("steps")) {
          JsonObject step = item.getAsJsonObject();
          Map<String, Object> p =
              step.has("parameters")
                  ? Json.GSON.fromJson(
                      step.get("parameters"), new TypeToken<Map<String, Object>>() {}.getType())
                  : Map.of();
          call(step.get("command").getAsString(), p);
        }
      for (String name : classes) {
        long start = System.nanoTime();
        try {
          ((Scenario) instance(name)).run(context);
          report.step(name, "scenario", System.nanoTime() - start, true, null);
        } catch (Throwable failure) {
          report.step(name, "scenario", System.nanoTime() - start, null, failure);
          throw failure;
        }
      }
      scenarioStatus = "PASS";
    } catch (Throwable failure) {
      scenarioStatus = "FAIL";
      scenarioError = failure.toString();
      throw failure;
    } finally {
      Thread.currentThread().setContextClassLoader(before);
    }
  }

  boolean isClosing() {
    return closing;
  }

  void requireSuccessfulScenario() {
    if ("FAIL".equals(scenarioStatus)) throw new DriverException("SCENARIO_FAILED", scenarioError);
  }

  @Override
  public Object call(String command, Map<String, ?> parameters) {
    if (isGameThread())
      throw new DriverException(
          "BAD_THREAD",
          "Issue commands from a scenario/RPC worker, use onClient only for short actions");
    try {
      Map<String, Object> p = new java.util.LinkedHashMap<>();
      parameters.forEach(p::put);
      Object result = registry.invoke(command, p);
      return result;
    } catch (Throwable failure) {
      throw Reflect.propagate(failure);
    }
  }

  @Override
  public <T> T onClient(Callable<T> action, Duration limit) {
    return game.client(action, limit);
  }

  @Override
  public <T> T onServer(Callable<T> action, Duration limit) {
    return game.server(action, limit);
  }

  @Override
  public boolean isGameThread() {
    return game.onGameThread()
        || (game.server() != null
            && Boolean.TRUE.equals(Reflect.call(game.server(), "isSameThread")));
  }

  @Override
  public Path outputDirectory() {
    return config.output();
  }

  @Override
  public void close() throws java.io.IOException {
    profiler.close();
    scenarios.close();
  }
}
