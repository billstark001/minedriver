package io.github.billstark001.minedriver.agent;

import io.github.billstark001.minedriver.api.DriverException;
import java.util.List;
import java.util.Map;

final class Parameters {
  private Parameters() {}

  static String string(Map<String, ?> parameters, String key, String fallback) {
    Object value = parameters.get(key);
    if (value == null) {
      if (fallback == null) throw invalid("Missing " + key);
      return fallback;
    }
    if (!(value instanceof String text)) throw invalid(key + " must be a string");
    return text;
  }

  static int integer(
      Map<String, ?> parameters, String key, int fallback, int minimum, int maximum) {
    double number = decimal(parameters, key, fallback);
    if (number < minimum || number > maximum || number != Math.rint(number))
      throw invalid(key + " must be an integer in " + minimum + ".." + maximum);
    return (int) number;
  }

  static double decimal(Map<String, ?> parameters, String key, double fallback) {
    Object value = parameters.get(key);
    if (value == null) return fallback;
    if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue()))
      throw invalid(key + " must be a finite number");
    return number.doubleValue();
  }

  static boolean bool(Map<String, ?> parameters, String key, boolean fallback) {
    Object value = parameters.get(key);
    if (value == null) return fallback;
    if (!(value instanceof Boolean result)) throw invalid(key + " must be boolean");
    return result;
  }

  @SuppressWarnings("unchecked")
  static Map<String, Object> object(Map<String, ?> parameters, String key) {
    Object value = parameters.get(key);
    if (!(value instanceof Map<?, ?> map)) throw invalid(key + " must be an object");
    for (Object item : map.keySet())
      if (!(item instanceof String)) throw invalid("Object keys must be strings");
    return (Map<String, Object>) map;
  }

  static List<?> list(Map<String, ?> parameters, String key) {
    Object value = parameters.get(key);
    if (value == null) return List.of();
    if (!(value instanceof List<?> result)) throw invalid(key + " must be an array");
    return result;
  }

  static DriverException invalid(String message) {
    return new DriverException("INVALID_ARGUMENT", message);
  }
}
