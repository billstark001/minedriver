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
