package io.github.billstark001.minedriver.agent;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.billstark001.minedriver.api.DriverException;
import io.github.billstark001.minedriver.protocol.Connection;
import io.github.billstark001.minedriver.protocol.Json;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class RpcServer implements AutoCloseable {
  private static final int LIMIT = 1_048_576;
  private final HttpServer server;
  private final ExecutorService workers;
  private final String token;
  private final CommandRegistry registry;
  private final Connection connection;

  RpcServer(CommandRegistry registry, String runId) throws IOException {
    this.registry = registry;
    byte[] random = new byte[32];
    new SecureRandom().nextBytes(random);
    token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 16);
    workers =
        Executors.newFixedThreadPool(
            4,
            action -> {
              Thread thread = new Thread(action, "MineDriver RPC");
              thread.setDaemon(true);
              return thread;
            });
    server.setExecutor(workers);
    server.createContext("/rpc", this::handle);
    server.start();
    connection =
        new Connection(
            1,
            runId,
            "http://127.0.0.1:" + server.getAddress().getPort(),
            token,
            ProcessHandle.current().pid());
  }

  Connection connection() {
    return connection;
  }

  private void handle(HttpExchange exchange) throws IOException {
    try (exchange) {
      if (!"/rpc".equals(exchange.getRequestURI().getPath())
          || !"POST".equals(exchange.getRequestMethod())) {
        send(exchange, 405, "Method not allowed");
        return;
      }
      String authorization = exchange.getRequestHeaders().getFirst("Authorization");
      if (authorization == null
          || !MessageDigest.isEqual(
              ("Bearer " + token).getBytes(StandardCharsets.UTF_8),
              authorization.getBytes(StandardCharsets.UTF_8))) {
        send(exchange, 401, "Unauthorized");
        return;
      }
      byte[] body = exchange.getRequestBody().readNBytes(LIMIT + 1);
      if (body.length > LIMIT) {
        send(exchange, 413, "Request too large");
        return;
      }
      JsonObject request;
      try {
        request =
            JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
      } catch (RuntimeException invalid) {
        send(exchange, 200, Json.GSON.toJson(error(null, -32700, "Invalid JSON", "PARSE_ERROR")));
        return;
      }
      if (!string(request, "jsonrpc")
          || !"2.0".equals(request.get("jsonrpc").getAsString())
          || !string(request, "method")
          || (request.has("id")
              && !(request.get("id").isJsonNull() || request.get("id").isJsonPrimitive()))) {
        send(
            exchange,
            200,
            Json.GSON.toJson(
                error(request, -32600, "Invalid JSON-RPC request", "INVALID_REQUEST")));
        return;
      }
      JsonObject response;
      try {
        if (request.has("params") && !request.get("params").isJsonObject())
          throw Parameters.invalid("params must be an object");
        Map<String, Object> parameters =
            request.has("params")
                ? Json.GSON.fromJson(
                    request.get("params"), new TypeToken<Map<String, Object>>() {}.getType())
                : Map.of();
        Object result = registry.invoke(request.get("method").getAsString(), parameters);
        response = envelope(request);
        response.add("result", Json.GSON.toJsonTree(result));
      } catch (Throwable failure) {
        String code = failure instanceof DriverException driver ? driver.code() : "INTERNAL_ERROR";
        int rpcCode =
            code.equals("UNKNOWN_COMMAND")
                ? -32601
                : code.equals("INVALID_ARGUMENT") ? -32602 : -32000;
        response =
            error(
                request,
                rpcCode,
                failure.getMessage() == null ? failure.toString() : failure.getMessage(),
                code);
      }
      if (!request.has("id")) {
        exchange.sendResponseHeaders(204, -1);
        return;
      }
      send(exchange, 200, Json.GSON.toJson(response));
    }
  }

  private static boolean string(JsonObject object, String key) {
    return object.has(key)
        && object.get(key).isJsonPrimitive()
        && object.getAsJsonPrimitive(key).isString();
  }

  private static JsonObject envelope(JsonObject request) {
    JsonObject result = new JsonObject();
    result.addProperty("jsonrpc", "2.0");
    result.add(
        "id",
        request == null || !request.has("id")
            ? com.google.gson.JsonNull.INSTANCE
            : request.get("id"));
    return result;
  }

  private static JsonObject error(JsonObject request, int code, String message, String category) {
    JsonObject response = envelope(request);
    JsonObject error = new JsonObject();
    error.addProperty("code", code);
    error.addProperty("message", message);
    error.add("data", Json.GSON.toJsonTree(Map.of("code", category)));
    response.add("error", error);
    return response;
  }

  private static void send(HttpExchange exchange, int status, String content) throws IOException {
    byte[] body = content.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
    exchange.getResponseHeaders().set("Cache-Control", "no-store");
    exchange.sendResponseHeaders(status, body.length);
    exchange.getResponseBody().write(body);
  }

  @Override
  public void close() {
    server.stop(0);
    workers.shutdownNow();
  }
}
