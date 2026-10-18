package io.github.billstark001.minedriver.agent;

import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/** Copies immediate 1.21.1 GUI draws using the current pose and native scissor rectangle. */
final class LegacyVisualCapture {
  private LegacyVisualCapture() {}

  static Map<String, Object> capture(
      GameRuntime game, Object graphics, Object font, Object content, int x, int y) {
    if (content == null || (font == null && Boolean.TRUE.equals(Reflect.call(content, "isEmpty"))))
      return null;
    int width = font == null ? 16 : ((Number) Reflect.call(font, "width", content)).intValue();
    int height = font == null ? 16 : ((Number) Reflect.field(font, "lineHeight")).intValue();
    Object matrix = Reflect.call(Reflect.call(Reflect.call(graphics, "pose"), "last"), "pose");
    double a = number(matrix, "m00"), b = number(matrix, "m10"), tx = number(matrix, "m30");
    double c = number(matrix, "m01"), d = number(matrix, "m11"), ty = number(matrix, "m31");
    double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
    double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
    for (int dx : new int[] {0, width})
      for (int dy : new int[] {0, height}) {
        double px = a * (x + dx) + b * (y + dy) + tx;
        double py = c * (x + dx) + d * (y + dy) + ty;
        minX = Math.min(minX, px);
        maxX = Math.max(maxX, px);
        minY = Math.min(minY, py);
        maxY = Math.max(maxY, py);
      }
    var layout = new UiGeometry.Bounds(minX, minY, maxX - minX, maxY - minY);
    UiGeometry.Bounds clipped = layout.intersect(game.ui.viewport());
    var stack = (Deque<?>) Reflect.field(Reflect.field(graphics, "scissorStack"), "stack");
    if (!stack.isEmpty()) clipped = clipped.intersect(VisualCapture.rectangle(stack.peek()));
    var value = new LinkedHashMap<String, Object>();
    value.put("type", font == null ? "item" : "text");
    value.put("text", font == null ? null : VisualCapture.text(game, content));
    value.put("layoutBounds", layout);
    value.put("clippedBounds", clipped);
    value.put("boundsSource", "native-immediate-gui");
    return java.util.Collections.unmodifiableMap(value);
  }

  private static double number(Object value, String method) {
    return ((Number) Reflect.call(value, method)).doubleValue();
  }
}
