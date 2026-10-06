package io.github.billstark001.minedriver.agent;

import io.github.billstark001.minedriver.api.DriverException;
import java.util.IdentityHashMap;
import java.util.Map;

/** Extension results must be bounded JSON values, never live Minecraft object graphs. */
final class JsonValues {
  private JsonValues() {}

  static void validate(Object value) {
    visit(value, new IdentityHashMap<>(), 0, new int[] {0});
  }

  private static void visit(
      Object value, IdentityHashMap<Object, Boolean> path, int depth, int[] count) {
    if (depth > 32 || ++count[0] > 8192) throw invalid("Result exceeds depth or value limit");
    if (value == null || value instanceof String || value instanceof Boolean) return;
    if (value instanceof Number n) {
      if (!Double.isFinite(n.doubleValue())) throw invalid("Non-finite number");
      return;
    }
    if (path.put(value, true) != null) throw invalid("Cyclic result");
    try {
      if (value instanceof Map<?, ?> map) {
        for (var entry : map.entrySet()) {
          if (!(entry.getKey() instanceof String)) throw invalid("Map keys must be strings");
          visit(entry.getValue(), path, depth + 1, count);
        }
      } else if (value instanceof Iterable<?> iterable) {
        for (Object entry : iterable) visit(entry, path, depth + 1, count);
      } else
        throw invalid(
            "Return JSON primitives, maps or lists; received " + value.getClass().getName());
    } finally {
      path.remove(value);
    }
  }

  private static DriverException invalid(String message) {
    return new DriverException("INVALID_EXTENSION_RESULT", message);
  }
}
