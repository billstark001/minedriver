package io.github.billstark001.minedriver.gradle;

import io.github.billstark001.minedriver.protocol.Json;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import org.gradle.work.DisableCachingByDefault;

/** Per-consuming-project generated data; no Mojang/Yarn mapping data is distributed in our JARs. */
@DisableCachingByDefault(because = "Uses locally cached upstream development mappings")
public abstract class PrepareMappings extends DefaultTask {
  @InputFile
  @PathSensitive(PathSensitivity.NONE)
  public abstract RegularFileProperty getLoomMappings();

  @InputFile
  @PathSensitive(PathSensitivity.NONE)
  public abstract RegularFileProperty getClientJar();

  @Input
  public abstract Property<String> getMinecraftVersion();

  @Input
  public abstract Property<String> getMappingsUrl();

  @Input
  public abstract Property<String> getMappingsSha1();

  @Input
  public abstract Property<Boolean> getOffline();

  @LocalState
  public abstract RegularFileProperty getMojangCache();

  @OutputFile
  public abstract RegularFileProperty getOutputFile();

  @TaskAction
  public void prepare() throws IOException {
    Path tiny = getLoomMappings().get().getAsFile().toPath();
    String version = getMinecraftVersion().get();
    Map<String, Object> data;
    if (RuntimeMappings.mojangNamed(tiny)) {
      data = Map.of("format", 1, "minecraft", version, "namespace", "mojang", "classes", List.of());
    } else {
      Path mojang = getMojangCache().get().getAsFile().toPath();
      if (!Files.exists(mojang) || !sha1(mojang).equals(getMappingsSha1().get())) {
        if (getOffline().get())
          throw new IOException(
              "Official client mappings must be cached before an offline Yarn launch: " + mojang);
        Files.createDirectories(mojang.getParent());
        var connection = URI.create(getMappingsUrl().get()).toURL().openConnection();
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(30000);
        Path temporary = Files.createTempFile(mojang.getParent(), "client-mappings-", ".tmp");
        try {
          try (var input = connection.getInputStream()) {
            Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
          }
          if (!sha1(temporary).equals(getMappingsSha1().get()))
            throw new IOException("Official mappings SHA-1 mismatch");
          Files.move(temporary, mojang, StandardCopyOption.REPLACE_EXISTING);
        } finally {
          Files.deleteIfExists(temporary);
        }
      }
      data =
          RuntimeMappings.generate(
              version, tiny, mojang, getClientJar().get().getAsFile().toPath());
    }
    Json.write(getOutputFile().get().getAsFile().toPath(), data);
  }

  private static String sha1(Path file) throws IOException {
    try {
      return HexFormat.of()
          .formatHex(MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(file)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new AssertionError(impossible);
    }
  }
}
