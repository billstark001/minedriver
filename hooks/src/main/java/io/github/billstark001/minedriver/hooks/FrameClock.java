package io.github.billstark001.minedriver.hooks;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

/** Bootstrap-visible primitives only; no dependency on the loader, Minecraft, or agent runtime. */
public final class FrameClock {
  private static final int CAPACITY = 4096;
  private static final AtomicLong COUNT = new AtomicLong();
  private static final AtomicLongArray START = new AtomicLongArray(CAPACITY);
  private static final AtomicLongArray INTERVAL = new AtomicLongArray(CAPACITY);
  private static final AtomicLongArray WORK = new AtomicLongArray(CAPACITY);
  private static final AtomicLongArray SEQUENCE = new AtomicLongArray(CAPACITY);
  private static volatile long started;
  private static long previous;

  private FrameClock() {}

  public static void begin() {
    started = System.nanoTime();
  }

  public static void end() {
    long start = started;
    long sequence = COUNT.get() + 1;
    int index = (int) (sequence % CAPACITY);
    SEQUENCE.set(index, 0);
    START.set(index, start);
    INTERVAL.set(index, previous == 0 ? 0 : start - previous);
    WORK.set(index, Math.max(0, System.nanoTime() - start));
    previous = start;
    SEQUENCE.set(index, sequence);
    COUNT.set(sequence);
  }

  public static long count() {
    return COUNT.get();
  }

  public record Sample(long sequence, long startedNanos, long intervalNanos, long workNanos) {}

  public static List<Sample> after(long first) {
    return after(first, COUNT.get());
  }

  public static List<Sample> after(long first, long last) {
    var result = new ArrayList<Sample>();
    for (long sequence = Math.max(first + 1, last - CAPACITY + 1); sequence <= last; sequence++) {
      int index = (int) (sequence % CAPACITY);
      if (SEQUENCE.get(index) != sequence) continue;
      var sample = new Sample(sequence, START.get(index), INTERVAL.get(index), WORK.get(index));
      if (SEQUENCE.get(index) == sequence) result.add(sample);
    }
    return List.copyOf(result);
  }
}
