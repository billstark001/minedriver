package net.neoforged.fml.i18n;

public final class FMLTranslations {
  private FMLTranslations() {}

  public static String translateIssueEnglish(net.neoforged.fml.ModLoader.Issue issue) {
    return issue.translationKey();
  }

  public static String parseMessage(String key, Object... arguments) {
    return "Continue";
  }
}
