package io.github.billstark001.minedriver.agent;

import static org.junit.jupiter.api.Assertions.*;

import io.github.billstark001.minedriver.api.DriverException;
import io.github.billstark001.minedriver.protocol.Json;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UiGeometryTest {
  @TempDir Path directory;

  @Test
  void categoryClicksUseHeaderRatherThanWholeExpandedGroupCenter() throws Exception {
    Path file = directory.resolve("config.json");
    Json.write(
        file,
        Map.of(
            "protocol",
            1,
            "runId",
            "geometry",
            "outputDirectory",
            directory.toString(),
            "allowProduction",
            true));
    var config = AgentConfig.read(file);
    var frames = new FrameTransformer();
    var minecraft = new net.minecraft.client.Minecraft();
    var game = new GameRuntime(minecraft, config, frames, new RuntimeObserver(config, frames));
    var category = new me.shedaniel.clothconfig2.gui.entries.SubCategoryListEntry();
    minecraft.gui.setScreen(new net.minecraft.client.gui.screens.Screen(List.of(category)));
    var selector = Map.<String, Object>of("key", "fixture.category");
    var node = game.ui.select(selector).node();
    assertEquals(100, node.layoutBounds().height());
    assertEquals(24, node.hitBounds().height());
    assertEquals("cloth-category", node.boundsSource());
    assertNull(game.ui.live(10).get(0).node().layoutBounds());
    game.ui.click(Map.of("selector", selector, "part", "header"));
    assertTrue(category.isExpanded());
    game.ui.click(Map.of("selector", selector));
    assertFalse(category.isExpanded());
    game.ui.expand(Map.of("selector", selector, "expanded", true));
    game.ui.expand(Map.of("selector", selector, "expanded", true));
    assertTrue(category.isExpanded());
    assertEquals("gui", game.ui.snapshot(10).get("coordinateSpace"));
    assertEquals(
        "UI_CHANGED",
        assertThrows(
                DriverException.class,
                () -> game.ui.click(Map.of("selector", selector, "screenId", "old")))
            .code());
  }

  @Test
  void clippingPreservesOriginalLayoutAndRejectsEmptyHitRegion() {
    var layout = new UiGeometry.Bounds(20, -10, 180, 24);
    var clipped = layout.intersect(new UiGeometry.Bounds(0, 0, 320, 240));
    assertEquals(0, clipped.y());
    assertEquals(14, clipped.height());
    assertEquals(-10, layout.y());
    assertTrue(layout.intersect(new UiGeometry.Bounds(0, 50, 320, 240)).empty());
    assertNull(UiGeometry.geometry(new Object()).layout());
  }
}
