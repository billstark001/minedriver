package io.github.billstark001.minedriver.agent;

import io.github.billstark001.minedriver.hooks.VisualHooks;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded per-frame value snapshots from the native text/item bounds, including transforms. */
final class VisualCapture implements VisualHooks.Listener, AutoCloseable {
  private final GameRuntime game;
  private final List<Map<String, Object>> pending = new ArrayList<>();
  private final IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<>();
  private volatile List<Map<String, Object>> completed = List.of();
  private volatile long frame;
  private volatile String screenId;
  private String pendingScreen;
  private volatile long dropped;
  private volatile String failure;

  VisualCapture(GameRuntime game) {
    this.game = game;
    VisualHooks.listener(this);
  }

  @Override
  public void begin() {
    pending.clear();
    seen.clear();
    pendingScreen = game.ui.screenId();
  }

  @Override
  public void capture(Object state, Object bounds) {
    if (seen.containsKey(state)) return;
    if (pending.size() >= 8192) {
      dropped++;
      return;
    }
    seen.put(state, true);
    try {
      var value = new LinkedHashMap<String, Object>();
      String type = state.getClass().getName();
      boolean text = type.endsWith(".GuiTextRenderState");
      value.put("type", text ? "text" : "item");
      value.put("clippedBounds", rectangle(bounds));
      value.put("boundsSource", "native-render-state");
      Object original;
      Object pose;
      if (text) {
        StringBuilder string = new StringBuilder();
        Object sink =
            Proxy.newProxyInstance(
                game.loader,
                new Class<?>[] {game.type("net.minecraft.util.FormattedCharSink")},
                (proxy, method, arguments) -> {
                  if (method.getName().equals("accept")) {
                    string.appendCodePoint(((Number) arguments[2]).intValue());
                    return true;
                  }
                  return null;
                });
        Reflect.call(Reflect.field(state, "text"), "accept", sink);
        value.put("text", string.toString());
        original = Reflect.call(Reflect.call(state, "ensurePrepared"), "bounds");
        pose = Reflect.field(state, "pose");
      } else {
        value.put("text", null);
        original = Reflect.call(state, "oversizedItemBounds");
        if (original == null)
          original =
              Reflect.create(
                  game.type("net.minecraft.client.gui.navigation.ScreenRectangle"),
                  Reflect.call(state, "x"),
                  Reflect.call(state, "y"),
                  16,
                  16);
        pose = Reflect.call(state, "pose");
      }
      value.put(
          "layoutBounds",
          original == null ? null : rectangle(Reflect.call(original, "transformMaxBounds", pose)));
      pending.add(java.util.Collections.unmodifiableMap(value));
    } catch (RuntimeException error) {
      failure = error.toString();
    }
  }

  @Override
  public void end(long sequence) {
    completed = List.copyOf(pending);
    screenId = pendingScreen;
    frame = sequence;
    pending.clear();
    seen.clear();
  }

  Map<String, Object> snapshot() {
    var result = new LinkedHashMap<String, Object>();
    result.put("frame", frame);
    result.put("screenId", screenId);
    result.put("draws", completed);
    result.put("dropped", dropped);
    result.put("failure", failure == null ? VisualHooks.failure() : failure);
    result.put("installedHooks", VisualHooks.installed());
    return result;
  }

  static UiGeometry.Bounds rectangle(Object rectangle) {
    return new UiGeometry.Bounds(
        ((Number) Reflect.call(rectangle, "left")).doubleValue(),
        ((Number) Reflect.call(rectangle, "top")).doubleValue(),
        ((Number) Reflect.call(rectangle, "width")).doubleValue(),
        ((Number) Reflect.call(rectangle, "height")).doubleValue());
  }

  @Override
  public void close() {
    VisualHooks.listener(null);
    seen.clear();
    pending.clear();
  }
}
