package io.github.billstark001.minedriver.agent;

import io.github.billstark001.minedriver.api.DriverException;
import java.time.Duration;

/** One monotonic budget shared by command admission, dispatch and all nested waits. */
final class OperationDeadline implements AutoCloseable {
  private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();
  private final Long previous;

  private OperationDeadline(Duration timeout) {
    if (timeout.isZero() || timeout.isNegative())
      throw Parameters.invalid("Timeout must be positive");
    previous = CURRENT.get();
    long requested = System.nanoTime() + timeout.toNanos();
    CURRENT.set(previous == null ? requested : Math.min(previous, requested));
  }

  static OperationDeadline enter(Duration timeout) {
    return new OperationDeadline(timeout);
  }

  static Duration remaining(Duration maximum) {
    Long deadline = CURRENT.get();
    long nanos =
        deadline == null
            ? maximum.toNanos()
            : Math.min(maximum.toNanos(), deadline - System.nanoTime());
    if (nanos <= 0) throw new DriverException("OPERATION_TIMEOUT", "Operation deadline expired");
    return Duration.ofNanos(nanos);
  }

  @Override
  public void close() {
    if (previous == null) CURRENT.remove();
    else CURRENT.set(previous);
  }
}
