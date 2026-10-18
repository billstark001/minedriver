package example;

import io.github.billstark001.minedriver.api.ProbeContext;
import io.github.billstark001.minedriver.api.Scenario;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import net.minecraft.client.MinecraftClient;

/** Compiles unchanged against each pinned Yarn-named runtime; assertions use native state. */
public final class CompatibilityScenario implements Scenario {
  @Override
  public void run(ProbeContext context) throws Exception {
    Map<?, ?> environment = (Map<?, ?>) context.call("session.inspect");
    String version = environment.get("minecraft").toString();
    boolean legacy = version.equals("1.21.1");
    context.require(
        "named".equals(environment.get("mappingNamespace")), "Loom named mapping bridge is active");
    context.require(
        "net.minecraft.client.MinecraftClient".equals(MinecraftClient.class.getName()),
        "Scenario uses native Yarn classes");
    context.require(
        Boolean.TRUE.equals(environment.get("development")), "Development guard stays enabled");
    Map<?, ?> isolation = (Map<?, ?>) context.call("input.isolation.status");
    context.require(
        Boolean.TRUE.equals(isolation.get("isolated")), "Session isolates native input");
    context.require(
        ((List<?>) isolation.get("missingHooks")).isEmpty(),
        "All version-specific hooks installed");
    context.call("window.configure", Map.of("width", 640, "height", 480, "guiScale", 2));
    context.click(io.github.billstark001.minedriver.api.Selector.key("menu.options"));
    context.call("render.await", Map.of("frames", 3));
    context.onClient(
        () -> {
          Object keyboard = MinecraftClient.getInstance().keyboard;
          Class<?> constants = Class.forName("net.minecraft.client.util.InputUtil");
          int escape = constants.getField("GLFW_KEY_ESCAPE").getInt(null);
          long handle = handle();
          if (legacy) {
            var callback =
                keyboard
                    .getClass()
                    .getDeclaredMethod(
                        "onKey", long.class, int.class, int.class, int.class, int.class);
            callback.setAccessible(true);
            callback.invoke(keyboard, handle, escape, 0, 1, 0);
            callback.invoke(keyboard, handle, escape, 0, 0, 0);
          } else {
            Class<?> event = Class.forName("net.minecraft.client.input.KeyInput");
            var callback =
                keyboard.getClass().getDeclaredMethod("onKey", long.class, int.class, event);
            callback.setAccessible(true);
            Object key =
                event.getConstructor(int.class, int.class, int.class).newInstance(escape, 0, 0);
            callback.invoke(keyboard, handle, 1, key);
            callback.invoke(keyboard, handle, 0, key);
          }
          return null;
        });
    context.require(
        !inspect(context).get("screen").toString().endsWith(".TitleScreen"),
        "External ESC is blocked");
    context.call("input.key", Map.of("key", "W", "action", "PRESS"));
    context.require(context.onClient(() -> polled(legacy)), "Virtual key reaches native polling");
    context.call("input.key", Map.of("key", "W", "action", "RELEASE"));
    context.require(!context.onClient(() -> polled(legacy)), "Native polling releases virtual key");
    context.call("render.await", Map.of("frames", 3));
    Map<?, ?> ui = (Map<?, ?>) context.call("ui.snapshot");
    Map<?, ?> render = (Map<?, ?>) ui.get("render");
    context.require(
        render.get("failure") == null, "Render adapter has no capture errors: " + render);
    context.require(
        !((List<?>) render.get("draws")).isEmpty(), "Rendered text geometry is captured");
    context.screenshot("compatibility-options");
    context.call("input.key", Map.of("key", "ESCAPE"));
    context.require(
        inspect(context).get("screen").toString().endsWith(".TitleScreen"),
        "Native ESC returns to title");
    context.activate(io.github.billstark001.minedriver.api.Selector.key("menu.options"));
    context.call("input.key", Map.of("key", "ESCAPE"));
    var textScreen =
        context.onClient(
            () -> {
              var screen = new CompatibilityInputScreen();
              CompatibilityClothScenario.show(screen);
              return screen;
            });
    context.call("input.text", Map.of("value", "MineDriver文字"));
    context.require(
        "MineDriver文字".equals(context.onClient(textScreen::value)),
        "Native character callbacks reach the focused TextFieldWidget");
    context.call(
        "assert.widget",
        Map.of("selector", Map.of("type", "net.minecraft.client.gui.components.EditBox")));
    context.call("ui.title");
    context.call("resources.language", Map.of("code", "zh_cn"));
    context.call("assert.widget", Map.of("selector", Map.of("key", "menu.options")));
    context.call("resources.language", Map.of("code", "en_us"));
    context.onClient(
        () -> {
          visibility(false);
          return null;
        });
    try {
      context.call("wait.state", Map.of("path", "focused", "equals", false));
      context.call(
          "world.create", Map.of("mode", "SURVIVAL", "flat", true, "timeoutMillis", 180000));
      context.call("assert.state", Map.of("path", "paused", "equals", false));
      var playerId = context.onClient(() -> MinecraftClient.getInstance().player.getUuid());
      double start =
          context.onServer(
              () ->
                  MinecraftClient.getInstance()
                      .getServer()
                      .getPlayerManager()
                      .getPlayer(playerId)
                      .getZ());
      context.call("input.key", Map.of("key", "W", "action", "PRESS"));
      try {
        context.await(
            "background movement reaches server",
            Duration.ofSeconds(15),
            () ->
                context.onServer(
                    () ->
                        Math.abs(
                                MinecraftClient.getInstance()
                                        .getServer()
                                        .getPlayerManager()
                                        .getPlayer(playerId)
                                        .getZ()
                                    - start)
                            > 0.1));
      } finally {
        context.call("input.key", Map.of("key", "W", "action", "RELEASE"));
      }
    } finally {
      context.onClient(
          () -> {
            visibility(true);
            return null;
          });
    }
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
    context.require(
        "minecraft:gold_block"
            .equals(((Map<?, ?>) ((List<?>) snapshot.get("blocks")).get(0)).get("id")),
        "Server block assertion");
    context.require(
        ((Number) ((Map<?, ?>) ((List<?>) snapshot.get("inventory")).get(0)).get("count"))
                .intValue()
            == 16,
        "Server inventory assertion");
    context.call("input.key", Map.of("key", "E"));
    context.call("render.await", Map.of("frames", 5));
    Map<?, ?> inventory = (Map<?, ?>) context.call("ui.snapshot");
    List<?> slots = (List<?>) inventory.get("slots");
    context.require(slots.size() >= 36, "Native inventory slots include empty stacks");
    Map<?, ?> inventoryRender = (Map<?, ?>) inventory.get("render");
    context.require(inventoryRender.get("failure") == null, "Inventory render capture succeeds");
    context.require(
        ((List<?>) inventoryRender.get("draws"))
            .stream()
                .map(value -> (Map<?, ?>) value)
                .anyMatch(value -> "item".equals(value.get("type"))),
        "Native item draws have geometry");
    int menuSlot =
        ((Number)
                slots.stream()
                    .map(value -> (Map<?, ?>) value)
                    .filter(value -> "minecraft:stone".equals(value.get("item")))
                    .findFirst()
                    .orElseThrow()
                    .get("menuSlot"))
            .intValue();
    context.call("input.click", Map.of("slot", menuSlot));
    context.await(
        "server confirms inventory pickup", Duration.ofSeconds(10), () -> count(context) == 0);
    context.call("input.click", Map.of("slot", menuSlot));
    context.await(
        "server confirms inventory replacement",
        Duration.ofSeconds(10),
        () -> count(context) == 16);
    context.screenshot("compatibility-inventory");
    context.call("input.key", Map.of("key", "ESCAPE"));
    context.call("network.command", Map.of("command", "time set noon"));
    context.require(
        Boolean.TRUE.equals(((Map<?, ?>) context.call("network.inspect")).get("connected")),
        "Native play connection");
    context.call("world.disconnect");
  }

