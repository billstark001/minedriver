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
    Files.deleteIfExists(file); // A failed callback must never validate an older capture.
    var completed = new CompletableFuture<Object>();
    var capturedUi = new java.util.concurrent.atomic.AtomicReference<Map<String, Object>>();
    game.client(
        () -> {
          capturedUi.set(game.ui.snapshot(8192));
          Object renderer = Reflect.field(game.minecraft, "gameRenderer");
          Object target = Reflect.call(renderer, "mainRenderTarget");
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
    completed.get(OperationDeadline.remaining(timeout).toNanos(), TimeUnit.NANOSECONDS);
    var image = ImageIO.read(file.toFile());
    if (image == null)
      throw new DriverException(
          "SCREENSHOT_FAILED", "Screenshot callback did not produce a valid PNG: " + file);
    Path uiFile = file.resolveSibling(name + ".ui.json");
    io.github.billstark001.minedriver.protocol.Json.write(uiFile, capturedUi.get());
    return Map.of(
        "path",
        file.toString(),
        "width",
        image.getWidth(),
        "height",
        image.getHeight(),
        "uiSnapshot",
        uiFile.toString(),
        "requestedAfterFrame",
        capturedUi.get().get("frame"),
        "completedAtFrame",
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
              Reflect.set(Reflect.field(game.minecraft, "options"), "languageCode", code);
              return Reflect.call(game.minecraft, "reloadResourcePacks");
            },
            timeout);
    ((Future<?>) future).get(OperationDeadline.remaining(timeout).toNanos(), TimeUnit.NANOSECONDS);
    game.observer.await(
        "RESOURCE_RELOAD",
        () -> game.client(() -> game.overlay() == null, timeout),
        timeout,
        "language reload");
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
    String action = Parameters.string(parameters, "action", "TAP");
    if (!java.util.List.of("TAP", "PRESS", "RELEASE").contains(action))
      throw Parameters.invalid("Unknown key action " + action);
    boolean keepPressed = false;
    Throwable failure = null;
    try {
      game.client(
          () -> {
            keyEvent(event, action.equals("RELEASE") ? 0 : 1);
            return null;
          },
          timeout);
      if (action.equals("TAP"))
        game.awaitFrames(Parameters.integer(parameters, "frames", 1, 1, 1200), timeout);
      keepPressed = action.equals("PRESS") || action.equals("RELEASE");
    } catch (RuntimeException | Error error) {
      failure = error;
      throw error;
    } finally {
      if (!keepPressed) {
        io.github.billstark001.minedriver.hooks.InputGate.key(key, false);
        OperationDeadline cleanup = OperationDeadline.cleanup(Duration.ofSeconds(2));
        try {
          game.client(
              () -> {
                keyEvent(event, 0);
                return null;
              },
              Duration.ofSeconds(2));
        } catch (RuntimeException | Error cleanupFailure) {
          if (failure != null) failure.addSuppressed(cleanupFailure);
          else throw cleanupFailure;
        } finally {
          cleanup.close();
        }
      }
    }
    return Map.of("channel", "input", "key", key);
  }

  Object inputText(Map<String, Object> parameters) {
    Object window = Reflect.call(game.minecraft, "getWindow");
    Object keyboard = Reflect.field(game.minecraft, "keyboardHandler");
    String value = Parameters.string(parameters, "value", null);
    if (!game.sdl()) {
      Class<?> eventType = game.type("net.minecraft.client.input.CharacterEvent");
      value
          .codePoints()
          .forEach(
              codepoint ->
                  Reflect.callback(
                      keyboard,
                      "charTyped",
                      new Class<?>[] {long.class, eventType},
                      Reflect.call(window, "handle"),
                      Reflect.create(eventType, codepoint)));
      return Map.of("channel", "input");
    }
    Reflect.call(keyboard, "textInput", Reflect.call(window, "handle"), value);
    return Map.of("channel", "input");
  }

  private void keyEvent(Object event, int action) {
    java.util.concurrent.Callable<Object> dispatch =
        () -> {
          io.github.billstark001.minedriver.hooks.InputGate.key(
              ((Number) Reflect.call(event, "key")).intValue(), action != 0);
          Object keyboard = Reflect.field(game.minecraft, "keyboardHandler");
          Reflect.callback(
              keyboard,
              "keyPress",
              new Class<?>[] {long.class, int.class, event.getClass()},
              Reflect.call(Reflect.call(game.minecraft, "getWindow"), "handle"),
              action,
              event);
          return null;
        };
    if (action == 0) InputIsolation.dispatch(dispatch);
    else game.input.automation(dispatch);
  }
}
