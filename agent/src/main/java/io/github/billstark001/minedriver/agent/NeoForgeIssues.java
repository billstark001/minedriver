package io.github.billstark001.minedriver.agent;

import io.github.billstark001.minedriver.api.DriverException;
import java.util.List;
import java.util.Map;

/** Reads NeoForge's issue model instead of inferring severity from localized button labels. */
final class NeoForgeIssues {
  record Issue(String severity, String modId, String key, String message) {
    String identity() {
      return modId + ":" + key;
    }
  }

  private NeoForgeIssues() {}

  static List<Issue> read(GameRuntime game) {
    Object issues = Reflect.call(game.type("net.neoforged.fml.ModLoader"), "getLoadingIssues");
    if (!(issues instanceof List<?> list))
      throw new DriverException("UNSUPPORTED_API", "NeoForge loading issues are not a list");
    return list.stream()
        .map(
            issue -> {
              Object mod = Reflect.call(issue, "affectedMod");
              return new Issue(
                  Reflect.call(issue, "severity").toString(),
                  mod == null ? "" : Reflect.call(mod, "getModId").toString(),
                  Reflect.call(issue, "translationKey").toString(),
                  Reflect.call(
                          game.type("net.neoforged.fml.i18n.FMLTranslations"),
                          "translateIssueEnglish",
                          issue)
                      .toString());
            })
        .toList();
  }

  static void requireAccepted(List<Issue> issues, List<String> accepted) {
    if (issues.isEmpty())
      throw new DriverException(
          "STARTUP_BLOCKED", "Loading error screen has no inspectable issues");
    if (issues.stream().anyMatch(issue -> !issue.severity().equals("WARNING")))
      throw new DriverException("STARTUP_ERRORS", "NeoForge loading failed: " + issues);
    if (issues.stream().anyMatch(issue -> !accepted.contains(issue.identity())))
      throw new DriverException(
          "STARTUP_WARNINGS",
          "Title screen blocked by loading warnings: "
              + issues
              + "; diagnose them before configuring acceptedStartupWarnings (modId:translationKey)");
  }

  static void proceed(GameRuntime game) {
    // NeoForge 26.2 formats this message eagerly into a literal Component; it has no key.
    String label =
        Reflect.call(
                game.type("net.neoforged.fml.i18n.FMLTranslations"),
                "parseMessage",
                "fml.button.continue.launch",
                new Object[0])
            .toString();
    game.ui.activate(Map.of("selector", Map.of("label", label)));
  }
}
