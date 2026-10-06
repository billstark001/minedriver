package io.github.billstark001.minedriver.api;

/** Custom namespaces expose mod state and actions without depending on this API in main sources. */
public interface DriverExtension {
  void register(ExtensionRegistry registry);
}
