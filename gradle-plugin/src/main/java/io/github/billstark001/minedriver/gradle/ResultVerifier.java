package io.github.billstark001.minedriver.gradle;

import com.google.gson.JsonObject;
import io.github.billstark001.minedriver.protocol.Json;
import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.api.GradleException;

final class ResultVerifier {
  private ResultVerifier() {}

  static void verify(Path path, String runId) {
    if (!Files.isRegularFile(path))
      throw new GradleException(
          "MineDriver client exited without a result. See " + path.getParent());
    try {
      JsonObject result = Json.read(path).getAsJsonObject();
      if (result.get("protocol").getAsInt() != 1
          || !runId.equals(result.get("runId").getAsString())
          || !result.get("complete").getAsBoolean())
        throw new GradleException(
            "MineDriver result is incomplete or belongs to another run: " + path);
      if (!"PASS".equals(result.get("status").getAsString()))
        throw new GradleException(
            "MineDriver failed: " + result.get("failure") + " (reports: " + path.getParent() + ")");
    } catch (GradleException error) {
      throw error;
    } catch (Exception invalid) {
      throw new GradleException("Invalid MineDriver result: " + path, invalid);
    }
  }
}
