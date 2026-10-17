package io.github.billstark001.minedriver.agent;

import io.github.billstark001.minedriver.api.DriverException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

final class MinecraftUi {
  record Node(
      String path,
      String stableId,
      String type,
      String label,
      String key,
      boolean active,
      boolean visible,
      boolean focused,
      String role,
      UiGeometry.Bounds layoutBounds,
      UiGeometry.Bounds hitBounds,
      UiGeometry.Bounds clippedBounds,
      String boundsSource,
      Boolean expanded,
      Object value) {}

  record Live(Node node, Object widget) {}

  private final GameRuntime game;
  private final Map<Object, String> identities = new WeakHashMap<>();

  MinecraftUi(GameRuntime game) {
    this.game = game;
  }

  synchronized void identify(Object widget, String id) {
    if (id == null || id.isBlank() || id.length() > 200)
      throw Parameters.invalid("Invalid stable widget ID");
    if (identities.size() >= 8192)
      throw Parameters.invalid("Too many registered widget identities");
    identities.put(widget, id);
  }

  private synchronized String identity(Object widget) {
    return identities.get(widget);
  }

  List<Live> live(int limit) {
    Object screen = game.screen();
    if (screen == null) return List.of();
    var result = new ArrayList<Live>();
    visit(screen, "0", true, true, viewport(), new IdentityHashMap<>(), result, limit, 0);
    return result;
  }

  private void visit(
      Object widget,
      String path,
      boolean parentActive,
      boolean parentVisible,
      UiGeometry.Bounds clip,
      IdentityHashMap<Object, Boolean> visited,
      List<Live> output,
      int limit,
      int depth) {
    if (depth > 32 || visited.put(widget, true) != null) return;
    if (output.size() >= limit)
      throw new DriverException("UI_TREE_LIMIT", "Widget tree exceeds " + limit + " nodes");
    Object component =
        UiGeometry.cloth(widget) && Reflect.hasMethod(widget, "getFieldName", 0)
            ? Reflect.call(widget, "getFieldName")
            : Reflect.hasMethod(widget, "getMessage", 0)
                ? Reflect.call(widget, "getMessage")
                : null;
    boolean active =
        parentActive
            && (UiGeometry.cloth(widget)
                ? Boolean.TRUE.equals(Reflect.call(widget, "isEnabled"))
                : flag(widget, "active", "isActive", true));
    boolean visible =
        parentVisible
            && (UiGeometry.cloth(widget)
                ? Boolean.TRUE.equals(Reflect.call(widget, "isDisplayed"))
                : flag(widget, "visible", null, true));
    UiGeometry.Geometry geometry = UiGeometry.geometry(widget);
    UiGeometry.Bounds clipped = geometry.hit() == null ? null : geometry.hit().intersect(clip);
    Object value =
        Reflect.hasMethod(widget, "getValue", 0) ? Reflect.call(widget, "getValue") : null;
    if (value != null
        && !(value instanceof String || value instanceof Number || value instanceof Boolean))
      value = null;
    var node =
        new Node(
            path,
            identity(widget),
            widget.getClass().getName(),
            text(component),
            key(component),
            active,
            visible,
            flag(widget, null, "isFocused", false),
            UiGeometry.group(widget)
                ? "config-group"
                : UiGeometry.header(widget)
                    ? "group-header"
                    : UiGeometry.cloth(widget)
                        ? "config-entry"
                        : path.equals("0") ? "screen" : "widget",
            geometry.layout(),
            geometry.hit(),
            clipped,
            geometry.source(),
            UiGeometry.group(widget) ? (Boolean) Reflect.call(widget, "isExpanded") : null,
            value);
    output.add(new Live(node, widget));
    if (Reflect.hasMethod(widget, "children", 0)) {
      Object children = Reflect.call(widget, "children");
      if (children instanceof Iterable<?> iterable) {
        int index = 0;
        for (Object child : iterable) {
          if (child != null)
            visit(
                child,
                path + "/" + index,
                active,
                visible,
                UiGeometry.childClip(widget, clip),
                visited,
                output,
                limit,
                depth + 1);
          index++;
        }
      }
    }
  }

  UiGeometry.Bounds viewport() {
    Object window = Reflect.call(game.minecraft, "getWindow");
    return new UiGeometry.Bounds(
        0,
        0,
        ((Number) Reflect.call(window, "getGuiScaledWidth")).doubleValue(),
        ((Number) Reflect.call(window, "getGuiScaledHeight")).doubleValue());
  }

  Map<String, Object> snapshot(int limit) {
    Object screen = game.screen();
    var snapshot = new LinkedHashMap<String, Object>();
    snapshot.put("frame", io.github.billstark001.minedriver.hooks.FrameClock.count());
    snapshot.put("screen", screen == null ? null : screen.getClass().getName());
    snapshot.put("screenId", screenId());
    snapshot.put("coordinateSpace", "gui");
    snapshot.put("viewport", viewport());
    snapshot.put("window", game.media.window(Map.of()));
    snapshot.put("occlusion", "unknown");
    snapshot.put("elements", live(limit).stream().map(Live::node).toList());
    snapshot.put("slots", UiSlots.snapshot(game));
    snapshot.put("render", game.visuals.snapshot());
    return snapshot;
  }

