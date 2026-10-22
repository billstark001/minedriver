package example;

import io.github.billstark001.minedriver.api.ProbeContext;
import io.github.billstark001.minedriver.api.Scenario;

/** An active pipeline and actual final-pass pixels are both required for success. */
public final class ShaderActiveScenario implements Scenario {
  @Override
  public void run(ProbeContext context) throws Exception {
    try (var restore = ShaderChecks.restoreAfter(context)) {
      ShaderChecks.world(context);
      ShaderChecks.hideHud(context);
      ShaderChecks.capture(context, "shaders-off", false);
      String pack = System.getProperty("minedriver.iris.activePack", ShaderChecks.VALID);
      ShaderChecks.select(context, pack, true);
      ShaderChecks.awaitActive(context, pack);
      ShaderChecks.capture(context, "shaders-active", true);
      ShaderChecks.requireActive(context, pack);
    }
    context.call("world.disconnect");
  }
}
