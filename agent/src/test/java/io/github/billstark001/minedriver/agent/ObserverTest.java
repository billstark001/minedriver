package io.github.billstark001.minedriver.agent;

import static org.junit.jupiter.api.Assertions.*;

import io.github.billstark001.minedriver.api.DriverException;
import io.github.billstark001.minedriver.protocol.Json;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ObserverTest {
  @TempDir Path directory;

  private GameRuntime runtime(String id) throws Exception {
    Path file = directory.resolve(id + ".json");
    Json.write(
        file,
        Map.of(
            "protocol",
            1,
            "runId",
            id,
            "outputDirectory",
            directory.toString(),
            "allowProduction",
            true));
    var config = AgentConfig.read(file);
    var frames = new FrameTransformer();
    return new GameRuntime(
        new net.minecraft.client.Minecraft(), config, frames, new RuntimeObserver(config, frames));
  }

  @Test
  @SuppressWarnings("unchecked")
  void unknownNativeAdaptersNeverGuessGlfw() throws Exception {
    var game = runtime("unknown-adapter");
    ((Map<String, Object>) Reflect.field(game, "environment")).put("minecraft", "26.4");
    assertEquals("UNSUPPORTED_API", assertThrows(DriverException.class, game::focused).code());
  }

  @Test
  void constructorFailureRestoresOptionsAndDetachesTheRenderListener() throws Exception {
    Path file = directory.resolve("failed-extension.json");
    Json.write(
        file,
        Map.of(
            "protocol",
            1,
            "runId",
            "failed-extension",
            "outputDirectory",
            directory.toString(),
            "allowProduction",
            true,
            "backgroundExecution",
            true,
            "extensions",
            java.util.List.of("missing.Extension")));
    var config = AgentConfig.read(file);
    var frames = new FrameTransformer();
    var client = new net.minecraft.client.Minecraft();
    var game = new GameRuntime(client, config, frames, new RuntimeObserver(config, frames));
    assertThrows(
        ClassNotFoundException.class,
        () -> new DriverSession(config, game, new CommandRegistry(), new RunReport(config)));
    assertTrue(client.options.pauseOnLostFocus);
    assertNull(
        Reflect.field(io.github.billstark001.minedriver.hooks.VisualHooks.class, "listener"));
  }

  @Test
  void expiredEntryAndOrdinaryPredicateFailuresRestoreWaitState() throws Exception {
    var observer = runtime("deadline-entry").observer;
    var budget = OperationDeadline.enter(Duration.ofMillis(1));
    try {
      Thread.sleep(5);
      assertEquals(
          "CONDITION_TIMEOUT",
          assertThrows(
                  DriverException.class,
                  () ->
                      observer.await("EXPIRED", () -> true, Duration.ofSeconds(1), "expired entry"))
              .code());
    } finally {
      budget.close();
    }
    assertNull(observer.status().get("waitingFor"));
    assertTrue(OperationDeadline.remaining(Duration.ofSeconds(1)).toMillis() > 900);
    assertThrows(
        IllegalStateException.class,
        () ->
            observer.await(
                "PREDICATE",
                () -> {
                  throw new IllegalStateException("predicate failed");
                },
                Duration.ofSeconds(1),
                "predicate"));
    assertEquals("PREDICATE", ((Map<?, ?>) observer.status().get("lastFailure")).get("phase"));
    assertEquals("INTERNAL_ERROR", ((Map<?, ?>) observer.status().get("lastFailure")).get("code"));
    assertNull(observer.status().get("waitingFor"));
    assertEquals("BOOTSTRAP", observer.status().get("phase"));
  }

  @Test
  void heartbeatTracksCompletedFramesIndependentlyOfSampling() throws Exception {
    var observer = runtime("heartbeat").observer;
    assertEquals(
        io.github.billstark001.minedriver.protocol.BuildInfo.VERSION,
        observer.status().get("agent"));
    assertEquals("26.3", observer.status().get("minecraft"));
    observer.observe();
    Thread.sleep(30);
    io.github.billstark001.minedriver.hooks.FrameClock.begin();
    io.github.billstark001.minedriver.hooks.FrameClock.end();
    var status = observer.status();
    assertTrue(
        ((Number) status.get("snapshotAgeMillis")).longValue()
            > ((Number) status.get("frameHeartbeatAgeMillis")).longValue());
  }

  private static void pump(
      net.minecraft.client.Minecraft client, java.util.function.BooleanSupplier ready)
      throws Exception {
    long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
    while (!ready.getAsBoolean()) {
      if (System.nanoTime() > deadline) fail("Client fixture did not reach condition");
      client.drainActions();
      Thread.sleep(1);
    }
  }

  @Test
  void concurrentWaitCompletionCannotClobberAnotherWaitOrTerminalState() throws Exception {
    var game = runtime("concurrent-waits");
    var observer = game.observer;
    var client = (net.minecraft.client.Minecraft) game.minecraft;
    var firstDone = new java.util.concurrent.atomic.AtomicBoolean();
    var secondDone = new java.util.concurrent.atomic.AtomicBoolean();
    var workers = Executors.newFixedThreadPool(2);
    try {
      var first =
          workers.submit(
              () -> observer.await("FIRST", firstDone::get, Duration.ofSeconds(5), "first"));
      pump(client, () -> "FIRST".equals(observer.status().get("phase")));
      var second =
          workers.submit(
              () -> observer.await("SECOND", secondDone::get, Duration.ofSeconds(5), "second"));
      pump(client, () -> "SECOND".equals(observer.status().get("phase")));
      firstDone.set(true);
      pump(client, first::isDone);
      first.get();
      assertEquals("SECOND", observer.status().get("phase"));
      observer.finish(new IllegalStateException("terminal failure"));
      assertEquals("SECOND", ((Map<?, ?>) observer.status().get("lastFailure")).get("phase"));
      secondDone.set(true);
      pump(client, second::isDone);
      second.get();
      assertEquals("FAILED", observer.status().get("phase"));
      assertTrue(observer.status().get("error").toString().contains("terminal failure"));
      assertNull(observer.status().get("waitingFor"));
    } finally {
      workers.shutdownNow();
    }
  }

  @Test
  void focusUsesNativeFlagsRatherThanTheClientsInitialCachedValue() throws Exception {
    Path file = directory.resolve("focus.json");
    Json.write(
        file,
        Map.of(
            "protocol",
            1,
            "runId",
            "focus",
            "outputDirectory",
            directory.toString(),
            "allowProduction",
            true));
    var config = AgentConfig.read(file);
    var frames = new FrameTransformer();
    var minecraft = new net.minecraft.client.Minecraft();
    var game = new GameRuntime(minecraft, config, frames, new RuntimeObserver(config, frames));
    org.lwjgl.sdl.SDLVideo.flags = 0;
    try {
      assertTrue(minecraft.getWindow().isFocused());
      assertFalse(game.focused());
      assertFalse((Boolean) game.observer.observe().get("focused"));
      assertEquals(
          "FOCUS_LOST",
          assertThrows(DriverException.class, () -> game.input.automation(() -> true)).code());
    } finally {
      org.lwjgl.sdl.SDLVideo.flags = org.lwjgl.sdl.SDLVideo.SDL_WINDOW_INPUT_FOCUS;
    }
  }

  @Test
  void missingIsolationHooksFailAndRestoreBackgroundOptions() throws Exception {
    Path file = directory.resolve("unsupported-input.json");
    Json.write(
        file,
        Map.of(
            "protocol",
            1,
            "runId",
            "unsupported-input",
            "outputDirectory",
            directory.toString(),
            "allowProduction",
            true,
            "inputIsolation",
            true,
            "backgroundExecution",
            true));
    var config = AgentConfig.read(file);
    var frames = new FrameTransformer();
    var minecraft = new net.minecraft.client.Minecraft();
    var game = new GameRuntime(minecraft, config, frames, new RuntimeObserver(config, frames));
    assertEquals(
        "INPUT_ISOLATION_UNAVAILABLE",
        assertThrows(DriverException.class, game.input::configure).code());
    assertTrue(minecraft.options.pauseOnLostFocus);
    assertFalse(io.github.billstark001.minedriver.hooks.InputGate.isolated());
  }

  @Test
  void loadingWarningsFailEvenWhenAnOverlayIsStillPresent() throws Exception {
    Path file = directory.resolve("warning.json");
    Json.write(
        file,
        Map.of(
            "protocol",
            1,
            "runId",
            "warning",
            "outputDirectory",
            directory.toString(),
            "allowProduction",
            true));
    var config = AgentConfig.read(file);
    var frames = new FrameTransformer();
    var observer = new RuntimeObserver(config, frames);
    var minecraft = new net.minecraft.client.Minecraft();
    new GameRuntime(minecraft, config, frames, observer);
    minecraft.gui.setScreen(new net.neoforged.neoforge.client.gui.LoadingErrorScreen());
    minecraft.gui.currentOverlay = new Object();
    net.neoforged.fml.ModLoader.issues =
        java.util.List.of(
            new net.neoforged.fml.ModLoader.Issue(
                "WARNING", new net.neoforged.fml.ModLoader.Mod("fixture"), "unknown.warning"));
    try {
      assertEquals(
          "STARTUP_WARNINGS",
          assertThrows(
                  DriverException.class,
                  () ->
                      observer.await("WAITING_TITLE", () -> false, Duration.ofSeconds(1), "title"))
              .code());
      assertEquals("OVERLAY", observer.status().get("blocker"));
      assertNotNull(observer.status().get("lastFailure"));
    } finally {
      net.neoforged.fml.ModLoader.issues = java.util.List.of();
    }
  }

  @Test
  void unknownScreenTimesOutWithLastScreenAndOverlay() throws Exception {
    Path path = directory.resolve("config.json");
    Json.write(
        path,
        Map.of(
            "protocol",
            1,
            "runId",
            "observer",
            "outputDirectory",
            directory.toString(),
            "allowProduction",
            true));
    var config = AgentConfig.read(path);
    var frames = new FrameTransformer();
    var observer = new RuntimeObserver(config, frames);
    var minecraft = new net.minecraft.client.Minecraft();
    var game = new GameRuntime(minecraft, config, frames, observer);
    minecraft.gui.setScreen(new net.minecraft.client.gui.screens.Screen(java.util.List.of()));
    minecraft.gui.currentOverlay = new Object();
    var failure =
        assertThrows(
            DriverException.class,
            () ->
                observer.await("WORLD_LOADING", () -> false, Duration.ofMillis(80), "world ready"));
    assertEquals("CONDITION_TIMEOUT", failure.code());
    assertTrue(failure.getMessage().contains("WORLD_LOADING"));
    assertTrue(failure.getMessage().contains("screens.Screen"));
    assertEquals("OVERLAY", observer.status().get("blocker"));
    assertFalse(observer.gameplayReady());
  }

  @Test
  void statusBypassesBusyActionsAndExpiredQueuedMutationNeverExecutes() throws Exception {
    var registry = new CommandRegistry();
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    registry.register(
        "custom.block",
        "block",
        false,
        "test",
        p -> {
          entered.countDown();
          release.await();
          return true;
        });
    registry.register("agent.status", "status", true, "read", p -> Map.of("state", "READY"));
    var mutated = new java.util.concurrent.atomic.AtomicBoolean();
    registry.register(
        "custom.mutate",
        "mutate",
        false,
        "test",
        p -> {
          mutated.set(true);
          return true;
        });
    var workers = Executors.newSingleThreadExecutor();
    try {
      var blocked = workers.submit(() -> registry.invoke("custom.block", Map.of()));
      assertTrue(entered.await(2, TimeUnit.SECONDS));
      assertEquals(Map.of("state", "READY"), registry.invoke("agent.status", Map.of()));
      assertEquals(
          "COMMAND_BUSY",
          assertThrows(
                  DriverException.class,
                  () -> registry.invoke("custom.mutate", Map.of("timeoutMillis", 30)))
              .code());
      release.countDown();
      assertEquals(true, blocked.get(2, TimeUnit.SECONDS));
      assertFalse(mutated.get());
    } finally {
      release.countDown();
      workers.shutdownNow();
    }
  }

  @Test
  void nestedStagesConsumeTheSameBudget() throws Exception {
    OperationDeadline budget = OperationDeadline.enter(Duration.ofMillis(100));
    try {
      Thread.sleep(70);
      assertTrue(OperationDeadline.remaining(Duration.ofSeconds(30)).toMillis() < 50);
      Thread.sleep(50);
      assertEquals(
          "OPERATION_TIMEOUT",
          assertThrows(
                  DriverException.class, () -> OperationDeadline.remaining(Duration.ofSeconds(30)))
              .code());
    } finally {
      budget.close();
    }
    assertTrue(OperationDeadline.remaining(Duration.ofSeconds(1)).toMillis() > 900);
  }
}
