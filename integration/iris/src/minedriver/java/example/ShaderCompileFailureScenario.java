package example;

import io.github.billstark001.minedriver.api.DriverException;
import io.github.billstark001.minedriver.api.ProbeContext;
import io.github.billstark001.minedriver.api.Scenario;
import java.util.List;
import java.util.Map;

/** PASS means the deliberate compiler error was observed and the active-pack gate rejected it. */
public final class ShaderCompileFailureScenario implements Scenario {
  @Override
  public void run(ProbeContext context) throws Exception {
    try (var restore = ShaderChecks.restoreAfter(context)) {
      ShaderChecks.world(context);
      ShaderChecks.hideHud(context);
      ShaderChecks.select(context, ShaderChecks.VALID, true);
      ShaderChecks.awaitActive(context, ShaderChecks.VALID);
      ShaderChecks.capture(context, "before-compile-failure", true);
      ShaderChecks.select(context, ShaderChecks.INVALID, true);
      context.call("render.await", Map.of("frames", 8));
      var failed = ShaderChecks.status(context);
      ShaderChecks.write(context, "compile-failure.json", failed);
      context.require(
          Boolean.TRUE.equals(failed.get("configuredEnabled")), "Shaders were requested on");
      context.require(
          ShaderChecks.INVALID.equals(failed.get("configuredPack")), "Broken pack was selected");
      context.require(
          Boolean.FALSE.equals(failed.get("active")),
          "Compiler failure must not leave shaders active");
      context.require(
          Boolean.TRUE.equals(failed.get("fallback")), "Iris explicitly fell back to vanilla");
      var errors = (List<?>) failed.get("error");
      context.require(
          errors.toString().contains("ShaderCompileException")
              && errors.toString().contains(ShaderChecks.COMPILE_MARKER),
          "Failure must come from our deliberate shader compiler error: " + errors);
      String rejection = null;
      try {
        ShaderChecks.requireActive(context, ShaderChecks.INVALID);
      } catch (DriverException expected) {
        if (!"ASSERTION_FAILED".equals(expected.code())) throw expected;
        rejection = expected.getMessage();
      }
      context.require(rejection != null, "The same active-pack gate must reject silent fallback");
      ShaderChecks.write(
          context,
          "expected-rejection.json",
          Map.of("code", "ASSERTION_FAILED", "message", rejection));
      context.require(
          failed.equals(ShaderChecks.status(context)), "Status reads preserve compiler evidence");
      ShaderChecks.capture(context, "compile-failure-fallback", false);
      ShaderChecks.select(context, ShaderChecks.VALID, true);
      ShaderChecks.awaitActive(context, ShaderChecks.VALID);
      ShaderChecks.capture(context, "recovered-active", true);
    }
    context.call("world.disconnect");
  }
}
