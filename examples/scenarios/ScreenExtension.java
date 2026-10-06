package example;

import io.github.billstark001.minedriver.api.DriverExtension;
import io.github.billstark001.minedriver.api.ExtensionRegistry;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Compile this in the consuming mod's minedriver source set; no production entrypoint is needed.
 */
public final class ScreenExtension implements DriverExtension {
  @Override
  public void register(ExtensionRegistry registry) {
    var count = new java.util.concurrent.atomic.AtomicInteger();
    registry.register(
        "custom.openScreen",
        "Open the extension's test screen",
        false,
        (context, parameters) ->
            context.onClient(
                () -> {
                  Minecraft minecraft = Minecraft.getInstance();
                  count.set(0);
                  minecraft.gui.setScreen(
                      new Screen(Component.literal("MineDriver extension")) {
                        @Override
                        protected void init() {
                          var button =
                              addRenderableWidget(
                                  Button.builder(
                                          Component.literal("Increment"),
                                          clicked -> count.incrementAndGet())
                                      .bounds(20, 40, 200, 20)
                                      .build());
                          registry.identify(button, "example.increment");
                        }
                      });
                  return Map.of("opened", true);
                }));
    registry.register(
        "custom.assertNoWorld",
        "Assert that no client world is loaded",
        true,
        (context, parameters) -> {
          context.require(
              context.onClient(() -> Minecraft.getInstance().level == null),
              "A world must not be loaded");
          return true;
        });
    registry.register(
        "custom.assertCount",
        "Assert test screen action count",
        true,
        (context, parameters) -> {
          context.require(
              count.get() == ((Number) parameters.getOrDefault("count", 0)).intValue(),
              "Screen counter mismatch");
          return Map.of("count", count.get());
        });
  }
}
