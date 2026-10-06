package example;

import io.github.billstark001.minedriver.api.ProbeContext;
import io.github.billstark001.minedriver.api.Scenario;
import java.util.List;
import java.util.Map;

/** A loader-neutral assertion using authoritative server snapshots and normal client packets. */
public final class FixtureAssertions implements Scenario {
  @Override
  public void run(ProbeContext context) {
    context.call("world.create", Map.of("flat", true, "seed", "1234", "timeoutMillis", 150000));
    context.call(
        "world.fixture",
        Map.of(
            "blocks",
            List.of(Map.of("x", 1, "y", 5, "z", 0, "state", "minecraft:gold_block")),
            "inventory",
            List.of(Map.of("slot", 0, "item", "minecraft:stone", "count", 16))));
    Map<?, ?> snapshot =
        (Map<?, ?>)
            context.call(
                "world.snapshot",
                Map.of("blocks", List.of(Map.of("x", 1, "y", 5, "z", 0)), "slots", List.of(0)));
    Map<?, ?> block = (Map<?, ?>) ((List<?>) snapshot.get("blocks")).get(0);
    Map<?, ?> item = (Map<?, ?>) ((List<?>) snapshot.get("inventory")).get(0);
    context.require(
        "minecraft:gold_block".equals(block.get("id")), "Fixture block must exist on the server");
    context.require(
        "minecraft:stone".equals(item.get("id")) && ((Number) item.get("count")).intValue() == 16,
        "Fixture inventory must match");
    context.call("network.command", Map.of("command", "time set noon"));
    Map<?, ?> connection = (Map<?, ?>) context.call("network.inspect");
    context.require(
        Boolean.TRUE.equals(connection.get("connected")), "Native play connection must be active");
    context.screenshot("fixture-assertions");
    context.call("world.disconnect");
  }
}
