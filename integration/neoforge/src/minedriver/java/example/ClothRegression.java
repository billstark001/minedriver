package example;

import io.github.billstark001.minedriver.api.ProbeContext;
import io.github.billstark001.minedriver.api.Scenario;
import java.util.List;
import java.util.Map;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.gui.entries.SubCategoryListEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Real Cloth controls; no fixture replacement for its event routing or layout. */
public final class ClothRegression implements Scenario {
  @Override
  public void run(ProbeContext context) throws Exception {
    context.call("window.configure", Map.of("width", 640, "height", 480, "guiScale", 2));
    SubCategoryListEntry group =
        context.onClient(
            () -> {
              var builder =
                  ConfigBuilder.create()
                      .setParentScreen(Minecraft.getInstance().gui.screen())
                      .setTitle(Component.literal("MineDriver Cloth regression"))
                      .setShouldListSmoothScroll(false);
              var entries = builder.entryBuilder();
              var nested =
                  entries
                      .startSubCategory(
                          Component.translatable("fixture.nested"),
                          List.of(
                              entries
                                  .startBooleanToggle(
                                      Component.translatable("fixture.nested.toggle"), true)
                                  .build()))
                      .build();
              var category =
                  entries
                      .startSubCategory(
                          Component.translatable("fixture.category"),
                          List.of(
                              entries
                                  .startBooleanToggle(
                                      Component.translatable("fixture.toggle"), true)
                                  .build(),
                              nested))
                      .setExpanded(false)
                      .build();
              builder.getOrCreateCategory(Component.literal("General")).addEntry(category);
              Minecraft.getInstance().gui.setScreen(builder.build());
              return category;
            });
    context.call("render.await", Map.of("frames", 2));
    Map<String, Object> selector = Map.of("key", "fixture.category");
    context.call("input.click", Map.of("selector", selector, "part", "header"));
    context.require(context.onClient(group::isExpanded), "Native header click must expand");
    context.call("render.await", Map.of("frames", 2));
    context.call("input.click", Map.of("selector", selector));
    context.require(
        !context.onClient(group::isExpanded), "Expanded group must collapse via header");
    context.call("ui.expand", Map.of("selector", selector, "expanded", true));
    context.call("ui.expand", Map.of("selector", selector, "expanded", true));
    context.call("render.await", Map.of("frames", 2));
    context.require(context.onClient(group::isExpanded), "Semantic expansion is idempotent");
    context.call("input.click", Map.of("selector", Map.of("key", "fixture.nested")));
    Map<?, ?> snapshot = (Map<?, ?>) context.call("ui.snapshot", Map.of("name", "cloth-snapshot"));
    Map<?, ?> render = (Map<?, ?>) snapshot.get("render");
    context.require(
        render.get("failure") == null,
        "Native text geometry capture must succeed: " + render.get("failure"));
    context.require(
        !((List<?>) render.get("draws")).isEmpty(), "Cloth text must have rendered bounds");
    context.screenshot("cloth-expanded");
    context.call("ui.expand", Map.of("selector", selector, "expanded", false));
    context.call("ui.title");
  }
}
