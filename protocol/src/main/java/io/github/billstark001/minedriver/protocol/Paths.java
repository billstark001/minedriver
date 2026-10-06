package io.github.billstark001.minedriver.protocol;

import java.nio.file.Path;

public final class Paths {
  private Paths() {}

  public static Path child(Path directory, String name) {
    if (name == null
        || name.isBlank()
        || name.length() > 128
        || !name.matches("[A-Za-z0-9][A-Za-z0-9._-]*")
        || name.contains(".."))
      throw new IllegalArgumentException("Expected a simple artifact name");
    Path root = directory.toAbsolutePath().normalize();
    Path result = root.resolve(name).normalize();
    if (!result.startsWith(root))
      throw new IllegalArgumentException("Artifact escapes output directory");
    return result;
  }
}