  private static Map<?, ?> inspect(ProbeContext context) {
    return (Map<?, ?>) context.call("session.inspect");
  }

  private static int count(ProbeContext context) {
    Map<?, ?> snapshot = (Map<?, ?>) context.call("world.snapshot", Map.of("slots", List.of(0)));
    return ((Number) ((Map<?, ?>) ((List<?>) snapshot.get("inventory")).get(0)).get("count"))
        .intValue();
  }

  private static long handle() throws Exception {
    Object window = MinecraftClient.getInstance().getWindow();
    return (Long) window.getClass().getMethod("getHandle").invoke(window);
  }

  private static boolean polled(boolean legacy) throws Exception {
    Class<?> constants = Class.forName("net.minecraft.client.util.InputUtil");
    int key = constants.getField("GLFW_KEY_W").getInt(null);
    if (legacy)
      return (Boolean)
          constants.getMethod("isKeyPressed", long.class, int.class).invoke(null, handle(), key);
    Object window = MinecraftClient.getInstance().getWindow();
    return (Boolean)
        constants.getMethod("isKeyPressed", window.getClass(), int.class).invoke(null, window, key);
  }

  private static void visibility(boolean visible) throws Exception {
    String method = visible ? "glfwShowWindow" : "glfwHideWindow";
    Class.forName("org.lwjgl.glfw.GLFW").getMethod(method, long.class).invoke(null, handle());
  }
}
