package io.github.billstark001.minedriver.protocol;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileDiagnosticsTest {
  @TempDir Path directory;

  @Test
  void recordsExistenceTransitionsWithoutChangingOwnership() throws Exception {
    Path target = directory.resolve("run");
    var evidence = new FileDiagnostics(directory, "test");
    evidence.capture("before", target);
    Files.createDirectory(target);
    String owner = Files.getOwner(target).getName();
    evidence.capture("after", target);
    evidence.write();
    assertEquals(owner, Files.getOwner(target).getName());
    var observations =
        Json.read(directory.resolve("filesystem-test.json"))
            .getAsJsonObject()
            .getAsJsonArray("observations");
    assertFalse(observations.get(0).getAsJsonObject().get("exists").getAsBoolean());
    assertEquals(owner, observations.get(1).getAsJsonObject().get("owner").getAsString());
  }
}
