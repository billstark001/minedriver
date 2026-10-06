package io.github.billstark001.minedriver.protocol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class Json {
  public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().serializeNulls().create();
  public static final Gson PRETTY =
      new GsonBuilder().disableHtmlEscaping().serializeNulls().setPrettyPrinting().create();

  private Json() {}

  public static JsonElement read(Path path) throws IOException {
    try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
      return JsonParser.parseReader(reader);
    }
  }

  public static void write(Path path, Object value) throws IOException {
    Files.createDirectories(path.toAbsolutePath().getParent());
    Path temporary =
        Files.createTempFile(path.toAbsolutePath().getParent(), ".minedriver-", ".json");
    try {
      Files.writeString(temporary, PRETTY.toJson(value), StandardCharsets.UTF_8);
      try {
        Files.move(
            temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (java.nio.file.AtomicMoveNotSupportedException unavailable) {
        Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(temporary);
    }
  }
}
