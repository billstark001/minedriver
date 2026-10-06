package io.github.billstark001.minedriver.api;

import java.util.Map;

public interface ExtensionRegistry {
  void register(String command, String description, boolean readOnly, Handler handler);

  /** Supplies an identity that survives translation and label changes. */
  void identify(Object widget, String stableId);

  @FunctionalInterface
  interface Handler {
    Object handle(ProbeContext context, Map<String, Object> parameters) throws Exception;
  }
}
