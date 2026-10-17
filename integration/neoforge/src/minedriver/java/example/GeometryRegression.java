package example;

import io.github.billstark001.minedriver.api.ProbeContext;
import io.github.billstark001.minedriver.api.Scenario;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

public final class GeometryRegression implements Scenario {
  @Override
  public void run(ProbeContext context) throws Exception {
    context.onClient(
        () -> {
          InputRegression.visibility(false);
          return null;
        });
    try {
      context.call("wait.state", Map.of("path", "focused", "equals", false));
      context.call("world.create", Map.of("mode", "SURVIVAL", "timeoutMillis", 180_000));
      context.call("assert.state", Map.of("path", "focused", "equals", false));
      context.call("assert.state", Map.of("path", "paused", "equals", false));
      java.util.UUID movingPlayer =
          context.onClient(() -> Minecraft.getInstance().player.getUUID());
      double[] start =
          context.onServer(
              () -> {
                var serverPlayer =
                    Minecraft.getInstance()
                        .getSingleplayerServer()
                        .getPlayerList()
                        .getPlayer(movingPlayer);
                return new double[] {serverPlayer.getX(), serverPlayer.getZ()};
              });
      context.call("input.key", Map.of("key", "W", "action", "PRESS"));
      try {
        context.await(
            "unfocused movement reaches integrated server",
            Duration.ofSeconds(10),
            () ->
                context.onServer(
                    () -> {
                      var serverPlayer =
                          Minecraft.getInstance()
                              .getSingleplayerServer()
                              .getPlayerList()
                              .getPlayer(movingPlayer);
                      return Math.abs(serverPlayer.getX() - start[0])
                              + Math.abs(serverPlayer.getZ() - start[1])
                          > 0.1;
                    }));
      } finally {
        context.call("input.key", Map.of("key", "W", "action", "RELEASE"));
      }
    } finally {
      context.onClient(
          () -> {
            InputRegression.visibility(true);
            return null;
          });
    }
    context.call("assert.state", Map.of("path", "gameplayReady", "equals", true));
    context.call(
        "world.fixture",
        Map.of("inventory", List.of(Map.of("slot", 0, "item", "minecraft:stone", "count", 32))));
    java.util.UUID player = context.onClient(() -> Minecraft.getInstance().player.getUUID());
    context.onClient(
        () -> {
          Minecraft.getInstance()
              .gui
              .setScreen(new InventoryScreen(Minecraft.getInstance().player));
          return null;
        });
    Map<?, ?> snapshot =
        (Map<?, ?>) context.call("ui.snapshot", Map.of("name", "inventory-snapshot"));
    List<?> slots = (List<?>) snapshot.get("slots");
    context.require(slots.size() >= 36, "Container snapshot includes empty inventory slots");
    Map<?, ?> stone =
        slots.stream()
            .map(value -> (Map<?, ?>) value)
            .filter(value -> value.get("item").equals("minecraft:stone"))
            .findFirst()
            .orElseThrow();
    context.require(
        ((Number) stone.get("count")).intValue() == 32, "Slot reports the native stack count");
    context.require(
        ((Number) stone.get("containerSlot")).intValue() == 0,
        "Container and menu slot numbering are distinct");
    int menuSlot = ((Number) stone.get("menuSlot")).intValue();
    context.call("input.click", Map.of("slot", menuSlot, "screenId", snapshot.get("screenId")));
    context.await(
        "server received inventory pickup",
        Duration.ofSeconds(10),
        () ->
            context.onServer(
                () ->
                    Minecraft.getInstance()
                        .getSingleplayerServer()
                        .getPlayerList()
                        .getPlayer(player)
                        .getInventory()
                        .getItem(0)
                        .isEmpty()));
    context.call("input.click", Map.of("slot", menuSlot));
    context.await(
        "server received inventory replacement",
        Duration.ofSeconds(10),
        () ->
            context.onServer(
                () ->
                    Minecraft.getInstance()
                            .getSingleplayerServer()
                            .getPlayerList()
                            .getPlayer(player)
                            .getInventory()
                            .getItem(0)
                            .getCount()
                        == 32));
    Map<?, ?> render = (Map<?, ?>) snapshot.get("render");
    context.require(
        render.get("failure") == null, "Text/item capture succeeded: " + render.get("failure"));
    context.require(
        ((List<?>) render.get("draws"))
            .stream()
                .map(value -> (Map<?, ?>) value)
                .anyMatch(value -> value.get("type").equals("item")),
        "Rendered items have bounds");
    context.screenshot("inventory-geometry");
    context.call("input.key", Map.of("key", "ESCAPE"));
    context.call("world.disconnect");
  }
}
