package io.github.billstark001.minedriver.agent;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import io.github.billstark001.minedriver.api.DriverException;
import io.github.billstark001.minedriver.api.ProbeContext;
import io.github.billstark001.minedriver.api.Scenario;
import io.github.billstark001.minedriver.protocol.Json;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionLifecycleTest {
  @TempDir Path directory;

  public static final class BlockingScenario implements Scenario {
    static CountDownLatch entered, release;

    @Override
    public void run(ProbeContext context) throws Exception {
      entered.countDown();
      if (!release.await(3, TimeUnit.SECONDS)) throw new IllegalStateException("Fixture timed out");
    }
  }

  @Test
  void finiteScenarioOwnsTheSameBusyGuardAsInteractiveScenarios() throws Exception {
    Path file = directory.resolve("session.json");
    Json.write(
        file,
        Map.of(
            "protocol",
            1,
            "runId",
            "lifecycle",
            "outputDirectory",
            directory.toString(),
            "allowProduction",
            true));
    var config = AgentConfig.read(file);
    var frames = new FrameTransformer();
    var game =
        new GameRuntime(
            new net.minecraft.client.Minecraft(),
            config,
            frames,
            new RuntimeObserver(config, frames));
    var registry = new CommandRegistry();
    var session = new DriverSession(config, game, registry, new RunReport(config));
    var worker = Executors.newSingleThreadExecutor();
    BlockingScenario.entered = new CountDownLatch(1);
    BlockingScenario.release = new CountDownLatch(1);
    try {
      var running =
          worker.submit(
              () -> {
                session.run(new JsonObject(), List.of(BlockingScenario.class.getName()));
                return true;
              });
      assertTrue(BlockingScenario.entered.await(2, TimeUnit.SECONDS));
      assertTrue(
          (Boolean) ((Map<?, ?>) registry.invoke("scenario.status", Map.of())).get("running"));
      for (String command : List.of("scenario.run", "session.close"))
        assertEquals(
            "SCENARIO_BUSY",
            assertThrows(DriverException.class, () -> registry.invoke(command, Map.of())).code());
      BlockingScenario.release.countDown();
      assertTrue(running.get(2, TimeUnit.SECONDS));
      registry.invoke("session.close", Map.of());
      assertEquals(
          "SESSION_CLOSED",
          assertThrows(DriverException.class, () -> registry.invoke("scenario.run", Map.of()))
              .code());
      assertEquals(
          "SESSION_CLOSED",
          assertThrows(DriverException.class, () -> registry.invoke("ui.title", Map.of())).code());
      assertDoesNotThrow(() -> registry.invoke("input.isolation.status", Map.of()));
    } finally {
      BlockingScenario.release.countDown();
      worker.shutdownNow();
      session.close();
    }
  }
}
