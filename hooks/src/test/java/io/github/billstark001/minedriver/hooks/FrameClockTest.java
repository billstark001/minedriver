package io.github.billstark001.minedriver.hooks;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class FrameClockTest {
  @Test
  void boundedRingKeepsCompletedFramesAndHonorsSnapshotEnd() {
    long before = FrameClock.count();
    FrameClock.begin();
    assertEquals(before, FrameClock.count());
    FrameClock.end();
    long snapshot = FrameClock.count();
    FrameClock.begin();
    FrameClock.end();
    assertEquals(1, FrameClock.after(before, snapshot).size());
    for (int i = 0; i < 4200; i++) {
      FrameClock.begin();
      FrameClock.end();
    }
    var samples = FrameClock.after(before);
    assertEquals(4096, samples.size());
    assertEquals(FrameClock.count(), samples.get(samples.size() - 1).sequence());
    assertTrue(
        samples.stream()
            .allMatch(sample -> sample.workNanos() >= 0 && sample.intervalNanos() >= 0));
  }
}