  String screenId() {
    Object screen = game.screen();
    return screen == null
        ? null
        : screen.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(screen));
  }

  Object expand(Map<String, Object> parameters) {
    Live selected = select(Parameters.object(parameters, "selector"));
    if (!UiGeometry.group(selected.widget()))
      throw new DriverException("UNSUPPORTED_WIDGET", "Widget is not an expandable Cloth category");
    if (!(parameters.get("expanded") instanceof Boolean expanded))
      throw Parameters.invalid("expanded must be boolean");
    Reflect.call(selected.widget(), "setExpanded", expanded);
    if (!Boolean.valueOf(expanded).equals(Reflect.call(selected.widget(), "isExpanded")))
      throw new DriverException(
          "UI_EFFECT_NOT_OBSERVED", "Category did not reach requested expansion state");
    return Map.of("channel", "semantic", "expanded", expanded, "path", selected.node().path());
  }

  Live select(Map<String, Object> selector) {
    var supported = List.of("key", "label", "type", "path", "stableId");
    if (selector.isEmpty() || selector.keySet().stream().anyMatch(key -> !supported.contains(key)))
      throw Parameters.invalid("Selector needs at least one of key, label, type, path, stableId");
    var matches =
        live(8192).stream()
            .filter(
                item -> {
                  Node node = item.node();
                  var values = new LinkedHashMap<String, Object>();
                  values.put("key", node.key());
                  values.put("label", node.label());
                  values.put("type", node.type());
                  values.put("path", node.path());
                  values.put("stableId", node.stableId());
                  return selector.entrySet().stream()
                      .allMatch(
                          entry ->
                              entry.getValue() instanceof String
                                  && entry.getValue().equals(values.get(entry.getKey())));
                })
            .toList();
    if (matches.isEmpty())
      throw new DriverException("WIDGET_NOT_FOUND", "No widget matches " + selector);
    if (matches.size() != 1)
      throw new DriverException("AMBIGUOUS_WIDGET", matches.size() + " widgets match " + selector);
    Live result = matches.get(0);
    if (!result.node().active() || !result.node().visible())
      throw new DriverException("WIDGET_DISABLED", "Widget is disabled or hidden: " + selector);
    return result;
  }

  Object activate(Map<String, Object> parameters) {
    Live selected = select(Parameters.object(parameters, "selector"));
    if (!Reflect.hasMethod(selected.widget(), "onPress", 1))
      throw new DriverException("UNSUPPORTED_WIDGET", "Widget has no semantic onPress action");
    Object event =
        Reflect.create(
            game.type("net.minecraft.client.input.KeyEvent"),
            Reflect.field(game.type("com.mojang.blaze3d.platform.InputConstants"), "KEY_RETURN"),
            0,
            0);
    Reflect.call(selected.widget(), "onPress", event);
    return Map.of("channel", "semantic", "widget", selected.node());
  }

  Object text(Map<String, Object> parameters) {
    Live selected = select(Parameters.object(parameters, "selector"));
    if (!Reflect.hasMethod(selected.widget(), "setValue", 1))
      throw new DriverException("UNSUPPORTED_WIDGET", "Widget has no text value setter");
    Reflect.call(selected.widget(), "setValue", Parameters.string(parameters, "value", null));
    return Map.of("channel", "semantic", "path", selected.node().path());
  }

  Object click(Map<String, Object> parameters) {
    Object screen = game.screen();
    if (screen == null)
      throw new DriverException("NO_SCREEN", "A screen is required for GUI input");
    if (game.overlay() != null)
      throw new DriverException("INPUT_BLOCKED", "An overlay covers the screen");
    if (parameters.containsKey("screenId") && !parameters.get("screenId").equals(screenId()))
      throw new DriverException("UI_CHANGED", "Screen differs from the selected snapshot");
    Live selected = null;
    double x;
    double y;
    int targets =
        (parameters.containsKey("selector") ? 1 : 0)
            + (parameters.containsKey("slot") ? 1 : 0)
            + (parameters.containsKey("x") || parameters.containsKey("y") ? 1 : 0);
    if (targets != 1)
      throw Parameters.invalid("Choose exactly one click target: selector, slot, or x and y");
    if (parameters.containsKey("slot")) {
      int index = Parameters.integer(parameters, "slot", -1, 0, 8191);
      Map<String, Object> slot =
          UiSlots.snapshot(game).stream()
              .filter(value -> ((Number) value.get("menuSlot")).intValue() == index)
              .findFirst()
              .orElseThrow(
                  () ->
                      new DriverException("SLOT_NOT_FOUND", "No visible container slot " + index));
      if (!Boolean.TRUE.equals(slot.get("active")))
        throw new DriverException("SLOT_DISABLED", "Container slot is inactive");
      UiGeometry.Bounds bounds = (UiGeometry.Bounds) slot.get("clippedBounds");
      if (bounds.empty())
        throw new DriverException("WIDGET_CLIPPED", "Slot is outside its viewport");
      x = bounds.x() + bounds.width() / 2;
      y = bounds.y() + bounds.height() / 2;
    } else if (parameters.containsKey("selector")) {
      selected = select(Parameters.object(parameters, "selector"));
      UiGeometry.Bounds bounds = selected.node().clippedBounds();
      if (bounds == null)
        throw new DriverException("NO_WIDGET_BOUNDS", "Widget has no known hit region");
      if (bounds.empty())
        throw new DriverException("WIDGET_CLIPPED", "Widget hit region is outside its viewport");
      String part = Parameters.string(parameters, "part", "hit");
      if (!part.equals("hit") && !(part.equals("header") && UiGeometry.group(selected.widget())))
        throw Parameters.invalid("Unsupported click part " + part);
      x = bounds.x() + bounds.width() / 2.0;
      y = bounds.y() + bounds.height() / 2.0;
    } else {
      if (!parameters.containsKey("x") || !parameters.containsKey("y"))
        throw Parameters.invalid("Both x and y are required");
      x = Parameters.decimal(parameters, "x", 0);
      y = Parameters.decimal(parameters, "y", 0);
    }
    Object button =
        Reflect.create(
            game.type("net.minecraft.client.input.MouseButtonInfo"),
            inputConstant(game, parameters, "button", "LEFT", "MOUSE_BUTTON_", 8),
            Parameters.integer(parameters, "modifiers", 0, 0, 65535));
    Object event =
        Reflect.create(game.type("net.minecraft.client.input.MouseButtonEvent"), x, y, button);
    Object window = Reflect.call(game.minecraft, "getWindow");
    if (x < 0
        || y < 0
        || x >= ((Number) Reflect.call(window, "getGuiScaledWidth")).doubleValue()
        || y >= ((Number) Reflect.call(window, "getGuiScaledHeight")).doubleValue())
      throw new DriverException("INPUT_OUTSIDE_VIEWPORT", "Click lies outside the GUI viewport");
    Boolean expandedBefore = selected == null ? null : selected.node().expanded();
    game.input.pointer(x, y);
    int buttonCode = ((Number) Reflect.call(button, "button")).intValue();
    game.input.button(buttonCode, true);
    boolean consumed;
    try {
      Reflect.call(screen, "mouseMoved", x, y);
      consumed = Boolean.TRUE.equals(Reflect.call(screen, "mouseClicked", event, false));
    } finally {
      try {
        Reflect.call(screen, "mouseReleased", event);
      } finally {
        game.input.button(buttonCode, false);
      }
    }
    if (!consumed)
      throw new DriverException(
          "INPUT_NOT_CONSUMED", "Screen did not consume click at " + x + ", " + y);
    if (expandedBefore != null
        && expandedBefore.equals(Reflect.call(selected.widget(), "isExpanded")))
      throw new DriverException(
          "UI_EFFECT_NOT_OBSERVED", "Click was consumed but category expansion did not change");
    return Map.of("channel", "input", "consumed", true, "x", x, "y", y);
  }

  Object scroll(Map<String, Object> parameters) {
    Object screen = game.screen();
    if (screen == null)
      throw new DriverException("NO_SCREEN", "A screen is required for scrolling");
    return Reflect.call(
        screen,
        "mouseScrolled",
        Parameters.decimal(parameters, "x", 0),
        Parameters.decimal(parameters, "y", 0),
        Parameters.decimal(parameters, "horizontal", 0),
        Parameters.decimal(parameters, "vertical", 0));
  }

  static int inputConstant(
      GameRuntime game,
      Map<String, Object> parameters,
      String field,
      String fallback,
      String prefix,
      int maximum) {
    Object value = parameters.getOrDefault(field, fallback);
    if (value instanceof String name) {
      if (!name.matches("[A-Z0-9_]+")) throw Parameters.invalid("Invalid input name " + name);
      return ((Number)
              Reflect.field(game.type("com.mojang.blaze3d.platform.InputConstants"), prefix + name))
          .intValue();
    }
    return Parameters.integer(parameters, field, 0, 0, maximum);
  }

  static String text(Object component) {
    return component == null ? null : Reflect.call(component, "getString").toString();
  }

  static String key(Object component) {
    if (component == null) return null;
    Object contents = Reflect.call(component, "getContents");
    return Reflect.hasMethod(contents, "getKey", 0)
        ? Reflect.call(contents, "getKey").toString()
        : null;
  }

  private static boolean flag(Object widget, String field, String method, boolean fallback) {
    if (field != null) {
      Object value = Reflect.optionalField(widget, field);
      if (value instanceof Boolean result) return result;
    }
    return method != null && Reflect.hasMethod(widget, method, 0)
        ? Boolean.TRUE.equals(Reflect.call(widget, method))
        : fallback;
  }
}
