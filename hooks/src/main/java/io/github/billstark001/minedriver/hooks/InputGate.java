package io.github.billstark001.minedriver.hooks;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.atomic.AtomicLong;

/** Bootstrap-visible standard input boundary, with scoped automation and queue generations. */
public final class InputGate {
  private static final Set<String> INSTALLED = ConcurrentHashMap.newKeySet();
  private static final AtomicIntegerArray KEYS = new AtomicIntegerArray(65536);
  private static final AtomicIntegerArray PHYSICAL = new AtomicIntegerArray(65536);
  private static final AtomicIntegerArray QUARANTINE = new AtomicIntegerArray(65536);
  private static final ThreadLocal<Integer> SOURCE = ThreadLocal.withInitial(() -> 0);
  private static final ThreadLocal<Boolean> RAW_QUERY = ThreadLocal.withInitial(() -> false);
  private static final AtomicLong GENERATION = new AtomicLong();
  private static final AtomicLong DROPPED = new AtomicLong();
  private static volatile boolean isolated;
  private static volatile String failure;

  private InputGate() {}

  public static void installed(String name) {
    INSTALLED.add(name);
  }

  public static List<String> installed() {
    return INSTALLED.stream().sorted().toList();
  }

  public static void failure(String message) {
    failure = message;
  }

  public static String failure() {
    return failure;
  }

  public static boolean isolated() {
    return isolated && !RAW_QUERY.get();
  }

  public static boolean automation() {
    return SOURCE.get() > 0;
  }

  public static void enter() {
    SOURCE.set(SOURCE.get() + 1);
  }

  public static void exit() {
    int depth = SOURCE.get() - 1;
    if (depth <= 0) SOURCE.remove();
    else SOURCE.set(depth);
  }

  public static void rawQuery(boolean value) {
    if (value) RAW_QUERY.set(true);
    else RAW_QUERY.remove();
  }

  public static void isolation(boolean value) {
    GENERATION.incrementAndGet();
    for (int i = 0; i < KEYS.length(); i++) {
      KEYS.set(i, 0);
      if (!value) QUARANTINE.set(i, PHYSICAL.get(i));
    }
    isolated = value;
  }

  public static void physicalKey(int key, boolean down) {
    if (key >= 0 && key < PHYSICAL.length()) PHYSICAL.set(key, down ? 1 : 0);
  }

  public static void key(int key, boolean down) {
    if (key >= 0 && key < KEYS.length()) KEYS.set(key, down ? 1 : 0);
  }

  public static boolean keyDown(int key) {
    return key >= 0 && key < KEYS.length() && KEYS.get(key) != 0;
  }

  public static boolean polled(int key, boolean down) {
    if (RAW_QUERY.get()) return down;
    physicalKey(key, down);
    if (key >= 0 && key < QUARANTINE.length() && QUARANTINE.get(key) != 0) {
      if (!down) QUARANTINE.set(key, 0);
      return false;
    }
    return down;
  }

  public static long dropped() {
    return DROPPED.get();
  }

  public static boolean blockExternal() {
    if (isolated && !automation()) {
      DROPPED.incrementAndGet();
      return true;
    }
    return false;
  }

  public static boolean blockKey(int key, int action) {
    if (automation()) return false;
    physicalKey(key, action != 0);
    if (blockExternal()) return true;
    if (key >= 0 && key < QUARANTINE.length() && QUARANTINE.get(key) != 0) {
      if (action == 0) QUARANTINE.set(key, 0);
      DROPPED.incrementAndGet();
      return true;
    }
    return false;
  }

  public static boolean blockMouse(int button, int action) {
    return blockKey(65520 + button, action);
  }

  public static void physicalMouse(int button, boolean down) {
    physicalKey(65520 + button, down);
  }

  public static Runnable queued(Runnable action) {
    long generation = GENERATION.get();
    return () -> {
      if (generation != GENERATION.get()) {
        DROPPED.incrementAndGet();
        return;
      }
      action.run();
    };
  }
}
