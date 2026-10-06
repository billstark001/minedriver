package io.github.billstark001.minedriver.agent;

import io.github.billstark001.minedriver.api.DriverException;
import io.github.billstark001.minedriver.hooks.FrameClock;
import io.github.billstark001.minedriver.protocol.Paths;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;

final class Profiler implements AutoCloseable {
  private Recording recording;
  private long firstFrame;
  private long started;
  private long gcBefore;
  private long gcTimeBefore;

  synchronized Object start(Map<String, Object> parameters) throws Exception {
    if (started != 0)
      throw new DriverException("PROFILE_ALREADY_RUNNING", "Stop the current profile first");
    if (Parameters.bool(parameters, "jfr", false)) {
      recording = new Recording(Configuration.getConfiguration("profile"));
      recording.setMaxSize(256L * 1024 * 1024);
      recording.setMaxAge(Duration.ofMinutes(30));
      recording.start();
    }
    firstFrame = FrameClock.count();
    started = System.nanoTime();
    gcBefore = gc(false);
    gcTimeBefore = gc(true);
    return Map.of("firstFrame", firstFrame, "jfr", recording != null);
  }

  synchronized Object stop(Path output, Map<String, Object> parameters, GameRuntime game)
      throws Exception {
    if (started == 0) throw new DriverException("NO_PROFILE", "No profile is running");
    var result = new LinkedHashMap<String, Object>();
    try {
      long lastFrame = FrameClock.count();
      var samples = FrameClock.after(firstFrame, lastFrame);
      result.put("durationMillis", (System.nanoTime() - started) / 1_000_000.0);
      result.put("observedFrames", lastFrame - firstFrame);
      result.put("retainedFrames", samples.size());
      result.put("droppedFrames", Math.max(0, lastFrame - firstFrame - samples.size()));
      result.put(
          "frameIntervalMillis",
          stats(
              samples.stream()
                  .mapToLong(FrameClock.Sample::intervalNanos)
                  .filter(n -> n > 0)
                  .toArray()));
      result.put(
          "renderMethodMillis",
          stats(samples.stream().mapToLong(FrameClock.Sample::workNanos).toArray()));
      result.put("gcCount", gc(false) - gcBefore);
      result.put("gcMillis", gc(true) - gcTimeBefore);
      result.put(
          "heapUsedBytes", ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
      if (game.client(game::server, Duration.ofSeconds(5)) != null)
        result.put(
            "serverAverageTickMillis",
            game.server(
                () ->
                    ((Number) Reflect.call(game.server(), "getAverageTickTimeNanos")).doubleValue()
                        / 1_000_000,
                Duration.ofSeconds(5)));
      if (recording != null) {
        recording.stop();
        Path file = Paths.child(output, Parameters.string(parameters, "name", "profile") + ".jfr");
        recording.dump(file);
        result.put("jfr", file.toString());
      }
      return result;
    } finally {
      close();
    }
  }

  static Map<String, Object> stats(long[] values) {
    Arrays.sort(values);
    if (values.length == 0) return Map.of("count", 0);
    return Map.of(
        "count",
        values.length,
        "p50",
        values[(values.length - 1) / 2] / 1e6,
        "p95",
        values[(int) Math.ceil(values.length * .95) - 1] / 1e6,
        "max",
        values[values.length - 1] / 1e6,
        "mean",
        Arrays.stream(values).average().orElse(0) / 1e6);
  }

  private static long gc(boolean time) {
    return ManagementFactory.getGarbageCollectorMXBeans().stream()
        .mapToLong(bean -> Math.max(0, time ? bean.getCollectionTime() : bean.getCollectionCount()))
        .sum();
  }

  @Override
  public synchronized void close() {
    if (recording != null) {
      recording.close();
      recording = null;
    }
    started = 0;
  }
}
