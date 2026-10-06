package io.github.billstark001.minedriver.api;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Callable;

/** Operations are issued from a scenario worker, never from a blocked game thread. */
public interface Driver {
  Object call(String command, Map<String, ?> parameters);

  <T> T onClient(Callable<T> action, Duration timeout);

  <T> T onServer(Callable<T> action, Duration timeout);

  Path outputDirectory();

  default boolean isGameThread() {
    return false;
  }
}
