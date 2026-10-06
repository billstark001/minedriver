package io.github.billstark001.minedriver.agent;

import io.github.billstark001.minedriver.api.DriverException;
import io.github.billstark001.minedriver.protocol.Paths;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.imageio.ImageIO;

final class MinecraftMedia {
  private final GameRuntime game;

  MinecraftMedia(GameRuntime game) {
    this.game = game;
  }

  Object screenshot(Map<String, Object> parameters, Duration timeout) throws Exception {
    game.awaitFrames(Parameters.integer(parameters, "frames", 2, 1, 120), timeout);
    String name = Parameters.string(parameters, "name", "capture-" + System.nanoTime());
    Path file = Paths.child(game.config.output().resolve("screenshots"), name + ".png");
    Files.createDirectories(file.getParent());
    var completed = new CompletableFuture<Object>();
    game.client(
        () -> {
          Object renderer = Reflect.field(game.minecraft, "gameRenderer");
          Object target =
              Reflect.hasMethod(renderer, "mainRenderTarget", 0)
                  ? Reflect.call(renderer, "mainRenderTarget")
                  : Reflect.call(game.minecraft, "getMainRenderTarget");
          Reflect.call(
              game.type("net.minecraft.client.Screenshot"),
              "grab",
              game.config.output().toFile(),
              file.getFileName().toString(),
              target,
              1,
              (Consumer<Object>) completed::complete);
          return null;
        },
        timeout);
    completed.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    var image = ImageIO.read(file.toFile());
    if (image == null)
      throw new DriverException(
          "SCREENSHOT_FAILED", "Screenshot callback did not produce a valid PNG: " + file);
    return Map.of(
        "path",
        file.toString(),
        "width",
        image.getWidth(),
        "height",
        image.getHeight(),
        "frame",
        io.github.billstark001.minedriver.hooks.FrameClock.count());
  }

  Object language(Map<String, Object> parameters, Duration timeout) throws Exception {
    String code = Parameters.string(parameters, "code", null);
    Object future =
        game.client(
            () -> {
              Object manager = Reflect.call(game.minecraft, "getLanguageManager");
              if (Reflect.call(manager, "getLanguage", code) == null)
                throw Parameters.invalid("Language unavailable: " + code);
              Reflect.call(manager, "setSelected", code);
              setField(Reflect.field(game.minecraft, "options"), "languageCode", code);
              return Reflect.call(game.minecraft, "reloadResourcePacks");
            },
            timeout);
    ((Future<?>) future).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    MinecraftWorld.await(
        () -> game.client(() -> game.overlay() == null, timeout), timeout, "language reload");
    game.awaitFrames(2, timeout);
    return game.client(game::inspect, timeout);
  }

  Object window(Map<String, Object> parameters) {
    Object window = Reflect.call(game.minecraft, "getWindow");
    if (parameters.containsKey("width") || parameters.containsKey("height"))
      Reflect.call(
          window,
          "setWindowed",
          Parameters.integer(parameters, "width", 1280, 320, 7680),
          Parameters.integer(parameters, "height", 720, 240, 4320));
    if (parameters.containsKey("guiScale")) {
      Reflect.call(
          Reflect.call(Reflect.field(game.minecraft, "options"), "guiScale"),
          "set",
          Parameters.integer(parameters, "guiScale", 2, 0, 16));
      Reflect.call(game.minecraft, "resizeGui");
    }
    return Map.of(
        "width",
        Reflect.call(window, "getWidth"),
        "height",
        Reflect.call(window, "getHeight"),
        "guiWidth",
        Reflect.call(window, "getGuiScaledWidth"),
        "guiHeight",
        Reflect.call(window, "getGuiScaledHeight"),
        "scale",
        Reflect.call(window, "getGuiScale"));
  }

  Object key(Map<String, Object> parameters, Duration timeout) {
    int key = MinecraftUi.inputConstant(game, parameters, "key", "ESCAPE", "KEY_", 65535);
    Object event =
        Reflect.create(
            game.type("net.minecraft.client.input.KeyEvent"),
            key,
            Parameters.integer(parameters, "scanCode", 0, 0, 65535),
            Parameters.integer(parameters, "modifiers", 0, 0, 65535));
    game.client(
        () -> {
          keyEvent(event, 1);
          return null;
        },
        timeout);
    try {
      game.awaitFrames(Parameters.integer(parameters, "frames", 1, 1, 1200), timeout);
    } finally {
      game.client(
          () -> {
            keyEvent(event, 0);
            return null;
          },
          timeout);
    }
    return Map.of("channel", "input", "key", key);
  }

  Object inputText(Map<String, Object> parameters) {
    Object window = Reflect.call(game.minecraft, "getWindow");
    Reflect.call(
        Reflect.field(game.minecraft, "keyboardHandler"),
        "textInput",
        Reflect.call(window, "handle"),
        Parameters.string(parameters, "value", null));
    return Map.of("channel", "input");
  }

  private void keyEvent(Object event, int action) {
    Reflect.call(
        Reflect.field(game.minecraft, "keyboardHandler"),
        "keyPress",
        Reflect.call(Reflect.call(game.minecraft, "getWindow"), "handle"),
        action,
        event);
  }

  private static void setField(Object target, String name, Object value) {
    try {
      target.getClass().getField(name).set(target, value);
    } catch (ReflectiveOperationException error) {
      throw new DriverException("UNSUPPORTED_API", "Cannot set " + name, error);
    }
  }
}
