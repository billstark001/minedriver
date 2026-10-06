package io.github.billstark001.minedriver.gradle;

import static org.junit.jupiter.api.Assertions.*;

import io.github.billstark001.minedriver.protocol.Json;
import java.nio.file.Path;
import java.util.Map;
import org.gradle.api.GradleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResultVerifierTest {
  @TempDir Path temporary;

  @Test
  void absentStaleIncompleteAndFailedResultsNeverPass() throws Exception {
    Path result = temporary.resolve("result.json");
    assertThrows(GradleException.class, () -> ResultVerifier.verify(result, "current"));
    Json.write(result, Map.of("protocol", 1, "runId", "old", "complete", true, "status", "PASS"));
    assertThrows(GradleException.class, () -> ResultVerifier.verify(result, "current"));
    Json.write(
        result, Map.of("protocol", 1, "runId", "current", "complete", false, "status", "PASS"));
    assertThrows(GradleException.class, () -> ResultVerifier.verify(result, "current"));
    Json.write(
        result,
        Map.of(
            "protocol",
            1,
            "runId",
            "current",
            "complete",
            true,
            "status",
            "FAIL",
            "failure",
            "explicit"));
    assertThrows(GradleException.class, () -> ResultVerifier.verify(result, "current"));
    Json.write(
        result, Map.of("protocol", 1, "runId", "current", "complete", true, "status", "PASS"));
    ResultVerifier.verify(result, "current");
  }
}
