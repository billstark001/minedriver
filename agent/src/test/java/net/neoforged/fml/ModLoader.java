package net.neoforged.fml;

import java.util.List;

public final class ModLoader {
  public record Mod(String getModId) {}

  public record Issue(String severity, Mod affectedMod, String translationKey) {}

  public static List<Issue> issues = List.of();

  public static List<Issue> getLoadingIssues() {
    return issues;
  }

  private ModLoader() {}
}
