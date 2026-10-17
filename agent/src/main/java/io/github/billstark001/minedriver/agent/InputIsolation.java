package io.github.billstark001.minedriver.agent;

import io.github.billstark001.minedriver.api.DriverException;
import io.github.billstark001.minedriver.hooks.InputGate;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;

/** Leases and native state transitions are owned by the client thread. */
final class InputIsolation implements AutoCloseable {
  private final GameRuntime game;
  private final boolean sdl;
  private final Map<String, Long> leases = new LinkedHashMap<>();
  private volatile Map<String, Object> status = Map.of("isolated", false);
  private String expired;
  private Boolean pauseOnLostFocus;
  private volatile Map<String, Long> leaseView = Map.of();

  InputIsolation(GameRuntime game) {
    this.game = game;
    sdl =
        Reflect.call(
                Reflect.call(game.type("net.minecraft.SharedConstants"), "getCurrentVersion"),
                "name")
            .equals("26.3");
  }

  List<String> missingHooks() {
    var required =
        new ArrayList<>(
            List.of(
                "key",
                "character",
                "preedit",
                "button",
                "scroll",
                "move",
                "drop",
                "poll",
                "focus",
                "queue",
                "grab"));
    if (sdl) required.addAll(List.of("text", "cursor", "release"));
    required.removeAll(InputGate.installed());
    return List.copyOf(required);
  }

  Map<String, Object> status() {
    var result = new LinkedHashMap<String, Object>(status);
    result.put("isolated", InputGate.isolated());
    result.put(
        "leases",
        leaseView.entrySet().stream()
            .map(
                entry ->
                    Map.of(
                        "leaseId",
                        entry.getKey(),
                        "remainingMillis",
                        entry.getValue() == Long.MAX_VALUE
                            ? -1L
                            : Math.max(0L, (entry.getValue() - System.nanoTime()) / 1_000_000)))
            .toList());
    result.put("installedHooks", InputGate.installed());
    result.put("missingHooks", missingHooks());
    result.put("hookFailure", InputGate.failure());
    result.put("droppedEvents", InputGate.dropped());
    return result;
  }

  void configure() {
    try {
      if (game.config.backgroundExecution()) {
        Object options = Reflect.field(game.minecraft, "options");
        pauseOnLostFocus = (Boolean) Reflect.field(options, "pauseOnLostFocus");
        Reflect.set(options, "pauseOnLostFocus", false);
      }
      if (game.config.inputIsolation()) acquireInternal("session", Long.MAX_VALUE);
      publish();
    } catch (RuntimeException failure) {
      if (pauseOnLostFocus != null)
        Reflect.set(Reflect.field(game.minecraft, "options"), "pauseOnLostFocus", pauseOnLostFocus);
      throw failure;
    }
  }

  Object acquire(Map<String, Object> parameters) {
    String id = UUID.randomUUID().toString();
    acquireInternal(
        id,
        System.nanoTime()
            + java.time.Duration.ofMillis(
                    Parameters.integer(parameters, "ttlMillis", 60_000, 100, 3_600_000))
                .toNanos());
    return Map.of("leaseId", id, "status", status());
  }

  private void acquireInternal(String id, long deadline) {
    if (!missingHooks().isEmpty() || InputGate.failure() != null)
      throw new DriverException(
          "INPUT_ISOLATION_UNAVAILABLE", "Required input hooks unavailable: " + status());
    if (leases.isEmpty()) {
      physicalKeys();
      clearNativeState();
      InputGate.isolation(true);
    }
    expired = null;
    leases.put(id, deadline);
    publish();
  }

  Object renew(Map<String, Object> parameters) {
    String id = Parameters.string(parameters, "leaseId", null);
    expire();
    if (!leases.containsKey(id) || id.equals("session"))
      throw new DriverException("INPUT_LEASE_NOT_FOUND", "Unknown interactive input lease");
    leases.put(
        id,
        System.nanoTime()
            + java.time.Duration.ofMillis(
                    Parameters.integer(parameters, "ttlMillis", 60_000, 100, 3_600_000))
                .toNanos());
    publish();
    return status();
  }

  Object release(Map<String, Object> parameters) {
    String id = Parameters.string(parameters, "leaseId", null);
    if (id.equals("session") || leases.remove(id) == null)
      throw new DriverException(
          "INPUT_LEASE_NOT_FOUND", "Only the owner of an interactive lease may release it");
    try {
      if (leases.isEmpty()) restoreInput();
    } finally {
      publish();
    }
    return status();
  }

  void expire() {
    long now = System.nanoTime();
    boolean removed =
        leases
            .entrySet()
            .removeIf(entry -> entry.getValue() != Long.MAX_VALUE && entry.getValue() <= now);
    if (removed) {
      expired = "INPUT_LEASE_EXPIRED";
      try {
        if (leases.isEmpty()) restoreInput();
      } finally {
        publish();
      }
    }
  }

