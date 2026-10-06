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
      int x,
      int y,
      int width,
      int height,
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
    visit(screen, "0", true, true, new IdentityHashMap<>(), result, limit, 0);
    return result;
  }

  private void visit(
      Object widget,
      String path,
      boolean parentActive,
      boolean parentVisible,
      IdentityHashMap<Object, Boolean> visited,
      List<Live> output,
      int limit,
      int depth) {
    if (depth > 32 || visited.put(widget, true) != null) return;
    if (output.size() >= limit)
      throw new DriverException("UI_TREE_LIMIT", "Widget tree exceeds " + limit + " nodes");
    Object component =
        Reflect.hasMethod(widget, "getMessage", 0) ? Reflect.call(widget, "getMessage") : null;
    boolean active = parentActive && flag(widget, "active", "isActive", true);
    boolean visible = parentVisible && flag(widget, "visible", null, true);
    Object value =
        Reflect.hasMethod(widget, "getValue", 0) ? Reflect.call(widget, "getValue") : null;
    if (value != null
        && !(value instanceof String || value instanceof Number || value instanceof Boolean))
      value = value.toString();
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
            number(widget, "getX"),
            number(widget, "getY"),
            number(widget, "getWidth"),
            number(widget, "getHeight"),
            value);
    output.add(new Live(node, widget));
    if (Reflect.hasMethod(widget, "children", 0)) {
      Object children = Reflect.call(widget, "children");
      if (children instanceof Iterable<?> iterable) {
        int index = 0;
        for (Object child : iterable) {
          if (child != null)
            visit(child, path + "/" + index, active, visible, visited, output, limit, depth + 1);
          index++;
        }
      }
    }
  }

  List<Node> tree(int limit) {
    return live(limit).stream().map(Live::node).toList();
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
    double x;
    double y;
    if (parameters.containsKey("selector")) {
      Node node = select(Parameters.object(parameters, "selector")).node();
      if (node.width() <= 0 || node.height() <= 0)
        throw new DriverException("NO_WIDGET_BOUNDS", "Widget has no clickable bounds");
      x = node.x() + node.width() / 2.0;
      y = node.y() + node.height() / 2.0;
    } else {
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
    Reflect.call(screen, "mouseMoved", x, y);
    boolean consumed = Boolean.TRUE.equals(Reflect.call(screen, "mouseClicked", event, false));
    Reflect.call(screen, "mouseReleased", event);
    if (!consumed)
      throw new DriverException(
          "INPUT_NOT_CONSUMED", "Screen did not consume click at " + x + ", " + y);
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

  private static int number(Object widget, String method) {
    return Reflect.hasMethod(widget, method, 0)
        ? ((Number) Reflect.call(widget, method)).intValue()
        : 0;
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
