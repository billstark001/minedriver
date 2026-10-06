package io.github.billstark001.minedriver.api;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.BooleanSupplier;

/** A small loader-neutral facade. Custom game code can run through onClient/onServer. */
public final class ProbeContext {
  private final Driver driver;
  private final Duration operationTimeout;

  public ProbeContext(Driver driver, Duration operationTimeout) {
    this.driver = driver;
    this.operationTimeout = operationTimeout;
  }

  public Object call(String command) {
    return call(command, Map.of());
  }

  public Object call(String command, Map<String, ?> parameters) {
    return driver.call(command, parameters);
  }

  public <T> T onClient(Callable<T> action) {
    return driver.onClient(action, operationTimeout);
  }

  public <T> T onServer(Callable<T> action) {
    return driver.onServer(action, operationTimeout);
  }

  public Path outputDirectory() {
    return driver.outputDirectory();
  }

  public void await(String description, Duration timeout, BooleanSupplier condition) {
    if (driver.isGameThread())
      throw new DriverException("BAD_THREAD", "Do not wait on a game thread");
    if (timeout.isNegative() || timeout.isZero())
      throw new IllegalArgumentException("Positive timeout required");
    long deadline = System.nanoTime() + timeout.toNanos();
    while (!condition.getAsBoolean()) {
      if (System.nanoTime() >= deadline)
        throw new DriverException("TIMEOUT", "Timed out waiting for " + description);
      try {
        Thread.sleep(50);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        throw new DriverException("CANCELLED", "Waiting interrupted: " + description, interrupted);
      }
    }
  }

  public void require(boolean condition, String message) {
    if (!condition) throw new DriverException("ASSERTION_FAILED", message);
  }

  public void activate(Selector selector) {
    call("ui.activate", Map.of("selector", selector.parameters()));
  }

  public void click(Selector selector) {
    call("input.click", Map.of("selector", selector.parameters()));
  }

  public void text(Selector selector, String value) {
    call("ui.text", Map.of("selector", selector.parameters(), "value", value));
  }

  public Object screenshot(String name) {
    return call("screenshot.capture", Map.of("name", name));
  }

  public Object inspect() {
    return call("session.inspect");
  }
}
