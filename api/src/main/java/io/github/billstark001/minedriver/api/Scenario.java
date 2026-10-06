package io.github.billstark001.minedriver.api;

/** Implement in src/minedriver/java; it is loaded for this session, not shipped with the mod. */
@FunctionalInterface
public interface Scenario {
  void run(ProbeContext context) throws Exception;
}
