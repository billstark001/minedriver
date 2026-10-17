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
