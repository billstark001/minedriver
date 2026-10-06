package io.github.billstark001.minedriver.cli;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.billstark001.minedriver.protocol.Connection;
import io.github.billstark001.minedriver.protocol.Json;
import io.github.billstark001.minedriver.protocol.RpcClient;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

/** Local connection-file client. Credentials are never accepted in command-line arguments. */
public final class Main {
  private Main() {}

  public static void main(String[] arguments) {
    try {
      if (arguments.length < 2)
        throw new IllegalArgumentException(
            "Usage: minedriver <connection.json|latest.json> <inspect|commands|call|watch|mcp> [method] [JSON|@file]");
      Path file = Path.of(arguments[0]);
      JsonObject metadata = Json.read(file).getAsJsonObject();
      if (metadata.has("connectionFile"))
        file = Path.of(metadata.get("connectionFile").getAsString());
      var rpc = new RpcClient(Connection.read(file));
      String command = arguments[1];
      Duration timeout = Duration.ofMinutes(5);
      switch (command) {
        case "inspect" ->
            System.out.println(Json.PRETTY.toJson(rpc.call("session.inspect", Map.of(), timeout)));
        case "commands" ->
            System.out.println(Json.PRETTY.toJson(rpc.call("session.commands", Map.of(), timeout)));
        case "call" -> {
          if (arguments.length < 3) throw new IllegalArgumentException("call needs a method");
          JsonElement parameters =
              arguments.length < 4
                  ? new JsonObject()
                  : arguments[3].startsWith("@")
                      ? Json.read(Path.of(arguments[3].substring(1)))
                      : JsonParser.parseString(arguments[3]);
          System.out.println(Json.PRETTY.toJson(rpc.call(arguments[2], parameters, timeout)));
        }
        case "watch" -> {
          while (!Thread.currentThread().isInterrupted()) {
            System.out.println(Json.GSON.toJson(rpc.call("session.inspect", Map.of(), timeout)));
            Thread.sleep(1000);
          }
        }
        case "mcp" -> new McpServer(rpc).serve(System.in, System.out);
        default -> throw new IllegalArgumentException("Unknown command: " + command);
      }
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      System.exit(130);
    } catch (Exception failure) {
      System.err.println("MineDriver: " + failure.getMessage());
      System.exit(1);
    }
  }
}
