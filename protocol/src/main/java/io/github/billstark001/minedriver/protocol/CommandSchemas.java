package io.github.billstark001.minedriver.protocol;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Discoverable JSON schemas for the stable built-in command surface. */
public final class CommandSchemas {
  private CommandSchemas() {}

  public static Map<String, Object> of(String command) {
    var properties = new LinkedHashMap<String, Object>();
    List<String> required = List.of();
    switch (command) {
      case "ui.tree" -> properties.put("limit", integer(1, 8192));
      case "ui.activate", "input.click", "assert.widget", "wait.widget" -> {
        properties.put("selector", selector());
        if (!command.equals("input.click")) required = List.of("selector");
        else {
          properties.put("x", number());
          properties.put("y", number());
          properties.put(
              "button",
              Map.of(
                  "oneOf",
                  List.of(
                      integer(0, 8),
                      Map.of(
                          "type",
                          "string",
                          "enum",
                          List.of("LEFT", "RIGHT", "MIDDLE", "4", "5", "6", "7", "8")))));
          properties.put("modifiers", integer(0, 65535));
        }
      }
      case "ui.text" -> {
        properties.put("selector", selector());
        properties.put("value", string());
        required = List.of("selector", "value");
      }
      case "ui.settings" -> {
        properties.put("modId", string());
        properties.put("configClass", string());
      }
      case "input.key" -> {
        properties.put(
            "key",
            Map.of(
                "oneOf",
                List.of(
                    Map.of(
                        "type",
                        "string",
                        "description",
                        "InputConstants name without KEY_, for example ESCAPE, RETURN, W, F3"),
                    integer(0, 65535))));
        properties.put("scanCode", integer(0, 65535));
        properties.put("modifiers", integer(0, 65535));
        properties.put("frames", integer(1, 1200));
      }
      case "input.text" -> {
        properties.put("value", string());
        required = List.of("value");
      }
      case "input.scroll" -> {
        for (String name : List.of("x", "y", "horizontal", "vertical"))
          properties.put(name, number());
      }
      case "render.await" -> properties.put("frames", integer(1, 1200));
      case "screenshot.capture" -> {
        properties.put("name", artifact());
        properties.put("frames", integer(1, 120));
      }
      case "screenshot.compare" -> {
        properties.put("actual", string());
        properties.put("expected", string());
        properties.put("name", artifact());
        properties.put("region", rectangle());
        properties.put("masks", Map.of("type", "array", "items", rectangle()));
        properties.put("channelTolerance", integer(0, 255));
        properties.put("maxChangedFraction", Map.of("type", "number", "minimum", 0, "maximum", 1));
        required = List.of("actual", "expected");
      }
      case "resources.language" -> {
        properties.put("code", string());
        required = List.of("code");
      }
      case "window.configure" -> {
        properties.put("width", integer(320, 7680));
        properties.put("height", integer(240, 4320));
        properties.put("guiScale", integer(0, 16));
      }
      case "world.create" -> {
        properties.put("name", string());
        properties.put("seed", string());
        properties.put("flat", bool());
        properties.put("mode", Map.of("type", "string", "enum", List.of("CREATIVE", "SURVIVAL")));
      }
      case "world.command", "network.command" -> {
        properties.put("command", string());
        required = List.of("command");
      }
      case "world.connect" -> {
        properties.put("address", string());
        required = List.of("address");
      }
      case "world.fixture" -> {
        properties.put(
            "position",
            object(
                Map.of(
                    "x", number(), "y", number(), "z", number(), "yaw", number(), "pitch",
                    number()),
                List.of()));
        properties.put(
            "blocks",
            Map.of(
                "type",
                "array",
                "items",
                object(
                    Map.of("x", number(), "y", number(), "z", number(), "state", string()),
                    List.of("x", "y", "z", "state"))));
        properties.put(
            "inventory",
            Map.of(
                "type",
                "array",
                "items",
                object(
                    Map.of("slot", integer(0, 8), "item", string(), "count", integer(1, 99)),
                    List.of("slot", "item"))));
        properties.put("commands", Map.of("type", "array", "items", string()));
      }
      case "world.snapshot" -> {
        properties.put(
            "blocks",
            Map.of(
                "type",
                "array",
                "items",
                object(
                    Map.of("x", number(), "y", number(), "z", number()), List.of("x", "y", "z"))));
        properties.put("slots", Map.of("type", "array", "items", integer(0, 35)));
      }
      case "profile.start" -> properties.put("jfr", bool());
      case "profile.stop" -> properties.put("name", artifact());
      case "assert.state", "wait.state" -> {
        properties.put("path", string());
        properties.put("equals", Map.of());
        required = List.of("path", "equals");
      }
      case "scenario.run" -> {
        properties.put("class", string());
        properties.put("plan", Map.of("type", "object"));
      }
      case "java.invoke" -> {
        properties.put("class", string());
        properties.put("method", string());
        properties.put("arguments", Map.of("type", "array"));
        required = List.of("class", "method");
      }
      default -> {
        if (command.startsWith("custom."))
          return Map.of("type", "object", "additionalProperties", true);
      }
    }
    if (List.of(
            "wait.state",
            "wait.widget",
            "render.await",
            "screenshot.capture",
            "resources.language",
            "world.create",
            "world.disconnect",
            "world.fixture",
            "world.connect")
        .contains(command)) properties.put("timeoutMillis", integer(1, 3_600_000));
    return object(properties, required);
  }

  private static Map<String, Object> string() {
    return Map.of("type", "string");
  }

  private static Map<String, Object> bool() {
    return Map.of("type", "boolean");
  }

  private static Map<String, Object> number() {
    return Map.of("type", "number");
  }

  private static Map<String, Object> integer(int min, int max) {
    return Map.of("type", "integer", "minimum", min, "maximum", max);
  }

  private static Map<String, Object> artifact() {
    return Map.of(
        "type",
        "string",
        "pattern",
        "^[A-Za-z0-9][A-Za-z0-9._-]*$",
        "maxLength",
        100,
        "description",
        "Simple artifact name, no paths or .. segments");
  }

  private static Map<String, Object> rectangle() {
    return object(
        Map.of(
            "x",
            integer(0, 100000),
            "y",
            integer(0, 100000),
            "width",
            integer(1, 100000),
            "height",
            integer(1, 100000)),
        List.of("x", "y", "width", "height"));
  }

  private static Map<String, Object> selector() {
    return Map.of(
        "type",
        "object",
        "properties",
        Map.of(
            "key",
            string(),
            "label",
            string(),
            "type",
            string(),
            "path",
            string(),
            "stableId",
            string()),
        "minProperties",
        1,
        "additionalProperties",
        false);
  }

  private static Map<String, Object> object(Map<String, ?> properties, List<String> required) {
    return Map.of(
        "type",
        "object",
        "properties",
        properties,
        "required",
        required,
        "additionalProperties",
        false);
  }
}
