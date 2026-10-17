package io.github.billstark001.minedriver.agent;

/**
 * Explicit geometry adapters: unknown bounds stay unknown rather than becoming a zero rectangle.
 */
final class UiGeometry {
  record Bounds(double x, double y, double width, double height) {
    Bounds intersect(Bounds other) {
      if (other == null) return this;
      double left = Math.max(x, other.x), top = Math.max(y, other.y);
      return new Bounds(
          left,
          top,
          Math.max(0, Math.min(x + width, other.x + other.width) - left),
          Math.max(0, Math.min(y + height, other.y + other.height) - top));
    }

    boolean empty() {
      return width <= 0 || height <= 0;
    }
  }

  record Geometry(Bounds layout, Bounds hit, String source) {}

  private UiGeometry() {}

  static boolean is(Object value, String name) {
    for (Class<?> type = value.getClass(); type != null; type = type.getSuperclass())
      if (type.getName().equals(name)) return true;
    return false;
  }

  static boolean cloth(Object value) {
    return is(value, "me.shedaniel.clothconfig2.gui.widget.DynamicEntryListWidget$Entry");
  }

  static boolean group(Object value) {
    return is(value, "me.shedaniel.clothconfig2.gui.entries.SubCategoryListEntry");
  }

  static boolean header(Object value) {
    return is(
        value, "me.shedaniel.clothconfig2.gui.entries.SubCategoryListEntry$CategoryLabelWidget");
  }

  static Bounds rectangle(Object value) {
    if (value == null) return null;
    return new Bounds(
        field(value, "x"), field(value, "y"), field(value, "width"), field(value, "height"));
  }

  static Geometry geometry(Object widget) {
    if (group(widget)) {
      Bounds layout = rectangle(Reflect.field(widget, "bounds"));
      // Cloth lazily initializes the header rectangle in its native region calculation.
      // Use the last rendered entry bounds and that calculation, never guess a header offset.
      if (layout != null && !layout.empty())
        Reflect.call(
            widget,
            "getEntryArea",
            (int) layout.x(),
            (int) layout.y(),
            (int) layout.width(),
            (int) layout.height());
      Bounds hit = rectangle(Reflect.field(Reflect.field(widget, "widget"), "rectangle"));
      return new Geometry(layout, hit, "cloth-category");
    }
    if (header(widget)) {
      Bounds hit = rectangle(Reflect.field(widget, "rectangle"));
      return new Geometry(hit, hit, "cloth-category-header");
    }
    if (cloth(widget)) {
      Bounds layout = rectangle(Reflect.field(widget, "bounds"));
      return new Geometry(layout, layout, "cloth-entry");
    }
    if (Reflect.hasMethod(widget, "getX", 0)
        && Reflect.hasMethod(widget, "getY", 0)
        && Reflect.hasMethod(widget, "getWidth", 0)
        && Reflect.hasMethod(widget, "getHeight", 0)) {
      Bounds bounds =
          new Bounds(
              number(widget, "getX"),
              number(widget, "getY"),
              number(widget, "getWidth"),
              number(widget, "getHeight"));
      return new Geometry(bounds, bounds, "native-widget");
    }
    return new Geometry(null, null, "unknown");
  }

  static Bounds childClip(Object widget, Bounds inherited) {
    Bounds viewport = null;
    if (is(widget, "me.shedaniel.clothconfig2.gui.widget.DynamicEntryListWidget"))
      viewport =
          new Bounds(
              field(widget, "left"),
              field(widget, "top"),
              field(widget, "right") - field(widget, "left"),
              field(widget, "bottom") - field(widget, "top"));
    else if (is(widget, "net.minecraft.client.gui.components.AbstractSelectionList"))
      viewport = geometry(widget).layout();
    return viewport == null ? inherited : viewport.intersect(inherited);
  }

  static double field(Object value, String name) {
    return ((Number) Reflect.field(value, name)).doubleValue();
  }

  private static double number(Object value, String name) {
    return ((Number) Reflect.call(value, name)).doubleValue();
  }
}
