package io.github.billstark001.minedriver.agent;

import io.github.billstark001.minedriver.api.DriverException;
import io.github.billstark001.minedriver.hooks.FrameClock;
import java.lang.instrument.Instrumentation;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Single lifecycle observer. Status is immutable cached evidence and never waits for the client.
 */
final class RuntimeObserver implements AutoCloseable {
  private final AgentConfig config;
  private final FrameTransformer frames;
  private final long startedMillis = System.currentTimeMillis();
  private volatile Map<String, Object> sample = Map.of();
  private volatile String state = "STARTING";
  private volatile String phase = "BOOTSTRAP";
  private volatile String waitingFor = "first completed client frame";
  private volatile String error;
  private volatile long sampledNanos;
  private volatile long frameChangedNanos = System.nanoTime();
  private volatile long lastFrame;
  private volatile long waitDeadline;
  private volatile boolean closed;
  private GameRuntime game;
  private RunReport report;

  RuntimeObserver(AgentConfig config, FrameTransformer frames) {
    this.config = config;
    this.frames = frames;
  }

  void bind(GameRuntime runtime) {
    game = runtime;
  }

  Map<String, Object> status() {
    var result = new LinkedHashMap<String, Object>(sample);
    result.put("runId", config.runId());
    result.put("state", state);
    result.put("phase", phase);
    result.put("waitingFor", waitingFor);
    result.put("error", error);
    result.put(
        "snapshotAgeMillis",
        sampledNanos == 0 ? null : (System.nanoTime() - sampledNanos) / 1_000_000);
    result.put("frame", FrameClock.count());
    result.put("frameHeartbeatAgeMillis", (System.nanoTime() - frameChangedNanos) / 1_000_000);
    result.put(
        "remainingMillis",
        waitDeadline == 0 ? null : Math.max(0, (waitDeadline - System.nanoTime()) / 1_000_000));
    return result;
  }

  Object awaitClient(Instrumentation instrumentation) throws java.io.IOException {
    Duration timeout =
        Duration.ofMillis(config.timeoutMillis() == 0 ? 300_000 : config.timeoutMillis());
    OperationDeadline budget = OperationDeadline.enter(timeout);
    waitDeadline = System.nanoTime() + OperationDeadline.remaining(timeout).toNanos();
    try {
      long nextCrashCheck = 0;
      while (true) {
        if (System.nanoTime() >= nextCrashCheck) {
          Path crash = StartupCrashReports.find(config.runDirectory(), startedMillis);
          if (crash != null)
            throw new DriverException("STARTUP_CRASH", "Native startup crash report: " + crash);
          nextCrashCheck = System.nanoTime() + Duration.ofMillis(500).toNanos();
        }
        if (frames.failure() != null)
          throw new DriverException("UNSUPPORTED_API", "Frame hook failed: " + frames.failure());
        if (FrameClock.count() > 0) {
          // Never initialize Minecraft from the observer before the game completes initialization.
          for (Class<?> type : instrumentation.getAllLoadedClasses())
            if (type.getName().equals("net.minecraft.client.Minecraft"))
              return Reflect.call(type, "getInstance");
        }
        try {
          OperationDeadline.remaining(timeout);
        } catch (DriverException expired) {
          throw new DriverException("STARTUP_TIMEOUT", "Client unavailable; " + status(), expired);
        }
        GameRuntime.pause();
      }
    } finally {
      waitDeadline = 0;
      budget.close();
    }
  }

  void attach(GameRuntime runtime, RunReport runReport) {
    game = runtime;
    report = runReport;
    Thread monitor =
        new Thread(
            () -> {
              while (!closed) {
                try {
                  game.client(
                      () -> {
                        observe();
                        return null;
                      },
                      Duration.ofMillis(500));
                } catch (RuntimeException failure) {
                  error = failure.toString();
                }
                try {
                  Thread.sleep(250);
                } catch (InterruptedException interrupted) {
                  Thread.currentThread().interrupt();
                  return;
                }
              }
            },
            "MineDriver observer");
    monitor.setDaemon(true);
    monitor.start();
  }

