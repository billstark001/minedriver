package io.github.billstark001.minedriver.agent;

import io.github.billstark001.minedriver.api.DriverException;
import io.github.billstark001.minedriver.hooks.FrameClock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** The game class loader is retained; no Minecraft type crosses the public API boundary. */
final class GameRuntime {
  final Object minecraft;
  final ClassLoader loader;
  final AgentConfig config;
  final FrameTransformer frames;
  final MinecraftUi ui;
  final MinecraftWorld world;
  final MinecraftMedia media;
  private final Map<String, Object> environment;

  GameRuntime(Object minecraft, AgentConfig config, FrameTransformer frames) {
    this.minecraft = minecraft;
    this.loader = minecraft.getClass().getClassLoader();
    this.config = config;
    this.frames = frames;
    environment = client(this::environment, Duration.ofMillis(config.operationTimeoutMillis()));
    String version = environment.get("minecraft").toString();
    if (!List.of("26.2", "26.3").contains(version) && !config.allowUnsupportedRuntime())
      throw new DriverException(
          "UNSUPPORTED_RUNTIME",
          "Minecraft " + version + " is outside the supported adapter matrix (26.2, 26.3)");
    if (!Boolean.TRUE.equals(environment.get("development")) && !config.allowProduction())
      throw new DriverException(
          "PRODUCTION_DISABLED",
          "Production or unclassified runtime requires allowProduction=true");
    ui = new MinecraftUi(this);
    world = new MinecraftWorld(this);
    media = new MinecraftMedia(this);
  }

  Class<?> type(String name) {
    return Reflect.type(loader, name);
  }

  Object gui() {
    return Reflect.field(minecraft, "gui");
  }

  Object screen() {
    return Reflect.call(gui(), "screen");
  }

  Object overlay() {
    return Reflect.call(gui(), "overlay");
  }

  void screen(Object screen) {
    Reflect.call(gui(), "setScreen", screen);
  }

  Object player() {
    return Reflect.field(minecraft, "player");
  }

  Object level() {
    return Reflect.field(minecraft, "level");
  }

  Object server() {
    return Reflect.call(minecraft, "getSingleplayerServer");
  }

  boolean onGameThread() {
    return Boolean.TRUE.equals(Reflect.call(minecraft, "isSameThread"));
  }

  <T> T client(Callable<T> action, Duration timeout) {
    return execute(minecraft, action, timeout);
  }

  <T> T server(Callable<T> action, Duration timeout) {
    Object server = client(this::server, timeout);
    if (server == null)
      throw new DriverException("NO_LOCAL_SERVER", "This operation needs an integrated server");
    return execute(server, action, timeout);
  }

  static <T> T execute(Object executor, Callable<T> action, Duration timeout) {
    if (timeout.isNegative() || timeout.isZero())
      throw Parameters.invalid("Timeout must be positive");
    if (Boolean.TRUE.equals(Reflect.call(executor, "isSameThread"))) {
      try {
        return action.call();
      } catch (Exception error) {
        throw Reflect.propagate(error);
      }
    }
    var future = new CompletableFuture<T>();
    Reflect.call(
        executor,
        "execute",
        (Runnable)
            () -> {
              if (future.isCancelled()) return;
              try {
                future.complete(action.call());
              } catch (Throwable error) {
                future.completeExceptionally(error);
              }
            });
    try {
      return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException error) {
      future.cancel(false);
      throw new DriverException(
          "GAME_THREAD_TIMEOUT", "Game thread did not complete operation in " + timeout, error);
    } catch (InterruptedException error) {
      future.cancel(false);
      Thread.currentThread().interrupt();
      throw new DriverException("CANCELLED", "Game operation interrupted", error);
    } catch (ExecutionException error) {
      throw Reflect.propagate(error.getCause());
    }
  }

