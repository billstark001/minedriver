package io.github.billstark001.minedriver.api;

import java.util.LinkedHashMap;
import java.util.Map;

/** Every supplied criterion must match exactly; zero or multiple matches are failures. */
public record Selector(String key, String label, String type, String path, String stableId) {
  public Selector {
    if (key == null && label == null && type == null && path == null && stableId == null)
      throw new IllegalArgumentException("At least one selector criterion is required");
  }

  public static Selector key(String key) {
    return new Selector(key, null, null, null, null);
  }

  public static Selector label(String label) {
    return new Selector(null, label, null, null, null);
  }

  public static Selector id(String id) {
    return new Selector(null, null, null, null, id);
  }

  public Map<String, Object> parameters() {
    var result = new LinkedHashMap<String, Object>();
    if (key != null) result.put("key", key);
    if (label != null) result.put("label", label);
    if (type != null) result.put("type", type);
    if (path != null) result.put("path", path);
    if (stableId != null) result.put("stableId", stableId);
    return result;
  }
}
