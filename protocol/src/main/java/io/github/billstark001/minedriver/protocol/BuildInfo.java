package io.github.billstark001.minedriver.protocol;

import java.io.IOException;
import java.util.Properties;

/** Build-generated metadata shared by the agent, CLI and plugin. */
public final class BuildInfo {
  public static final String VERSION = version();

  private BuildInfo() {}

  private static String version() {
    try (var input = BuildInfo.class.getResourceAsStream("build.properties")) {
      if (input == null) throw new IllegalStateException("MineDriver build metadata is missing");
      var values = new Properties();
      values.load(input);
      String version = values.getProperty("version");
      if (version == null || version.isBlank() || version.contains("${"))
        throw new IllegalStateException("MineDriver build version is missing or unexpanded");
      return version;
    } catch (IOException failure) {
      throw new IllegalStateException("Cannot read MineDriver build metadata", failure);
    }
  }
}
