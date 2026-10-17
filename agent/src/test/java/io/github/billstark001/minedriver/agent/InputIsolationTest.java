package io.github.billstark001.minedriver.agent;

import static org.junit.jupiter.api.Assertions.*;

import io.github.billstark001.minedriver.api.DriverException;
import io.github.billstark001.minedriver.hooks.InputGate;
import io.github.billstark001.minedriver.protocol.Json;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InputIsolationTest {
  @TempDir Path directory;
  private GameRuntime game;
  private net.minecraft.client.Minecraft minecraft;
  private Set<String> hooks;
  private Set<String> previousHooks;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setup() throws Exception {
    Path file = directory.resolve("input.json");
    Json.write(
        file,
        Map.of(
            "protocol",
            1,
            "runId",
            "input",
            "outputDirectory",
            directory.toString(),
            "allowProduction",
            true,
            "backgroundExecution",
            true));
    var config = AgentConfig.read(file);
    var frames = new FrameTransformer();
    minecraft = new net.minecraft.client.Minecraft();
    game = new GameRuntime(minecraft, config, frames, new RuntimeObserver(config, frames));
    // Restore capability state after each test, so unsupported-hook tests remain independent.
    hooks = (Set<String>) Reflect.field(InputGate.class, "INSTALLED");
    previousHooks = new HashSet<>(hooks);
    game.input.missingHooks().forEach(InputGate::installed);
    game.input.configure();
  }

  @AfterEach
  void cleanup() {
    try {
      game.input.close();
    } finally {
      InputGate.isolation(false);
      hooks.clear();
      hooks.addAll(previousHooks);
      game.visuals.close();
      org.lwjgl.sdl.SDLMouse.buttons = 0;
      InputGate.physicalMouse(1, false);
    }
  }

  @Test
  void restoringSamplesMouseStateWhenTheReleaseCallbackWasDiscarded() {
    String lease = (String) ((Map<?, ?>) game.input.acquire(Map.of())).get("leaseId");
    InputGate.blockMouse(1, 1);
    org.lwjgl.sdl.SDLMouse.buttons = 0;
    game.input.release(Map.of("leaseId", lease));
    assertFalse(InputGate.blockMouse(1, 1));
    InputGate.blockMouse(1, 0);
    lease = (String) ((Map<?, ?>) game.input.acquire(Map.of())).get("leaseId");
    org.lwjgl.sdl.SDLMouse.buttons = 1;
    game.input.release(Map.of("leaseId", lease));
    assertTrue(InputGate.blockMouse(1, 1));
    assertTrue(InputGate.blockMouse(1, 0));
    assertFalse(InputGate.blockMouse(1, 1));
    InputGate.blockMouse(1, 0);
  }

  @SuppressWarnings("unchecked")
  private void expireLeases() {
    ((Map<String, Long>) Reflect.field(game.input, "leases"))
        .replaceAll((key, deadline) -> System.nanoTime() - 1);
  }

  @Test
  void inputChecksExpiryBeforeTheObserverCanRun() {
    game.input.acquire(Map.of());
    InputGate.key(26, true);
    expireLeases();
    assertEquals(
        "INPUT_LEASE_EXPIRED",
        assertThrows(
                DriverException.class, () -> game.input.automation(() -> fail("Expired input ran")))
            .code());
    assertFalse(InputGate.isolated());
    assertFalse(InputGate.keyDown(26));
    assertEquals("INPUT_LEASE_EXPIRED", game.input.status().get("expiredLease"));
  }

  @Test
  void acquiringAfterExpiryClearsThePreviousVirtualKeys() {
    game.input.acquire(Map.of());
    InputGate.key(26, true);
    expireLeases();
    game.input.acquire(Map.of());
    assertTrue(InputGate.isolated());
    assertFalse(InputGate.keyDown(26));
    assertEquals(1, ((java.util.List<?>) game.input.status().get("leases")).size());
  }

  @Test
  void oneExpiredLeaseCannotReleaseAnotherOwnersLease() {
    String first = (String) ((Map<?, ?>) game.input.acquire(Map.of())).get("leaseId");
    game.input.acquire(Map.of());
    @SuppressWarnings("unchecked")
    var leases = (Map<String, Long>) Reflect.field(game.input, "leases");
    leases.put(first, System.nanoTime() - 1);
    InputGate.key(26, true);
    assertTrue(game.input.automation(() -> true));
    assertTrue(InputGate.isolated());
    assertTrue(InputGate.keyDown(26));
    assertEquals(
        "INPUT_LEASE_NOT_FOUND",
        assertThrows(DriverException.class, () -> game.input.renew(Map.of("leaseId", first)))
            .code());
  }

  @Test
  void failedPressAlwaysReleasesAndPreservesThePrimaryFailure() {
    for (String action : java.util.List.of("PRESS", "TAP")) {
      minecraft.keyboardHandler.failPress = true;
      minecraft.keyboardHandler.failRelease = true;
      var failure =
          assertThrows(
              IllegalStateException.class,
              () -> game.media.key(Map.of("key", "W", "action", action), Duration.ofSeconds(1)));
      assertEquals("press failed", failure.getMessage());
      assertEquals("release failed", failure.getSuppressed()[0].getMessage());
      assertFalse(InputGate.keyDown(26));
      assertFalse(minecraft.keyboardHandler.held);
    }
  }

  @Test
  void closingWithoutIsolationStillReleasesInjectedKeysAndRestoresOptions() {
    int before = net.minecraft.client.KeyMapping.releases;
    game.media.key(Map.of("key", "W", "action", "PRESS"), Duration.ofSeconds(1));
    assertTrue(InputGate.keyDown(26));
    game.input.close();
    assertFalse(InputGate.keyDown(26));
    assertFalse(InputGate.isolated());
    assertTrue(net.minecraft.client.KeyMapping.releases > before);
    assertTrue(minecraft.options.pauseOnLostFocus);
    assertEquals(
        "SESSION_CLOSED",
        assertThrows(DriverException.class, () -> game.input.acquire(Map.of())).code());
    assertEquals(
        "SESSION_CLOSED",
        assertThrows(DriverException.class, () -> game.input.automation(() -> true)).code());
  }
}
