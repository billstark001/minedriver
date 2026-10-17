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
  private final long startupDeadline;
  private volatile Map<String, Object> sample = Map.of();
  private volatile Map<String, Object> environment =
      Map.of("agent", io.github.billstark001.minedriver.protocol.BuildInfo.VERSION);
  private volatile String state = "STARTING";
  private volatile String phase = "BOOTSTRAP";
  private volatile String error;
  private volatile String observationError;
  private volatile Map<String, Object> lastFailure;
  private volatile long sampledNanos;

  private record Waiting(String phase, String description, long deadline) {}

  private final java.util.concurrent.ConcurrentSkipListMap<Long, Waiting> waits =
      new java.util.concurrent.ConcurrentSkipListMap<>();
  private final java.util.concurrent.atomic.AtomicLong waitSequence =
      new java.util.concurrent.atomic.AtomicLong();
  private Thread monitor;
  private volatile boolean closed;
  private GameRuntime game;
  private RunReport report;
  private Object proceededWarningScreen;

  RuntimeObserver(AgentConfig config, FrameTransformer frames) {
    this.config = config;
    this.frames = frames;
    startupDeadline =
        System.nanoTime()
            + Duration.ofMillis(config.timeoutMillis() == 0 ? 300_000 : config.timeoutMillis())
                .toNanos();
  }

  void bind(GameRuntime runtime) {
    game = runtime;
    environment = runtime.environmentSnapshot();
  }

  Map<String, Object> status() {
    var result = new LinkedHashMap<String, Object>(environment);
    result.putAll(sample);
    result.put("runId", config.runId());
    result.put("state", state);
    var latestWait = waits.lastEntry();
    Waiting waiting = latestWait == null ? null : latestWait.getValue();
    boolean terminal = state.equals("FAILED") || state.equals("FINISHED");
    result.put("phase", terminal || waiting == null ? phase : waiting.phase());
    result.put("waitingFor", terminal || waiting == null ? null : waiting.description());
    result.put("error", error);
    result.put("observationError", observationError);
    result.put("lastFailure", lastFailure);
    result.put(
        "snapshotAgeMillis",
        sampledNanos == 0 ? null : (System.nanoTime() - sampledNanos) / 1_000_000);
    result.put("frame", FrameClock.count());
    long completed = FrameClock.completedNanos();
    result.put(
        "frameHeartbeatAgeMillis",
        completed == 0 ? null : (System.nanoTime() - completed) / 1_000_000);
    result.put(
        "remainingMillis",
        terminal || waiting == null
            ? null
            : Math.max(0, (waiting.deadline() - System.nanoTime()) / 1_000_000));
    return result;
  }

  Object awaitClient(Instrumentation instrumentation) throws java.io.IOException {
    Duration timeout = startupRemaining();
    OperationDeadline budget = OperationDeadline.enter(timeout);
    long waitId = waitSequence.incrementAndGet();
    try {
      waits.put(
          waitId,
          new Waiting(
              "BOOTSTRAP",
              "first completed client frame",
              System.nanoTime() + OperationDeadline.remaining(timeout).toNanos()));
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
            if (type.getName().equals("net.minecraft.client.Minecraft")) {
              Object instance = Reflect.call(type, "getInstance");
              if (instance != null) return instance;
            }
        }
        try {
          OperationDeadline.remaining(timeout);
        } catch (DriverException expired) {
          throw new DriverException("STARTUP_TIMEOUT", "Client unavailable; " + status(), expired);
        }
        GameRuntime.pause();
      }
    } catch (RuntimeException failure) {
      rememberFailure(failure, "BOOTSTRAP", "first completed client frame");
      throw failure;
    } finally {
      waits.remove(waitId);
      budget.close();
    }
  }

  void attach(GameRuntime runtime, RunReport runReport) {
    game = runtime;
    report = runReport;
    monitor =
        new Thread(
            () -> {
              while (!closed) {
                try {
                  game.client(
                      () -> {
                        if (!closed) observe();
                        return null;
                      },
                      Duration.ofMillis(500));
                } catch (RuntimeException failure) {
                  if (!closed) observationError = failure.toString();
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
    game.input.expire();
    Object screen = game.screen();
    Object overlay = game.overlay();
    boolean focused = game.focused();
    boolean inWorld = game.level() != null && game.player() != null;
    value.put("screen", screen == null ? null : screen.getClass().getName());
    value.put(
        "screenTitle", screen == null ? null : MinecraftUi.text(Reflect.call(screen, "getTitle")));
    value.put("overlay", overlay == null ? null : overlay.getClass().getName());
    value.put("focused", focused);
    value.put("input", game.input.status());
    value.put("paused", Reflect.call(game.minecraft, "isPaused"));
    value.put("inWorld", inWorld);
    value.put("gameplayReady", gameplayReady());
    String blocker =
        overlay != null ? "OVERLAY" : screen != null ? "SCREEN" : !inWorld ? "NO_WORLD" : null;
    value.put("blocker", blocker);
    sample = java.util.Collections.unmodifiableMap(value);
    sampledNanos = System.nanoTime();
    observationError = null;
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
        startupRemaining(),
        "native title screen");
    state = "READY";
    phase = "TITLE_READY";
  }

  private Duration startupRemaining() {
    long remaining = startupDeadline - System.nanoTime();
    if (remaining <= 0)
      throw new DriverException("STARTUP_TIMEOUT", "Startup deadline expired; " + status());
    return Duration.ofNanos(remaining);
  }

  void worldReady() {
    phase = "WORLD_READY";
  }

  void titleReady() {
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
      var issues = NeoForgeIssues.read(game);
      long start = System.nanoTime();
      try {
        NeoForgeIssues.requireAccepted(issues, config.acceptedStartupWarnings());
        if (game.overlay() == null && proceededWarningScreen != screen) {
          NeoForgeIssues.proceed(game);
          proceededWarningScreen = screen;
          if (report != null)
            report.step("startup.warnings", "startup", System.nanoTime() - start, issues, null);
        }
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
    long waitId = waitSequence.incrementAndGet();
    try {
      waits.put(
          waitId,
          new Waiting(
              nextPhase,
              description,
              System.nanoTime() + OperationDeadline.remaining(timeout).toNanos()));
      while (true) {
        game.client(
            () -> {
              observe();
              requireUnblocked();
              return null;
            },
            OperationDeadline.remaining(operationTimeout()));
        if (condition.getAsBoolean()) {
          OperationDeadline.remaining(timeout);
          if (!state.equals("FAILED") && !state.equals("FINISHED")) error = null;
          return;
        }
        OperationDeadline.remaining(timeout);
        GameRuntime.pause();
      }
    } catch (RuntimeException | Error failure) {
      rememberFailure(failure, nextPhase, description);
      if (failure instanceof DriverException driver && driver.code().equals("OPERATION_TIMEOUT"))
        throw new DriverException(
            "CONDITION_TIMEOUT", "Timed out waiting for " + description + "; " + status(), failure);
      throw failure;
    } finally {
      waits.remove(waitId);
      budget.close();
    }
  }

  private void rememberFailure(Throwable failure, String failedPhase, String description) {
    if (state.equals("FAILED") || state.equals("FINISHED")) return;
    error = failure.toString();
    var evidence = new LinkedHashMap<String, Object>(sample);
    evidence.put("phase", failedPhase);
    evidence.put("waitingFor", description);
    evidence.put(
        "code", failure instanceof DriverException driver ? driver.code() : "INTERNAL_ERROR");
    lastFailure = java.util.Collections.unmodifiableMap(evidence);
  }

  void finish(Throwable failure) {
    if (failure != null && lastFailure == null) {
      var latest = waits.lastEntry();
      rememberFailure(
          failure,
          latest == null ? phase : latest.getValue().phase(),
          latest == null ? null : latest.getValue().description());
    }
    state = failure == null ? "FINISHED" : "FAILED";
    phase = state;
    error = failure == null ? null : failure.toString();
  }

  @Override
  public void close() {
    closed = true;
    if (monitor != null) monitor.interrupt();
  }
}
