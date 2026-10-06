package io.github.billstark001.minedriver.agent;

import io.github.billstark001.minedriver.api.DriverException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class CommandRegistry {
  record Description(
      String name,
      String description,
      boolean readOnly,
      String channel,
      Map<String, Object> inputSchema) {}

  record Entry(Description description, Handler handler) {}

  @FunctionalInterface
  interface Handler {
    Object apply(Map<String, Object> parameters) throws Exception;
  }

  private final Map<String, Entry> commands = new TreeMap<>();
  private final java.util.concurrent.locks.ReentrantLock actions =
      new java.util.concurrent.locks.ReentrantLock(true);
  private volatile RunReport report;

  void report(RunReport value) {
    report = value;
  }

  synchronized void register(
      String name, String description, boolean readOnly, String channel, Handler handler) {
    if (!name.matches("[a-z][a-zA-Z0-9_.-]*") || commands.containsKey(name))
      throw new IllegalArgumentException("Invalid or duplicate command: " + name);
    commands.put(
        name,
        new Entry(
            new Description(
                name,
                description,
                readOnly,
                channel,
                io.github.billstark001.minedriver.protocol.CommandSchemas.of(name)),
            handler));
  }

  Object invoke(String name, Map<String, Object> parameters) throws Exception {
    Entry entry;
    synchronized (this) {
      entry = commands.get(name);
    }
    boolean lock =
        !List.of("agent.status", "scenario.status", "session.commands", "session.close")
            .contains(name);
    long started = System.nanoTime();
    if (lock) actions.lockInterruptibly();
    try {
      if (entry == null) throw new DriverException("UNKNOWN_COMMAND", "Unknown command: " + name);
      Object result = entry.handler().apply(parameters);
      if (report != null)
        report.step(name, channel(name), System.nanoTime() - started, result, null);
      return result;
    } catch (Exception | Error failure) {
      if (report != null)
        report.step(name, channel(name), System.nanoTime() - started, null, failure);
      throw failure;
    } finally {
      if (lock) actions.unlock();
    }
  }

  synchronized List<Description> descriptions() {
    return commands.values().stream().map(Entry::description).toList();
  }

  synchronized String channel(String command) {
    var entry = commands.get(command);
    return entry == null ? "unknown" : entry.description().channel();
  }
}
