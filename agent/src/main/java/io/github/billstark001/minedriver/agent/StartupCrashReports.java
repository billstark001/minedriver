package io.github.billstark001.minedriver.agent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/** A handled loader crash may write a report without reaching a frame or an uncaught handler. */
final class StartupCrashReports {
  private StartupCrashReports() {}

  static Path find(Path runDirectory, long startedMillis) throws IOException {
    Path directory = runDirectory.resolve("crash-reports");
    if (!Files.isDirectory(directory)) return null;
    try (var reports = Files.newDirectoryStream(directory, "crash-*-client.txt")) {
      for (Path report : reports)
        if (Files.isRegularFile(report, LinkOption.NOFOLLOW_LINKS)
            && Files.getLastModifiedTime(report).toMillis() >= startedMillis)
          return report.toAbsolutePath().normalize();
    }
    return null;
  }
}
