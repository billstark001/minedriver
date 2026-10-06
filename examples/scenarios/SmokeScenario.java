package example;

import io.github.billstark001.minedriver.api.ProbeContext;
import io.github.billstark001.minedriver.api.Scenario;
import io.github.billstark001.minedriver.api.Selector;
import java.util.Map;

/** Copy to src/minedriver/java/example/SmokeScenario.java in a consuming mod project. */
public final class SmokeScenario implements Scenario {
  @Override
  public void run(ProbeContext context) {
    context.click(Selector.key("menu.options"));
    context.call(
        "wait.state",
        Map.of(
            "path", "screen", "equals", "net.minecraft.client.gui.screens.options.OptionsScreen"));
    context.screenshot("options");
    context.call("input.key", Map.of("key", "ESCAPE"));
  }
}