  Map<String, Object> inspect() {
    var state = new LinkedHashMap<String, Object>(environment);
    Object screen = screen();
    state.put("screen", screen == null ? null : screen.getClass().getName());
    state.put(
        "screenTitle", screen == null ? null : MinecraftUi.text(Reflect.call(screen, "getTitle")));
    state.put("overlay", overlay() == null ? null : overlay().getClass().getName());
    state.put("inWorld", level() != null && player() != null);
    state.put("frame", FrameClock.count());
    state.put("frameHook", frames.installed());
    state.put("frameHookFailure", frames.failure());
    state.put("fps", Reflect.call(minecraft, "getFps"));
    state.put("frameTimeNanos", Reflect.call(minecraft, "getFrameTimeNs"));
    Object options = Reflect.field(minecraft, "options");
    state.put("language", Reflect.field(options, "languageCode"));
    if (player() != null) {
      var position = new LinkedHashMap<String, Object>();
      for (String axis : List.of("X", "Y", "Z"))
        position.put(axis.toLowerCase(), Reflect.call(player(), "get" + axis));
      position.put("uuid", Reflect.call(player(), "getUUID").toString());
      state.put("player", position);
      state.put(
          "dimension", Reflect.call(Reflect.call(level(), "dimension"), "identifier").toString());
    }
    return state;
  }

  private Map<String, Object> environment() {
    var result = new LinkedHashMap<String, Object>();
    Object version = Reflect.call(type("net.minecraft.SharedConstants"), "getCurrentVersion");
    result.put("minecraft", Reflect.call(version, "name"));
    result.put("java", System.getProperty("java.version"));
    result.put("agent", "0.1.0-SNAPSHOT");
    result.put("loader", "vanilla");
    result.put("development", false);
    try {
      Object fabric = Reflect.call(type("net.fabricmc.loader.api.FabricLoader"), "getInstance");
      result.put("loader", "fabric");
      result.put("development", Reflect.call(fabric, "isDevelopmentEnvironment"));
      var mods = new LinkedHashMap<String, String>();
      for (Object container : (Iterable<?>) Reflect.call(fabric, "getAllMods")) {
        Object metadata = Reflect.call(container, "getMetadata");
        mods.put(
            Reflect.call(metadata, "getId").toString(),
            Reflect.call(Reflect.call(metadata, "getVersion"), "getFriendlyString").toString());
      }
      result.put("mods", mods);
    } catch (DriverException error) {
      if (!error.code().equals("UNSUPPORTED_API")) throw error;
      try {
        Object mods = Reflect.call(type("net.neoforged.fml.ModList"), "get");
        result.put("loader", "neoforge");
        Class<?> environment = type("net.neoforged.fml.loading.FMLEnvironment");
        Object production = Reflect.optionalField(environment, "production");
        if (production == null && Reflect.hasMethod(environment, "isProduction", 0))
          production = Reflect.call(environment, "isProduction");
        result.put("development", Boolean.FALSE.equals(production));
        var loaded = new LinkedHashMap<String, String>();
        for (Object metadata : (Iterable<?>) Reflect.call(mods, "getMods"))
          loaded.put(
              Reflect.call(metadata, "getModId").toString(),
              Reflect.call(metadata, "getVersion").toString());
        result.put("mods", loaded);
      } catch (DriverException unsupported) {
        if (!unsupported.code().equals("UNSUPPORTED_API")) throw unsupported;
        result.put("loaderDetailsUnavailable", unsupported.getMessage());
      }
    }
    return result;
  }

  void awaitFrames(int count, Duration timeout) {
    if (onGameThread())
      throw new DriverException("BAD_THREAD", "Frame waits cannot run on the client thread");
    if (!frames.installed())
      throw new DriverException(
          "UNSUPPORTED_API", "Frame barrier unavailable: " + frames.failure());
    long target = FrameClock.count() + count;
    long deadline = System.nanoTime() + timeout.toNanos();
    while (FrameClock.count() < target) {
      if (System.nanoTime() >= deadline)
        throw new DriverException("RENDER_TIMEOUT", "No completed frame before deadline");
      pause();
    }
  }

  static void pause() {
    try {
      Thread.sleep(25);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new DriverException("CANCELLED", "Operation interrupted", interrupted);
    }
  }
}
