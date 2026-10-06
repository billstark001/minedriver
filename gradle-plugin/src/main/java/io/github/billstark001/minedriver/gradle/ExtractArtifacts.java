package io.github.billstark001.minedriver.gradle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

@org.gradle.work.DisableCachingByDefault(
    because = "Copies embedded content addressed artifacts; local up-to-date checks suffice")
public abstract class ExtractArtifacts extends DefaultTask {
  @OutputFile
  public abstract RegularFileProperty getAgentJar();

  @OutputFile
  public abstract RegularFileProperty getApiJar();

  @TaskAction
  public void extract() throws IOException {
    extract("agent.jar", getAgentJar());
    extract("api.jar", getApiJar());
  }

  static byte[] resource(String name) throws IOException {
    try (var stream =
        ExtractArtifacts.class.getResourceAsStream(
            "/io/github/billstark001/minedriver/artifacts/" + name)) {
      if (stream == null) throw new IOException("Embedded artifact missing: " + name);
      return stream.readAllBytes();
    }
  }

  static String hash(String name) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(resource(name)));
    } catch (IOException | NoSuchAlgorithmException error) {
      throw new IllegalStateException(error);
    }
  }

  private static void extract(String name, RegularFileProperty property) throws IOException {
    var target = property.get().getAsFile().toPath();
    Files.createDirectories(target.getParent());
    byte[] data = resource(name);
    if (Files.exists(target) && java.util.Arrays.equals(Files.readAllBytes(target), data)) return;
    var temporary = Files.createTempFile(target.getParent(), "extract-", ".tmp");
    try {
      Files.write(temporary, data);
      Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }
}