  private void publish() {
    leaseView = Map.copyOf(leases);
    var value = new LinkedHashMap<String, Object>();
    value.put("isolated", InputGate.isolated());
    value.put("backgroundExecution", game.config.backgroundExecution());
    value.put("expiredLease", expired);
    status = java.util.Collections.unmodifiableMap(value);
  }

  private void physicalKeys() {
    Class<?> constants = game.type("com.mojang.blaze3d.platform.InputConstants");
    Object window = Reflect.call(game.minecraft, "getWindow");
    InputGate.rawQuery(true);
    try {
      for (var field : constants.getFields()) {
        if (!field.getName().startsWith("KEY_")
            || field.getType() != int.class
            || !Modifier.isStatic(field.getModifiers())) continue;
        int key = ((Number) Reflect.field(constants, field.getName())).intValue();
        if (key < 0 || key >= 512) continue;
        boolean down =
            Boolean.TRUE.equals(
                sdl
                    ? Reflect.call(constants, "isKeyDown", key)
                    : Reflect.call(constants, "isKeyDown", window, key));
        InputGate.physicalKey(key, down);
      }
    } finally {
      InputGate.rawQuery(false);
    }
  }

  private void clearNativeState() {
    Reflect.call(game.type("net.minecraft.client.KeyMapping"), "releaseAll");
    Reflect.set(Reflect.field(game.minecraft, "keyboardHandler"), "lastPreeditEvent", null);
    Object mouse = Reflect.field(game.minecraft, "mouseHandler");
    Reflect.call(mouse, "releaseMouse");
    Reflect.set(mouse, "activeButton", null);
    Reflect.set(mouse, "lastClick", null);
    Reflect.set(mouse, "mousePressedTime", 0.0);
    for (String name : List.of("smoothTurnX", "smoothTurnY"))
      Reflect.call(Reflect.field(mouse, name), "reset");
    Object scroll = Reflect.field(mouse, "scrollWheelHandler");
    for (String name : List.of("accumulatedScrollX", "accumulatedScrollY"))
      Reflect.set(scroll, name, 0.0);
    for (String name : List.of("isLeftPressed", "isMiddlePressed", "isRightPressed"))
      Reflect.set(mouse, name, false);
    for (String name : List.of("accumulatedDX", "accumulatedDY")) Reflect.set(mouse, name, 0.0);
    Reflect.call(mouse, "setIgnoreFirstMove");
    Object screen = game.screen();
    if (screen != null) Reflect.call(screen, "setDragging", false);
  }

  private void restoreInput() {
    try {
      physicalKeys();
      clearNativeState();
    } finally {
      InputGate.isolation(false);
    }
  }

  <T> T automation(Callable<T> action) {
    if (expired != null && leases.isEmpty())
      throw new DriverException(
          "INPUT_LEASE_EXPIRED", "Acquire a new lease before continuing automation");
    if (!game.config.backgroundExecution() && !game.focused())
      throw new DriverException(
          "FOCUS_LOST", "Foreground input requires the game window to be focused");
    return dispatch(action);
  }

  static <T> T dispatch(Callable<T> action) {
    InputGate.enter();
    try {
      return action.call();
    } catch (Exception failure) {
      throw Reflect.propagate(failure);
    } finally {
      InputGate.exit();
    }
  }

  void pointer(double guiX, double guiY) {
    if (!InputGate.isolated()) return;
    Object window = Reflect.call(game.minecraft, "getWindow");
    Object mouse = Reflect.field(game.minecraft, "mouseHandler");
    Reflect.set(
        mouse,
        "xpos",
        guiX
            * ((Number) Reflect.call(window, "getScreenWidth")).doubleValue()
            / ((Number) Reflect.call(window, "getGuiScaledWidth")).doubleValue());
    Reflect.set(
        mouse,
        "ypos",
        guiY
            * ((Number) Reflect.call(window, "getScreenHeight")).doubleValue()
            / ((Number) Reflect.call(window, "getGuiScaledHeight")).doubleValue());
    Reflect.set(mouse, "accumulatedDX", 0.0);
    Reflect.set(mouse, "accumulatedDY", 0.0);
  }

  void button(int button, boolean pressed) {
    if (!InputGate.isolated()) return;
    Class<?> constants = game.type("com.mojang.blaze3d.platform.InputConstants");
    Object mouse = Reflect.field(game.minecraft, "mouseHandler");
    for (String name : List.of("LEFT", "MIDDLE", "RIGHT"))
      if (((Number) Reflect.field(constants, "MOUSE_BUTTON_" + name)).intValue() == button)
        Reflect.set(
            mouse,
            "is"
                + name.substring(0, 1)
                + name.substring(1).toLowerCase(java.util.Locale.ROOT)
                + "Pressed",
            pressed);
  }

  @Override
  public void close() {
    try {
      if (!leases.isEmpty()) {
        leases.clear();
        restoreInput();
      }
    } finally {
      try {
        if (pauseOnLostFocus != null)
          Reflect.set(
              Reflect.field(game.minecraft, "options"), "pauseOnLostFocus", pauseOnLostFocus);
      } finally {
        publish();
      }
    }
  }
}
