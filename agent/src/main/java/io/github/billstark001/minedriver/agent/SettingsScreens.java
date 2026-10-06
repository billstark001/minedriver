package io.github.billstark001.minedriver.agent;

import io.github.billstark001.minedriver.api.DriverException;
import java.util.Map;
import java.util.Optional;

final class SettingsScreens {
  private SettingsScreens() {}

  static Object open(GameRuntime game, Map<String, Object> parameters) {
    Object parent = game.screen();
    if (parameters.containsKey("configClass")) {
      Object supplier =
          Reflect.call(
              game.type("me.shedaniel.autoconfig.AutoConfigClient"),
              "getConfigScreen",
              game.type(Parameters.string(parameters, "configClass", null)),
              parent);
      game.screen(Reflect.call(supplier, "get"));
      return Map.of("provider", "cloth-autoconfig");
    }
    String id = Parameters.string(parameters, "modId", null);
    try {
      Object fabric =
          Reflect.call(game.type("net.fabricmc.loader.api.FabricLoader"), "getInstance");
      if (!Boolean.TRUE.equals(Reflect.call(fabric, "isModLoaded", "modmenu")))
        throw new DriverException(
            "SETTINGS_PROVIDER_MISSING",
            "Mod Menu is absent; supply configClass or a custom scenario factory");
      Class<?> api = game.type("com.terraformersmc.modmenu.api.ModMenuApi");
      for (Object entry :
          (Iterable<?>) Reflect.call(fabric, "getEntrypointContainers", "modmenu", api)) {
        Object metadata = Reflect.call(Reflect.call(entry, "getProvider"), "getMetadata");
        if (!id.equals(Reflect.call(metadata, "getId"))) continue;
        Object factory =
            Reflect.call(Reflect.call(entry, "getEntrypoint"), "getModConfigScreenFactory");
        Object screen = Reflect.call(factory, "create", parent);
        if (screen == null)
          throw new DriverException(
              "SETTINGS_PROVIDER_MISSING", "Settings factory returned null for " + id);
        game.screen(screen);
        return Map.of("provider", "modmenu", "modId", id);
      }
      throw new DriverException("SETTINGS_PROVIDER_MISSING", "No Mod Menu entrypoint for " + id);
    } catch (DriverException error) {
      if (!error.code().equals("UNSUPPORTED_API")) throw error;
      Object mods = Reflect.call(game.type("net.neoforged.fml.ModList"), "get");
      Object container =
          ((Optional<?>) Reflect.call(mods, "getModContainerById", id))
              .orElseThrow(() -> new DriverException("MOD_NOT_FOUND", "Mod not loaded: " + id));
      Object factory =
          ((Optional<?>)
                  Reflect.call(
                      game.type("net.neoforged.neoforge.client.gui.IConfigScreenFactory"),
                      "getForMod",
                      Reflect.call(container, "getModInfo")))
              .orElseThrow(
                  () ->
                      new DriverException(
                          "SETTINGS_PROVIDER_MISSING", "No NeoForge settings factory for " + id));
      game.screen(Reflect.call(factory, "createScreen", container, parent));
      return Map.of("provider", "neoforge", "modId", id);
    }
  }
}
