package io.github.billstark001.minedriver.agent;

import static org.junit.jupiter.api.Assertions.*;

import io.github.billstark001.minedriver.api.DriverException;
import io.github.billstark001.minedriver.protocol.Json;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeSafetyTest {
  @Test
  void extensionsRejectCyclesNonFiniteNumbersAndLiveObjects() {
    var cycle = new java.util.HashMap<String, Object>();
    cycle.put("self", cycle);
    for (Object invalid : java.util.List.of(cycle, Double.NaN, new Object())) {
      assertEquals(
          "INVALID_EXTENSION_RESULT",
          assertThrows(DriverException.class, () -> JsonValues.validate(invalid)).code());
    }
    assertDoesNotThrow(
        () -> JsonValues.validate(Map.of("values", java.util.List.of(true, 7, "ok"))));
  }

  private static class HiddenParent {
    public boolean isFocused() {
      return true;
    }
  }

  public static class PublicChild extends HiddenParent {}

  @Test
  void visibilityBridgeRemainsCallable() {
    assertEquals(true, Reflect.call(new PublicChild(), "isFocused"));
  }

  @TempDir Path directory;

  public static final class QueueOnly {
    Runnable queued;

    public boolean isSameThread() {
      return false;
    }

    public void execute(Runnable action) {
      queued = action;
    }
  }

  public static final class Callback {
    private int callback(int value) {
      return value + 1;
    }
  }

  @Test
  void timedOutQueuedActionNeverRunsLater() {
    var queue = new QueueOnly();
    var changed = new AtomicBoolean();
    var failure =
        assertThrows(
            DriverException.class,
            () ->
                GameRuntime.execute(
                    queue,
                    () -> {
                      changed.set(true);
                      return true;
                    },
                    Duration.ofMillis(20)));
    assertEquals("GAME_THREAD_TIMEOUT", failure.code());
    queue.queued.run();
    assertFalse(changed.get());
  }

  @Test
  void nativePrivateCallbackRequiresExactSignature() {
    assertEquals(8, Reflect.callback(new Callback(), "callback", new Class<?>[] {int.class}, 7));
    assertEquals(
        "UNSUPPORTED_API",
        assertThrows(
                DriverException.class,
                () -> Reflect.callback(new Callback(), "callback", new Class<?>[] {long.class}, 7L))
            .code());
  }

  @Test
  void configurationRejectsCoercedOrFractionalTypes() throws Exception {
    Path config = directory.resolve("config.json");
    for (Object invalid : java.util.List.of("123", 1.5, true)) {
      Json.write(
          config,
          Map.of(
              "protocol",
              1,
              "runId",
              "strict",
              "outputDirectory",
              directory.toString(),
              "timeoutMillis",
              invalid));
      assertThrows(IllegalArgumentException.class, () -> AgentConfig.read(config));
    }
    Json.write(
        config,
        Map.of(
            "protocol",
            1,
            "runId",
            "strict",
            "outputDirectory",
            directory.toString(),
            "allowProduction",
            "true"));
    assertThrows(IllegalArgumentException.class, () -> AgentConfig.read(config));
  }

  @Test
  void rpcCommandsAreWrittenToTheRunTrace() throws Exception {
    Path config = directory.resolve("config.json");
    Json.write(
        config,
        Map.of("protocol", 1, "runId", "rpc-report", "outputDirectory", directory.toString()));
    var report = new RunReport(AgentConfig.read(config));
    var registry = new CommandRegistry();
    registry.report(report);
    registry.register("custom.echo", "echo", true, "extension", p -> p);
    registry.invoke("custom.echo", Map.of("value", 7));
    report.finish(null, Map.of());
    assertEquals(
        "custom.echo",
        Json.read(directory.resolve("result.json"))
            .getAsJsonObject()
            .getAsJsonArray("steps")
            .get(0)
            .getAsJsonObject()
            .get("command")
            .getAsString());
  }
}
