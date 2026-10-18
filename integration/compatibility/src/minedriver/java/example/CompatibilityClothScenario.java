package example;

import io.github.billstark001.minedriver.api.ProbeContext;
import io.github.billstark001.minedriver.api.Scenario;
import java.util.List;
import java.util.Map;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.gui.entries.SubCategoryListEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Real version-matched Cloth controls, including the legacy layout/event implementation. */
public final class CompatibilityClothScenario implements Scenario {
  @Override
  public void run(ProbeContext context) throws Exception {
    SubCategoryListEntry group =
        context.onClient(
            () -> {
              var builder =
                  ConfigBuilder.create()
                      .setTitle(Component.literal("MineDriver compatibility"))
                      .setShouldListSmoothScroll(false);
              var entries = builder.entryBuilder();
              var category =
                  entries
                      .startSubCategory(
                          Component.translatable("fixture.category"),
                          List.of(
                              entries
                                  .startBooleanToggle(
                                      Component.translatable("fixture.toggle"), true)
                                  .build()))
                      .setExpanded(false)
                      .build();
              builder.getOrCreateCategory(Component.literal("General")).addEntry(category);
              show(builder.build());
              return category;
            });
    context.call("render.await", Map.of("frames", 3));
    var selector = Map.of("key", "fixture.category");
    context.call("input.click", Map.of("selector", selector, "part", "header"));
    context.require(context.onClient(group::isExpanded), "Native Cloth header expands");
    context.call("render.await", Map.of("frames", 3));
    context.call("input.click", Map.of("selector", selector));
    context.require(!context.onClient(group::isExpanded), "Native Cloth header collapses");
    context.call("ui.expand", Map.of("selector", selector, "expanded", true));
    context.call("render.await", Map.of("frames", 3));
    Map<?, ?> ui = (Map<?, ?>) context.call("ui.snapshot");
    Map<?, ?> render = (Map<?, ?>) ui.get("render");
    context.require(render.get("failure") == null, "Cloth render capture succeeds: " + render);
    context.require(!((List<?>) render.get("draws")).isEmpty(), "Cloth rendered text has geometry");
    context.screenshot("compatibility-cloth");
    context.call("ui.title");
  }

  static void show(Screen screen) throws Exception {
    var client = Minecraft.getInstance();
    try {
      Minecraft.class.getMethod("setScreen", Screen.class).invoke(client, screen);
    } catch (NoSuchMethodException moved) {
      Object gui = Minecraft.class.getField("gui").get(client);
      gui.getClass().getMethod("setScreen", Screen.class).invoke(gui, screen);
    }
  }
}
