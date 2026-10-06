package io.github.billstark001.minedriver.agent;

import io.github.billstark001.minedriver.api.DriverException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

final class MinecraftWorld {
  private final GameRuntime game;

  MinecraftWorld(GameRuntime game) {
    this.game = game;
  }

  Object create(Map<String, Object> parameters, Duration timeout) {
    game.client(
        () -> {
          if (game.level() != null)
            throw new DriverException("WORLD_ALREADY_OPEN", "Disconnect before creating a fixture");
          Reflect.call(
              game.type("net.minecraft.client.gui.screens.worldselection.CreateWorldScreen"),
              "openFresh",
              game.minecraft,
              (Runnable) () -> {});
          return null;
        },
        timeout);
    await(
        () ->
            game.client(
                () ->
                    game.screen() != null
                        && game.screen().getClass().getName().endsWith(".CreateWorldScreen"),
                timeout),
        timeout,
        "world creation screen");
    game.client(
        () -> {
          Object state = Reflect.call(game.screen(), "getUiState");
          Reflect.call(
              state,
              "setName",
              Parameters.string(parameters, "name", "MineDriver-" + game.config.runId()));
          Reflect.call(state, "setSeed", Parameters.string(parameters, "seed", "1"));
          Reflect.call(state, "setAllowCommands", true);
          Object mode =
              enumValue(
                  game.type(
                      "net.minecraft.client.gui.screens.worldselection.WorldCreationUiState$SelectedGameMode"),
                  Parameters.string(parameters, "mode", "CREATIVE"));
          Reflect.call(state, "setGameMode", mode);
          if (Parameters.bool(parameters, "flat", true)) {
            Object flat =
                ((List<?>) Reflect.call(state, "getNormalPresetList"))
                    .stream()
                        .filter(
                            entry ->
                                "generator.minecraft.flat"
                                    .equals(MinecraftUi.key(Reflect.call(entry, "describePreset"))))
                        .findFirst()
                        .orElseThrow(
                            () -> new DriverException("UNSUPPORTED_API", "Flat preset missing"));
            Reflect.call(state, "setWorldType", flat);
          }
          game.ui.activate(Map.of("selector", Map.of("key", "selectWorld.create")));
          return null;
        },
        timeout);
    await(
        () ->
            game.client(
                () -> game.level() != null && game.player() != null && game.overlay() == null,
                timeout),
        timeout,
        "world ready");
    return game.client(game::inspect, timeout);
  }

  Object command(String command, Duration timeout) {
    // Brigadier.execute propagates syntax and result errors instead of silently logging them.
    return game.server(
        () -> {
          Object server = game.server();
          Object commands = Reflect.call(server, "getCommands");
          Object dispatcher = Reflect.call(commands, "getDispatcher");
          String normalized = command.startsWith("/") ? command.substring(1) : command;
          return Reflect.call(
              dispatcher, "execute", normalized, Reflect.call(server, "createCommandSourceStack"));
        },
        timeout);
  }

  Object fixture(Map<String, Object> parameters, Duration timeout) {
    String player =
        game.client(
            () -> {
              if (game.player() == null)
                throw new DriverException("NO_WORLD", "A player is required");
              return Reflect.call(game.player(), "getUUID").toString();
            },
            timeout);
    if (parameters.containsKey("position")) {
      Map<String, Object> p = Parameters.object(parameters, "position");
      command(
          "tp "
              + player
              + " "
              + Parameters.decimal(p, "x", 0)
              + " "
              + Parameters.decimal(p, "y", 5)
              + " "
              + Parameters.decimal(p, "z", 0)
              + " "
              + Parameters.decimal(p, "yaw", 0)
              + " "
              + Parameters.decimal(p, "pitch", 0),
          timeout);
    }
    for (Object item : Parameters.list(parameters, "blocks")) {
      Map<String, Object> block = map(item);
      command(
          "setblock "
              + Parameters.integer(block, "x", 0, -30000000, 30000000)
              + " "
              + Parameters.integer(block, "y", 0, -2048, 2048)
              + " "
              + Parameters.integer(block, "z", 0, -30000000, 30000000)
              + " "
              + identifier(Parameters.string(block, "state", null)),
          timeout);
    }
    for (Object item : Parameters.list(parameters, "inventory")) {
      Map<String, Object> slot = map(item);
      command(
          "item replace entity "
              + player
              + " hotbar."
              + Parameters.integer(slot, "slot", 0, 0, 8)
              + " with "
              + identifier(Parameters.string(slot, "item", null))
              + " "
              + Parameters.integer(slot, "count", 1, 1, 99),
          timeout);
    }
    for (Object command : Parameters.list(parameters, "commands"))
      command(command.toString(), timeout);
    return game.client(game::inspect, timeout);
  }

