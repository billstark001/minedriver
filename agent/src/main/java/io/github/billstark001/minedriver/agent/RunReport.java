package io.github.billstark001.minedriver.agent;

import io.github.billstark001.minedriver.protocol.Json;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class RunReport {
  private final AgentConfig config;
  private final long start = System.nanoTime();
  private final String started = Instant.now().toString();
  private final List<Map<String, Object>> steps = new ArrayList<>();
  private boolean complete;

  RunReport(AgentConfig config) {
    this.config = config;
  }

  synchronized void step(
      String command, String channel, long nanos, Object result, Throwable failure) {
    var item = new LinkedHashMap<String, Object>();
    item.put("command", command);
    item.put("channel", channel);
    item.put("durationMillis", nanos / 1e6);
    item.put("status", failure == null ? "PASS" : "FAIL");
    // Retain serializable summaries, never arbitrary game object graphs.
    String summary = result == null ? "null" : Json.GSON.toJson(result);
    item.put("result", summary.length() > 32768 ? summary.substring(0, 32768) + "…" : summary);
    if (failure != null) item.put("error", failure.toString());
    steps.add(item);
  }

  synchronized void finish(Throwable failure, Map<String, Object> environment) throws Exception {
    if (complete) return;
    double seconds = (System.nanoTime() - start) / 1e9;
    var result = new LinkedHashMap<String, Object>();
    result.put("protocol", 1);
    result.put("runId", config.runId());
    result.put("complete", true);
    result.put("status", failure == null ? "PASS" : "FAIL");
    result.put("started", started);
    result.put("finished", Instant.now().toString());
    result.put("durationSeconds", seconds);
    result.put("environment", environment);
    result.put("steps", List.copyOf(steps));
    String trace = "";
    if (failure != null) {
      var buffer = new StringWriter();
      failure.printStackTrace(new PrintWriter(buffer));
      trace = buffer.toString();
      result.put(
          "failure",
          Map.of(
              "code",
              failure instanceof io.github.billstark001.minedriver.api.DriverException d
                  ? d.code()
                  : "INTERNAL_ERROR",
              "message",
              failure.toString(),
              "trace",
              trace));
    }
    Files.createDirectories(config.output());
    String xml =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?><testsuite name=\"MineDriver\" tests=\"1\" failures=\""
            + (failure == null ? 0 : 1)
            + "\" time=\""
            + seconds
            + "\"><testcase classname=\""
            + escape(config.runId())
            + "\" name=\"scenario\" time=\""
            + seconds
            + "\">"
            + (failure == null
                ? ""
                : "<failure message=\""
                    + escape(failure.toString())
                    + "\">"
                    + escape(trace)
                    + "</failure>")
            + "</testcase></testsuite>";
    Files.writeString(config.output().resolve("junit.xml"), xml, StandardCharsets.UTF_8);
    Files.writeString(
        config.output().resolve("report.html"),
        "<!doctype html><meta charset=utf-8><title>MineDriver "
            + escape(config.runId())
            + "</title><style>body{font:15px system-ui;margin:2rem;max-width:1000px}pre{white-space:pre-wrap;background:#eee;padding:1rem}</style><h1>MineDriver: "
            + result.get("status")
            + "</h1><p>"
            + escape(config.runId())
            + " · "
            + seconds
            + " seconds</p><p><a href=\"junit.xml\">JUnit XML</a> · <a href=\"result.json\">JSON</a></p><pre>"
            + escape(Json.PRETTY.toJson(result))
            + "</pre>",
        StandardCharsets.UTF_8);
    // Completion marker goes last: partial reports must never be accepted as a passing run.
    Json.write(config.resultFile(), result);
    complete = true;
  }

  private static String escape(String value) {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;");
  }
}
