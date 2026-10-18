package io.github.billstark001.minedriver.agent;

import io.github.billstark001.minedriver.api.DriverException;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;

/** Explicit Mojang-named development-runtime contracts; no version-range guessing. */
class MinecraftAdapter {
  static final List<String> VERSIONS = List.of("1.21.1", "1.21.11", "26.1.2", "26.2", "26.3");

  static MinecraftAdapter forVersion(String version) {
    return switch (version) {
      case "1.21.1" -> new Legacy121();
      case "1.21.11" ->
          new BeforeGui(false) {
            @Override
            void resizeGui(GameRuntime game) {
              Reflect.call(game.minecraft, "resizeDisplay");
            }

            @Override
            Object characterEvent(GameRuntime game, int codepoint) {
              return Reflect.create(
                  game.type("net.minecraft.client.input.CharacterEvent"), codepoint, 0);
            }
          };
      case "26.1.2" -> new BeforeGui(true);
      case "26.2" -> new MinecraftAdapter();
      case "26.3" ->
          new MinecraftAdapter() {
            @Override
            boolean sdl() {
              return true;
            }
          };
      default ->
          throw new DriverException(
              "UNSUPPORTED_API", "No native window/input adapter for Minecraft " + version);
    };
  }

  boolean sdl() {
    return false;
  }

  boolean preedit() {
    return true;
  }

  String autoConfigClass() {
    return "me.shedaniel.autoconfig.AutoConfigClient";
  }

  Object screen(GameRuntime game) {
    return Reflect.call(game.gui(), "screen");
  }

  Object overlay(GameRuntime game) {
    return Reflect.call(game.gui(), "overlay");
  }

  void screen(GameRuntime game, Object screen) {
    Reflect.call(game.gui(), "setScreen", screen);
  }

  long handle(Object window) {
    return ((Number) Reflect.call(window, "handle")).longValue();
  }

  boolean keyDown(GameRuntime game, Object window, int key) {
    Class<?> constants = game.type("com.mojang.blaze3d.platform.InputConstants");
    return Boolean.TRUE.equals(
        sdl()
            ? Reflect.call(constants, "isKeyDown", key)
            : Reflect.call(constants, "isKeyDown", window, key));
  }

  void key(GameRuntime game, int key, int scanCode, int modifiers, int action) {
    Class<?> event = game.type("net.minecraft.client.input.KeyEvent");
    Reflect.callback(
        Reflect.field(game.minecraft, "keyboardHandler"),
        "keyPress",
        new Class<?>[] {long.class, int.class, event},
        handle(Reflect.call(game.minecraft, "getWindow")),
        action,
        Reflect.create(event, key, scanCode, modifiers));
  }

  void character(GameRuntime game, int codepoint) {
    Class<?> event = game.type("net.minecraft.client.input.CharacterEvent");
    Reflect.callback(
        Reflect.field(game.minecraft, "keyboardHandler"),
        "charTyped",
        new Class<?>[] {long.class, event},
        handle(Reflect.call(game.minecraft, "getWindow")),
        characterEvent(game, codepoint));
  }

  Object characterEvent(GameRuntime game, int codepoint) {
    return Reflect.create(game.type("net.minecraft.client.input.CharacterEvent"), codepoint);
  }

  boolean canActivate(Object widget) {
    return Reflect.hasMethod(widget, "onPress", 1);
  }

  void activate(GameRuntime game, Object widget) {
    Reflect.call(
        widget,
        "onPress",
        Reflect.create(
            game.type("net.minecraft.client.input.KeyEvent"),
            Reflect.field(game.type("com.mojang.blaze3d.platform.InputConstants"), "KEY_RETURN"),
            0,
            0));
  }

  Object mouseEvent(GameRuntime game, double x, double y, int button, int modifiers) {
    Object info =
        Reflect.create(game.type("net.minecraft.client.input.MouseButtonInfo"), button, modifiers);
    return Reflect.create(game.type("net.minecraft.client.input.MouseButtonEvent"), x, y, info);
  }

  boolean click(GameRuntime game, Object screen, double x, double y, int button, int modifiers) {
    return Boolean.TRUE.equals(
        Reflect.call(screen, "mouseClicked", mouseEvent(game, x, y, button, modifiers), false));
  }

  void release(GameRuntime game, Object screen, double x, double y, int button, int modifiers) {
    Reflect.call(screen, "mouseReleased", mouseEvent(game, x, y, button, modifiers));
  }

  void clearInput(GameRuntime game, Object mouse) {
    if (preedit())
      Reflect.set(Reflect.field(game.minecraft, "keyboardHandler"), "lastPreeditEvent", null);
    Reflect.set(mouse, "activeButton", null);
    Reflect.set(mouse, "lastClick", null);
    clearScroll(Reflect.field(mouse, "scrollWheelHandler"));
  }

  static void clearScroll(Object scroll) {
    for (String field : List.of("accumulatedScrollX", "accumulatedScrollY"))
      Reflect.set(scroll, field, 0.0);
  }

  Object renderTarget(GameRuntime game) {
    return Reflect.call(Reflect.field(game.minecraft, "gameRenderer"), "mainRenderTarget");
  }

  void screenshot(GameRuntime game, File directory, String name, Consumer<Object> completed) {
    Reflect.call(
        game.type("net.minecraft.client.Screenshot"),
        "grab",
        directory,
        name,
        renderTarget(game),
        1,
        completed);
  }