  Map<String, Object> observe() {
    var value = new LinkedHashMap<String, Object>();
    Object screen = game.screen();
    Object overlay = game.overlay();
    boolean focused =
        Boolean.TRUE.equals(Reflect.call(Reflect.call(game.minecraft, "getWindow"), "isFocused"));
    boolean inWorld = game.level() != null && game.player() != null;
    value.put("screen", screen == null ? null : screen.getClass().getName());
    value.put(
        "screenTitle", screen == null ? null : MinecraftUi.text(Reflect.call(screen, "getTitle")));
    value.put("overlay", overlay == null ? null : overlay.getClass().getName());
    value.put("focused", focused);
    value.put("paused", Reflect.call(game.minecraft, "isPaused"));
    value.put("inWorld", inWorld);
    value.put("gameplayReady", gameplayReady());
    String blocker =
        overlay != null ? "OVERLAY" : screen != null ? "SCREEN" : !inWorld ? "NO_WORLD" : null;
    value.put("blocker", blocker);
    sample = java.util.Collections.unmodifiableMap(value);
    sampledNanos = System.nanoTime();
    long frame = FrameClock.count();
    if (frame != lastFrame) {
      lastFrame = frame;
      frameChangedNanos = sampledNanos;
    }
    return sample;
  }

  boolean gameplayReady() {
    return game.level() != null
        && game.player() != null
        && game.screen() == null
        && game.overlay() == null
        && !Boolean.TRUE.equals(Reflect.call(game.minecraft, "isPaused"));
  }

  void awaitTitle() {
    await(
        "WAITING_TITLE",
        () ->
            game.client(
                () -> {
                  Object screen = game.screen();
                  if (screen == null || game.overlay() != null) return false;
                  String name = screen.getClass().getName();
                  if (name.equals("net.minecraft.client.gui.screens.TitleScreen")) return true;
                  if (name.equals("net.minecraft.client.gui.screens.AccessibilityOnboardingScreen"))
                    game.ui.activate(Map.of("selector", Map.of("key", "gui.continue")));
                  return false;
                },
                operationTimeout()),
        Duration.ofMillis(config.timeoutMillis() == 0 ? 300_000 : config.timeoutMillis()),
        "native title screen");
    state = "READY";
    phase = "TITLE_READY";
  }

  private Duration operationTimeout() {
    return Duration.ofMillis(config.operationTimeoutMillis());
  }

  private void requireUnblocked() {
    Object screen = game.screen();
    if (screen == null) return;
    String name = screen.getClass().getName();
    if (name.equals("net.neoforged.neoforge.client.gui.LoadingErrorScreen")) {
      var issues = StartupWarnings.read(game);
      long start = System.nanoTime();
      try {
        StartupWarnings.requireAccepted(
            issues, config.allowStartupWarnings(), config.acceptedStartupWarnings());
        if (game.overlay() == null) StartupWarnings.proceed(game);
        if (report != null)
          report.step("startup.warnings", "startup", System.nanoTime() - start, issues, null);
      } catch (RuntimeException failure) {
        if (report != null)
          report.step("startup.warnings", "startup", System.nanoTime() - start, issues, failure);
        throw failure;
      }
    } else if (name.equals("net.minecraft.client.gui.screens.DisconnectedScreen")) {
      throw new DriverException("WORLD_LOAD_FAILED", "Connection/loading terminated; " + status());
    }
  }

  void await(String nextPhase, BooleanSupplier condition, Duration timeout, String description) {
    OperationDeadline budget = OperationDeadline.enter(timeout);
    String beforePhase = phase;
    String beforeWait = waitingFor;
    long beforeDeadline = waitDeadline;
    phase = nextPhase;
    waitingFor = description;
    waitDeadline = System.nanoTime() + OperationDeadline.remaining(timeout).toNanos();
    try {
      while (true) {
        game.client(
            () -> {
              observe();
              requireUnblocked();
              return null;
            },
            OperationDeadline.remaining(operationTimeout()));
        if (condition.getAsBoolean()) return;
        OperationDeadline.remaining(timeout);
        GameRuntime.pause();
      }
    } catch (DriverException failure) {
      error = failure.toString();
      if (failure.code().equals("OPERATION_TIMEOUT"))
        throw new DriverException(
            "CONDITION_TIMEOUT", "Timed out waiting for " + description + "; " + status(), failure);
      throw failure;
    } finally {
      phase = beforePhase;
      waitingFor = beforeWait;
      waitDeadline = beforeDeadline;
      budget.close();
    }
  }

  void finish(Throwable failure) {
    state = failure == null ? "FINISHED" : "FAILED";
    phase = state;
    waitingFor = null;
    error = failure == null ? null : failure.toString();
  }

  @Override
  public void close() {
    closed = true;
  }
}
