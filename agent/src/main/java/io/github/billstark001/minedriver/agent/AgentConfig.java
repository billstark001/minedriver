package io.github.billstark001.minedriver.agent;

import com.google.gson.JsonObject;
import io.github.billstark001.minedriver.protocol.Json;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

record AgentConfig(
    String runId,
    String mode,
    Path output,
    Path connectionFile,
    Path resultFile,
    Path runDirectory,
    long timeoutMillis,
    long operationTimeoutMillis,
    boolean allowUnsupportedRuntime,
    boolean allowProduction,
    boolean allowStartupWarnings,
    boolean unsafeReflection,
    List<String> scenarios,
    List<String> extensions,
    List<String> scenarioClasspath,
    JsonObject plan) {
  static AgentConfig read(Path file) throws IOException {
    JsonObject json = Json.read(file).getAsJsonObject();
    if (number(json, "protocol", -1) != 1)
      throw new IllegalArgumentException("Expected MineDriver configuration protocol 1");
    String mode = text(json, "mode", "check");
    if (!List.of("check", "interactive", "framework").contains(mode))
      throw new IllegalArgumentException("Unknown mode " + mode);
    String runId = text(json, "runId", null);
    if (runId == null || !runId.matches("[A-Za-z0-9._-]{1,100}"))
      throw new IllegalArgumentException("A valid runId is required");
    Path output = Path.of(text(json, "outputDirectory", null)).toAbsolutePath().normalize();
    long timeout = number(json, "timeoutMillis", mode.equals("interactive") ? 0 : 300_000);
    long operationTimeout = number(json, "operationTimeoutMillis", 30_000);
    if (timeout < 0 || operationTimeout <= 0 || operationTimeout > 3_600_000)
      throw new IllegalArgumentException("Invalid timeout");
    return new AgentConfig(
        runId,
        mode,
        output,
        Path.of(text(json, "connectionFile", output.resolve("connection.json").toString())),
        Path.of(text(json, "resultFile", output.resolve("result.json").toString())),
        Path.of(text(json, "runDirectory", ".")).toAbsolutePath().normalize(),
        timeout,
        operationTimeout,
        flag(json, "allowUnsupportedRuntime"),
        flag(json, "allowProduction"),
        flag(json, "allowStartupWarnings"),
        flag(json, "unsafeReflection"),
        strings(json, "scenarios"),
        strings(json, "extensions"),
        strings(json, "scenarioClasspath"),
        json.has("plan") ? json.getAsJsonObject("plan") : new JsonObject());
  }

  private static String text(JsonObject json, String key, String fallback) {
    if (!json.has(key) || json.get(key).isJsonNull()) return fallback;
    if (!json.get(key).isJsonPrimitive() || !json.getAsJsonPrimitive(key).isString())
      throw new IllegalArgumentException(key + " must be a string");
    return json.get(key).getAsString();
  }

  private static long number(JsonObject json, String key, long fallback) {
    if (!json.has(key)) return fallback;
    if (!json.get(key).isJsonPrimitive() || !json.getAsJsonPrimitive(key).isNumber())
      throw new IllegalArgumentException(key + " must be an integer");
    try {
      return json.get(key).getAsBigDecimal().longValueExact();
    } catch (ArithmeticException error) {
      throw new IllegalArgumentException(key + " must be an integer in long range", error);
    }
  }

  private static boolean flag(JsonObject json, String key) {
    if (!json.has(key)) return false;
    if (!json.get(key).isJsonPrimitive() || !json.getAsJsonPrimitive(key).isBoolean())
      throw new IllegalArgumentException(key + " must be boolean");
    return json.get(key).getAsBoolean();
  }

  private static List<String> strings(JsonObject json, String key) {
    var result = new ArrayList<String>();
    if (json.has(key))
      json.getAsJsonArray(key)
          .forEach(
              item -> {
                if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString())
                  throw new IllegalArgumentException(key + " must contain strings");
                result.add(item.getAsString());
              });
    return List.copyOf(result);
  }
}