  Object disconnect(Duration timeout) {
    Object server = game.client(game::server, timeout);
    game.client(
        () -> {
          Reflect.call(game.minecraft, "disconnectWithSavingScreen");
          return null;
        },
        timeout);
    await(
        () ->
            game.client(() -> game.level() == null && game.server() == null, timeout)
                && (server == null || Boolean.TRUE.equals(Reflect.call(server, "isStopped"))),
        timeout,
        "world disconnect");
    game.awaitFrames(2, timeout);
    return true;
  }

  static Object enumValue(Class<?> type, String name) {
    for (Object constant : type.getEnumConstants())
      if (((Enum<?>) constant).name().equals(name)) return constant;
    throw Parameters.invalid("Unknown " + type.getSimpleName() + " value " + name);
  }

  Object snapshot(Map<String, Object> parameters, Duration timeout) {
    String uuid =
        game.client(
            () -> {
              if (game.player() == null)
                throw new DriverException("NO_WORLD", "A player is required");
              return Reflect.call(game.player(), "getUUID").toString();
            },
            timeout);
    return game.server(
        () -> {
          Object player =
              Reflect.call(
                  Reflect.call(game.server(), "getPlayerList"),
                  "getPlayer",
                  java.util.UUID.fromString(uuid));
          if (player == null)
            throw new DriverException("NO_SERVER_PLAYER", "Server has not registered the player");
          Object level = Reflect.call(player, "level");
          var blocks = new java.util.ArrayList<Object>();
          for (Object entry : Parameters.list(parameters, "blocks")) {
            Map<String, Object> p = map(entry);
            Object position =
                Reflect.create(
                    game.type("net.minecraft.core.BlockPos"),
                    Parameters.integer(p, "x", 0, -30000000, 30000000),
                    Parameters.integer(p, "y", 0, -2048, 2048),
                    Parameters.integer(p, "z", 0, -30000000, 30000000));
            Object state = Reflect.call(level, "getBlockState", position);
            Object id =
                Reflect.call(
                    Reflect.field(
                        game.type("net.minecraft.core.registries.BuiltInRegistries"), "BLOCK"),
                    "getKey",
                    Reflect.call(state, "getBlock"));
            blocks.add(Map.of("position", p, "id", id.toString(), "state", state.toString()));
          }
          var inventory = new java.util.ArrayList<Object>();
          for (Object value : Parameters.list(parameters, "slots")) {
            int slot = Parameters.integer(Map.of("slot", value), "slot", 0, 0, 35);
            Object stack = Reflect.call(Reflect.call(player, "getInventory"), "getItem", slot);
            Object id =
                Reflect.call(
                    Reflect.field(
                        game.type("net.minecraft.core.registries.BuiltInRegistries"), "ITEM"),
                    "getKey",
                    Reflect.call(stack, "getItem"));
            inventory.add(
                Map.of(
                    "slot", slot, "id", id.toString(), "count", Reflect.call(stack, "getCount")));
          }
          return Map.of(
              "authoritative",
              true,
              "blocks",
              blocks,
              "inventory",
              inventory,
              "tick",
              Reflect.call(game.server(), "getTickCount"));
        },
        timeout);
  }

  static void await(
      java.util.function.BooleanSupplier condition, Duration timeout, String description) {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (!condition.getAsBoolean()) {
      if (System.nanoTime() >= deadline)
        throw new DriverException("CONDITION_TIMEOUT", "Timed out waiting for " + description);
      GameRuntime.pause();
    }
  }

  private static Map<String, Object> map(Object object) {
    return Parameters.object(Map.of("item", object), "item");
  }

  private static String identifier(String value) {
    if (!value.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+(\\[[a-z0-9_=,.-]+\\])?"))
      throw Parameters.invalid("Invalid block/item identifier");
    return value;
  }
}
