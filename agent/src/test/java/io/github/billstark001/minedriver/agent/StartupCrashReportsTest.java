package io.github.billstark001.minedriver.agent;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StartupCrashReportsTest {
  @TempDir Path directory;

  @Test
  void ignoresOldReportsDirectoriesAndUnrelatedFiles() throws Exception {
    Path reports = Files.createDirectory(directory.resolve("crash-reports"));
    Path old = Files.writeString(reports.resolve("crash-old-client.txt"), "Old fixture");
    Files.setLastModifiedTime(old, FileTime.fromMillis(1000));
    Files.writeString(reports.resolve("latest.log"), "Not a crash report");
    Files.createDirectory(reports.resolve("crash-directory-client.txt"));
    assertNull(StartupCrashReports.find(directory, 2000));
  }

  @Test
  void detectsFreshNativeClientReportWithoutInitializingMinecraft() throws Exception {
    assertNull(StartupCrashReports.find(directory, 0));
    Path reports = Files.createDirectory(directory.resolve("crash-reports"));
    Path crash = Files.writeString(reports.resolve("crash-new-client.txt"), "Native failure");
    assertEquals(crash.toAbsolutePath(), StartupCrashReports.find(directory, 0));
  }
}
