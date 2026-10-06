package io.github.billstark001.minedriver.protocol;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProtocolTest {
  @TempDir Path temporary;

  @Test
  void atomicJsonAndConnectionValidation() throws Exception {
    var file = temporary.resolve("nested/result.json");
    Json.write(file, Map.of("status", "PASS"));
    assertEquals("PASS", Json.read(file).getAsJsonObject().get("status").getAsString());
    assertThrows(
        IllegalArgumentException.class,
        () -> new Connection(1, "test", "http://example.com:1", "a".repeat(40), 1));
    assertThrows(
        IllegalArgumentException.class,
        () -> new Connection(1, "test", "http://127.0.0.1:1", "short", 1));
    assertThrows(IllegalArgumentException.class, () -> Paths.child(temporary, "../escape"));
    assertThrows(IllegalArgumentException.class, () -> Paths.child(temporary, "a..b"));
  }
}
