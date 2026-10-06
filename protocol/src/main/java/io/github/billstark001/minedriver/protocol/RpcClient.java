package io.github.billstark001.minedriver.protocol;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

public final class RpcClient {
  private final Connection connection;
  private final HttpClient http;
  private final Duration timeout;
  private final AtomicLong sequence = new AtomicLong();

  public RpcClient(Connection connection) {
    this(connection, Duration.ofMinutes(5));
  }

  public RpcClient(Connection connection, Duration timeout) {
    this.connection = connection;
    this.timeout = timeout;
    http =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
  }

  public JsonElement call(String method, Object parameters)
      throws IOException, InterruptedException {
    return call(method, parameters, timeout);
  }

  public JsonElement call(String method, Object parameters, Duration limit)
      throws IOException, InterruptedException {
    var body = new JsonObject();
    body.addProperty("jsonrpc", "2.0");
    body.addProperty("id", sequence.incrementAndGet());
    body.addProperty("method", method);
    body.add("params", Json.GSON.toJsonTree(parameters));
    var request =
        HttpRequest.newBuilder(URI.create(connection.endpoint() + "/rpc"))
            .timeout(limit)
            .header("Authorization", "Bearer " + connection.token())
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(Json.GSON.toJson(body)))
            .build();
    var response = http.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != 200)
      throw new IOException("MineDriver HTTP " + response.statusCode());
    var parsed = com.google.gson.JsonParser.parseString(response.body()).getAsJsonObject();
    if (!parsed.has("jsonrpc")
        || !"2.0".equals(parsed.get("jsonrpc").getAsString())
        || !body.get("id").equals(parsed.get("id")))
      throw new IOException("Invalid MineDriver response envelope");
    if (parsed.has("error")) {
      var error = parsed.getAsJsonObject("error");
      throw new RpcException(error.get("message").getAsString(), error.get("data"));
    }
    return parsed.get("result");
  }

  public static final class RpcException extends IOException {
    private final JsonElement data;

    public RpcException(String message, JsonElement data) {
      super(message);
      this.data = data;
    }

    public JsonElement data() {
      return data;
    }
  }
}
