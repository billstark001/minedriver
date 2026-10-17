package io.github.billstark001.minedriver.hooks;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Transient render callback. The listener copies values; native objects are never retained here.
 */
public final class VisualHooks {
  public interface Listener {
    void begin();

    void capture(Object state, Object bounds);

    void end(long frame);
  }

  private static final Set<String> INSTALLED = ConcurrentHashMap.newKeySet();
  private static volatile Listener listener;
  private static volatile String failure;

  private VisualHooks() {}

  public static void listener(Listener value) {
    listener = value;
  }

  public static void installed(String name) {
    INSTALLED.add(name);
  }

  public static java.util.List<String> installed() {
    return INSTALLED.stream().sorted().toList();
  }

  public static void failure(String value) {
    failure = value;
  }

  public static String failure() {
    return failure;
  }

  public static void begin() {
    Listener value = listener;
    if (value != null) value.begin();
  }

  public static void capture(Object state, Object bounds) {
    Listener value = listener;
    if (value != null && bounds != null) value.capture(state, bounds);
  }

  public static void end(long frame) {
    Listener value = listener;
    if (value != null) value.end(frame);
  }
}
