package io.github.billstark001.minedriver.cli;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import io.github.billstark001.minedriver.protocol.CommandSchemas;
import io.github.billstark001.minedriver.protocol.Connection;
import io.github.billstark001.minedriver.protocol.Json;
import io.github.billstark001.minedriver.protocol.RpcClient;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class McpServerTest {
  @Test
  void handshakeSchemasAndToolErrorsUseCleanStdio() throws Exception {
    String token = "test-token-".repeat(4);
    HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    http.createContext(
        "/rpc",
        exchange -> {
          assertEquals("Bearer " + token, exchange.getRequestHeaders().getFirst("Authorization"));
          var request =
              JsonParser.parseString(
                      new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8))
                  .getAsJsonObject();
          var response = new com.google.gson.JsonObject();
          response.addProperty("jsonrpc", "2.0");
          response.add("id", request.get("id"));
          if (request.get("method").getAsString().equals("session.commands")) {
            response.add(
                "result",
                Json.GSON.toJsonTree(
                    List.of(
                        Map.of(
                            "name",
                            "input.key",
                            "description",
                            "Key input",
                            "channel",
                            "input",
                            "readOnly",
                            false,
                            "inputSchema",
                            CommandSchemas.of("input.key")))));
          } else
            response.add(
                "error",
                Json.GSON.toJsonTree(
                    Map.of(
                        "code",
                        -32000,
                        "message",
                        "fixture error",
                        "data",
                        Map.of("code", "ASSERTION_FAILED"))));
          byte[] bytes = Json.GSON.toJson(response).getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, bytes.length);
          try (var output = exchange.getResponseBody()) {
            output.write(bytes);
          }
        });
    http.start();
    try {
      var rpc =
          new RpcClient(
              new Connection(
                  1, "mcp-test", "http://127.0.0.1:" + http.getAddress().getPort(), token, 1));
      String input =
          """
        {"jsonrpc":"2.0","method":"tools/list","id":0}
        {"jsonrpc":"2.0","method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"test","version":"1"}},"id":1}
        {"jsonrpc":"2.0","method":"notifications/initialized"}
        {"jsonrpc":"2.0","method":"tools/list","id":2}
        {"jsonrpc":"2.0","method":"tools/call","params":{"name":"input.key","arguments":{"key":"ESCAPE"}},"id":3}
        {"jsonrpc":"2.0","method":"missing","id":4}
        """;
      var output = new ByteArrayOutputStream();
      new McpServer(rpc)
          .serve(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)), output);
      var replies =
          output
              .toString(StandardCharsets.UTF_8)
              .lines()
              .map(line -> JsonParser.parseString(line).getAsJsonObject())
              .toList();
      assertEquals(5, replies.size());
      assertEquals(-32600, replies.get(0).getAsJsonObject("error").get("code").getAsInt());
      assertEquals(
          "2025-11-25",
          replies.get(1).getAsJsonObject("result").get("protocolVersion").getAsString());
      var tool =
          replies.get(2).getAsJsonObject("result").getAsJsonArray("tools").get(0).getAsJsonObject();
      assertTrue(tool.getAsJsonObject("inputSchema").getAsJsonObject("properties").has("key"));
      assertFalse(tool.getAsJsonObject("annotations").get("readOnlyHint").getAsBoolean());
      assertTrue(replies.get(3).getAsJsonObject("result").get("isError").getAsBoolean());
      assertEquals(-32601, replies.get(4).getAsJsonObject("error").get("code").getAsInt());
    } finally {
      http.stop(0);
    }
  }
}
