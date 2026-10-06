package io.github.billstark001.minedriver.agent;

import io.github.billstark001.minedriver.api.DriverException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class CommandRegistry {
  record Description(String name, String description, boolean readOnly, String channel) {}

  record Entry(Description description, Handler handler) {}

  @FunctionalInterface
  interface Handler {
    Object apply(Map<String, Object> parameters) throws Exception;
  }

  private final Map<String, Entry> commands = new TreeMap<>();

  synchronized void register(
      String name, String description, boolean readOnly, String channel, Handler handler) {
    if (!name.matches("[a-z][a-zA-Z0-9_.-]*") || commands.containsKey(name))
      throw new IllegalArgumentException("Invalid or duplicate command: " + name);
    commands.put(name, new Entry(new Description(name, description, readOnly, channel), handler));
  }

  Object invoke(String name, Map<String, Object> parameters) throws Exception {
    Entry entry;
    synchronized (this) {
      entry = commands.get(name);
    }
    if (entry == null) throw new DriverException("UNKNOWN_COMMAND", "Unknown command: " + name);
    return entry.handler().apply(parameters);
  }

  synchronized List<Description> descriptions() {
    return commands.values().stream().map(Entry::description).toList();
  }

  synchronized String channel(String command) {
    var entry = commands.get(command);
    return entry == null ? "unknown" : entry.description().channel();
  }
}
