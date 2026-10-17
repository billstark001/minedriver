package io.github.billstark001.minedriver.agent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class UiSlots {
  private UiSlots() {}

  static List<Map<String, Object>> snapshot(GameRuntime game) {
    Object screen = game.screen();
    if (screen == null
        || !UiGeometry.is(
            screen, "net.minecraft.client.gui.screens.inventory.AbstractContainerScreen"))
      return List.of();
    Object menu = Reflect.call(screen, "getMenu");
    double left = UiGeometry.field(screen, "leftPos"), top = UiGeometry.field(screen, "topPos");
    var result = new ArrayList<Map<String, Object>>();
    for (Object slot : (Iterable<?>) Reflect.field(menu, "slots")) {
      double x = left + UiGeometry.field(slot, "x"), y = top + UiGeometry.field(slot, "y");
      Object stack = Reflect.call(slot, "getItem");
      Object item =
          Reflect.call(
              Reflect.field(game.type("net.minecraft.core.registries.BuiltInRegistries"), "ITEM"),
              "getKey",
              Reflect.call(stack, "getItem"));
      var value = new LinkedHashMap<String, Object>();
      value.put("menuSlot", Reflect.field(slot, "index"));
      value.put("containerSlot", Reflect.call(slot, "getContainerSlot"));
      value.put("item", item.toString());
      value.put("count", Reflect.call(stack, "getCount"));
      value.put("active", Reflect.call(slot, "isActive"));
      value.put("contentBounds", new UiGeometry.Bounds(x, y, 16, 16));
      var hit = new UiGeometry.Bounds(x - 1, y - 1, 18, 18);
      value.put("hitBounds", hit);
      value.put("clippedBounds", hit.intersect(game.ui.viewport()));
      value.put("boundsSource", "native-container-slot");
      result.add(value);
    }
    return List.copyOf(result);
  }
}