  void openWorld(GameRuntime game) {
    Reflect.call(
        game.type("net.minecraft.client.gui.screens.worldselection.CreateWorldScreen"),
        "openFresh",
        game.minecraft,
        (Runnable) () -> {});
  }

  void disconnect(GameRuntime game) {
    Reflect.call(
        game.minecraft,
        "disconnectFromWorld",
        Reflect.call(
            game.type("net.minecraft.network.chat.Component"), "literal", "MineDriver disconnect"));
  }

  Object dimension(Object key) {
    return Reflect.call(key, "identifier");
  }

  void resizeGui(GameRuntime game) {
    Reflect.call(game.minecraft, "resizeGui");
  }

  /** 1.21.11/26.1.2 keep screen/overlay and the main target on Minecraft itself. */
  private static class BeforeGui extends MinecraftAdapter {
    private final boolean ime;

    BeforeGui(boolean ime) {
      this.ime = ime;
    }

    @Override
    boolean preedit() {
      return ime;
    }

    @Override
    Object screen(GameRuntime game) {
      return Reflect.field(game.minecraft, "screen");
    }

    @Override
    Object overlay(GameRuntime game) {
      return Reflect.call(game.minecraft, "getOverlay");
    }

    @Override
    void screen(GameRuntime game, Object screen) {
      Reflect.call(game.minecraft, "setScreen", screen);
    }

    @Override
    Object renderTarget(GameRuntime game) {
      return Reflect.call(game.minecraft, "getMainRenderTarget");
    }
  }

  /** 1.21.1 predates input-event records, IME, scroll state objects and render-state extraction. */
  private static final class Legacy121 extends BeforeGui {
    Legacy121() {
      super(false);
    }

    @Override
    String autoConfigClass() {
      return "me.shedaniel.autoconfig.AutoConfig";
    }

    @Override
    long handle(Object window) {
      return ((Number) Reflect.call(window, "getWindow")).longValue();
    }

    @Override
    boolean keyDown(GameRuntime game, Object window, int key) {
      return Boolean.TRUE.equals(
          Reflect.call(
              game.type("com.mojang.blaze3d.platform.InputConstants"),
              "isKeyDown",
              handle(window),
              key));
    }

    @Override
    void key(GameRuntime game, int key, int scanCode, int modifiers, int action) {
      Reflect.callback(
          Reflect.field(game.minecraft, "keyboardHandler"),
          "keyPress",
          new Class<?>[] {long.class, int.class, int.class, int.class, int.class},
          handle(Reflect.call(game.minecraft, "getWindow")),
          key,
          scanCode,
          action,
          modifiers);
    }

    @Override
    void character(GameRuntime game, int codepoint) {
      Reflect.callback(
          Reflect.field(game.minecraft, "keyboardHandler"),
          "charTyped",
          new Class<?>[] {long.class, int.class, int.class},
          handle(Reflect.call(game.minecraft, "getWindow")),
          codepoint,
          0);
    }

    @Override
    boolean canActivate(Object widget) {
      return Reflect.hasMethod(widget, "onPress", 0);
    }

    @Override
    void activate(GameRuntime game, Object widget) {
      Reflect.call(widget, "onPress");
    }

    @Override
    boolean click(GameRuntime game, Object screen, double x, double y, int button, int modifiers) {
      // Match the modern mouseClicked(event, false) contract: input.click is one single click.
      if (UiGeometry.is(
          screen, "net.minecraft.client.gui.screens.inventory.AbstractContainerScreen"))
        Reflect.set(screen, "lastClickTime", 0L);
      return Boolean.TRUE.equals(Reflect.call(screen, "mouseClicked", x, y, button));
    }

    @Override
    void release(GameRuntime game, Object screen, double x, double y, int button, int modifiers) {
      Reflect.call(screen, "mouseReleased", x, y, button);
    }

    @Override
    void clearInput(GameRuntime game, Object mouse) {
      Reflect.set(mouse, "activeButton", -1);
      clearScroll(mouse);
    }

    @Override
    void screenshot(GameRuntime game, File directory, String name, Consumer<Object> completed) {
      Reflect.call(
          game.type("net.minecraft.client.Screenshot"),
          "grab",
          directory,
          name,
          renderTarget(game),
          completed);
    }

    @Override
    void openWorld(GameRuntime game) {
      Reflect.call(
          game.type("net.minecraft.client.gui.screens.worldselection.CreateWorldScreen"),
          "openFresh",
          game.minecraft,
          game.screen());
    }

    @Override
    void disconnect(GameRuntime game) {
      if (game.level() != null) Reflect.call(game.level(), "disconnect");
      Reflect.call(
          game.minecraft,
          "disconnect",
          Reflect.create(
              game.type("net.minecraft.client.gui.screens.GenericMessageScreen"),
              Reflect.call(
                  game.type("net.minecraft.network.chat.Component"),
                  "translatable",
                  "menu.savingLevel",
                  new Object[0])));
      screen(game, Reflect.create(game.type("net.minecraft.client.gui.screens.TitleScreen")));
    }

    @Override
    Object dimension(Object key) {
      return Reflect.call(key, "location");
    }

    @Override
    void resizeGui(GameRuntime game) {
      Reflect.call(game.minecraft, "resizeDisplay");
    }
  }
}
