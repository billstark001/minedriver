package io.github.billstark001.minedriver.agent;

import io.github.billstark001.minedriver.api.DriverException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

final class MinecraftNetwork {
  private MinecraftNetwork() {}

  static Object inspect(GameRuntime game) {
    Object listener = Reflect.call(game.minecraft, "getConnection");
    var result = new LinkedHashMap<String, Object>();
    result.put("connected", false);
    if (listener == null) return result;
    Object connection = Reflect.call(listener, "getConnection");
    result.put("connected", Reflect.call(connection, "isConnected"));
    result.put("remoteAddress", String.valueOf(Reflect.call(connection, "getRemoteAddress")));
    result.put("averageReceivedPackets", Reflect.call(connection, "getAverageReceivedPackets"));
    result.put("averageSentPackets", Reflect.call(connection, "getAverageSentPackets"));
    result.put(
        "onlinePlayers",
        ((java.util.Collection<?>) Reflect.call(listener, "getOnlinePlayerIds")).size());
    return result;
  }

  static Object command(GameRuntime game, Map<String, Object> parameters) {
    Object listener = Reflect.call(game.minecraft, "getConnection");
    if (listener == null) throw new DriverException("NOT_CONNECTED", "No client play connection");
    String command = Parameters.string(parameters, "command", null);
    Reflect.call(listener, "sendCommand", command.startsWith("/") ? command.substring(1) : command);
    return Map.of("sent", true, "serverResultVerified", false);
  }

  static Object connect(GameRuntime game, Map<String, Object> parameters, Duration timeout) {
    String address = Parameters.string(parameters, "address", null);
    game.client(
        () -> {
          if (game.level() != null)
            throw new DriverException("WORLD_ALREADY_OPEN", "Disconnect before connecting");
          Object parsed =
              Reflect.call(
                  game.type("net.minecraft.client.multiplayer.resolver.ServerAddress"),
                  "parseString",
                  address);
          if (!Boolean.TRUE.equals(
              Reflect.call(
                  game.type("net.minecraft.client.multiplayer.resolver.ServerAddress"),
                  "isValidAddress",
                  address))) throw Parameters.invalid("Invalid server address");
          Object type =
              MinecraftWorld.enumValue(
                  game.type("net.minecraft.client.multiplayer.ServerData$Type"), "OTHER");
          Object data =
              Reflect.create(
                  game.type("net.minecraft.client.multiplayer.ServerData"),
                  "MineDriver",
                  address,
                  type);
          Reflect.call(
              game.type("net.minecraft.client.gui.screens.ConnectScreen"),
              "startConnecting",
              game.screen(),
              game.minecraft,
              parsed,
              data,
              false,
              null);
          return null;
        },
        timeout);
    MinecraftWorld.await(
        () ->
            game.client(
                () -> game.player() != null && game.level() != null && game.overlay() == null,
                timeout),
        timeout,
        "multiplayer world");
    return game.client(() -> inspect(game), timeout);
  }
}
