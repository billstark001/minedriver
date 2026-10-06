package io.github.billstark001.minedriver.cli;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.billstark001.minedriver.protocol.Json;
import io.github.billstark001.minedriver.protocol.RpcClient;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/** MCP 2025-11-25 tools over newline-delimited stdio. Stdout contains only protocol messages. */
final class McpServer {
  private final RpcClient rpc;
  private boolean initialized;
  private boolean negotiated;

  McpServer(RpcClient rpc) {
    this.rpc = rpc;
  }

  void serve(InputStream input, OutputStream output) throws Exception {
    var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
    var writer = new PrintWriter(output, true, StandardCharsets.UTF_8);
    String line;
    while ((line = reader.readLine()) != null) {
      JsonObject request = null;
      JsonObject response = new JsonObject();
      response.addProperty("jsonrpc", "2.0");
      try {
        if (line.length() > 1_048_576) throw new IllegalArgumentException("Message exceeds 1 MiB");
        request = JsonParser.parseString(line).getAsJsonObject();
        response.add("id", request.has("id") ? request.get("id") : JsonNull.INSTANCE);
        if (!request.has("jsonrpc") || !"2.0".equals(request.get("jsonrpc").getAsString()))
          throw new IllegalArgumentException("Expected JSON-RPC 2.0");
        String method = request.get("method").getAsString();
        if (!request.has("id")) {
          if (method.equals("notifications/initialized") && negotiated) initialized = true;
          continue;
        }
        response.add(
            "result",
            Json.GSON.toJsonTree(
                dispatch(
                    method,
                    request.has("params") ? request.getAsJsonObject("params") : new JsonObject())));
      } catch (Exception failure) {
        if (!response.has("id")) response.add("id", JsonNull.INSTANCE);
        response.add(
            "error",
            Json.GSON.toJsonTree(
                Map.of(
                    "code",
                    request == null
                        ? -32700
                        : failure instanceof ProtocolFailure protocol ? protocol.code : -32602,
                    "message",
                    failure.getMessage() == null ? failure.toString() : failure.getMessage())));
      }
      writer.println(Json.GSON.toJson(response));
    }
  }

  private Object dispatch(String method, JsonObject parameters) throws Exception {
    if (method.equals("initialize")) {
      if (negotiated) throw new ProtocolFailure(-32600, "Already initialized");
      if (!parameters.has("protocolVersion"))
        throw new ProtocolFailure(-32602, "protocolVersion is required");
      negotiated = true;
      return Map.of(
          "protocolVersion",
          "2025-11-25",
          "capabilities",
          Map.of("tools", Map.of("listChanged", false)),
          "serverInfo",
          Map.of("name", "MineDriver", "version", "0.1.0-SNAPSHOT"),
          "instructions",
          "Controls the local development Minecraft client selected by its connection file. Mutating actions may change its isolated world.");
    }
    if (method.equals("ping")) return Map.of();
    if (!initialized)
      throw new ProtocolFailure(-32600, "Send initialize and notifications/initialized first");
    if (method.equals("tools/list")) {
      JsonArray commands =
          rpc.call("session.commands", Map.of(), Duration.ofSeconds(10)).getAsJsonArray();
      var tools = new ArrayList<Object>();
      for (JsonElement item : commands) {
        JsonObject command = item.getAsJsonObject();
        tools.add(
            Map.of(
                "name",
                command.get("name").getAsString(),
                "description",
                command.get("description").getAsString()
                    + " (channel: "
                    + command.get("channel").getAsString()
                    + ")",
                "inputSchema",
                command.getAsJsonObject("inputSchema"),
                "annotations",
                Map.of(
                    "readOnlyHint",
                    command.get("readOnly").getAsBoolean(),
                    "openWorldHint",
                    command.get("name").getAsString().equals("world.connect")
                        || command.get("name").getAsString().startsWith("custom."))));
      }
      return Map.of("tools", tools);
    }
    if (method.equals("tools/call")) {
      try {
        String name = parameters.get("name").getAsString();
        JsonElement result =
            rpc.call(
                name,
                parameters.has("arguments") ? parameters.get("arguments") : new JsonObject(),
                Duration.ofMinutes(5));
        var content = new ArrayList<Object>();
        content.add(Map.of("type", "text", "text", Json.GSON.toJson(result)));
        if (name.equals("screenshot.capture")
            && result.isJsonObject()
            && result.getAsJsonObject().has("path")) {
          Path path = Path.of(result.getAsJsonObject().get("path").getAsString());
          if (Files.size(path) <= 10 * 1024 * 1024)
            content.add(
                Map.of(
                    "type",
                    "image",
                    "mimeType",
                    "image/png",
                    "data",
                    Base64.getEncoder().encodeToString(Files.readAllBytes(path))));
        }
        return Map.of("content", content, "isError", false);
      } catch (Exception failure) {
        return Map.of(
            "content",
            List.of(Map.of("type", "text", "text", failure.toString())),
            "isError",
            true);
      }
    }
    throw new ProtocolFailure(-32601, "Unsupported MCP method: " + method);
  }

  private static final class ProtocolFailure extends RuntimeException {
    final int code;

    ProtocolFailure(int code, String message) {
      super(message);
      this.code = code;
    }
  }
}
