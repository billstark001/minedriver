package io.github.billstark001.minedriver.agent;

import static org.junit.jupiter.api.Assertions.*;

import io.github.billstark001.minedriver.api.DriverException;
import java.util.List;
import org.junit.jupiter.api.Test;

class StartupWarningsTest {
  private static final StartupWarnings.Issue KNOWN =
      new StartupWarnings.Issue("WARNING", "cloth_config", "deprecated.logo", "Old logo metadata");

  @Test
  void exactAllowlistAcceptsOnlyTheDiagnosedModAndIssue() {
    assertDoesNotThrow(
        () -> StartupWarnings.requireAccepted(List.of(KNOWN), false, List.of(KNOWN.identity())));
    for (var issue :
        List.of(
            KNOWN,
            new StartupWarnings.Issue("WARNING", "other", KNOWN.key(), KNOWN.message()),
            new StartupWarnings.Issue("WARNING", KNOWN.modId(), "unknown", "New problem"))) {
      List<String> accepted = issue == KNOWN ? List.of() : List.of(KNOWN.identity());
      assertEquals(
          "STARTUP_WARNINGS",
          assertThrows(
                  DriverException.class,
                  () -> StartupWarnings.requireAccepted(List.of(issue), false, accepted))
              .code());
    }
    var unknown = new StartupWarnings.Issue("WARNING", "other", "new", "New problem");
    assertThrows(
        DriverException.class,
        () ->
            StartupWarnings.requireAccepted(
                List.of(KNOWN, unknown), false, List.of(KNOWN.identity())));
  }

  @Test
  void errorsAndUninspectableScreensNeverProceedEvenWithTheLegacyOverride() {
    assertEquals(
        "STARTUP_BLOCKED",
        assertThrows(
                DriverException.class,
                () -> StartupWarnings.requireAccepted(List.of(), true, List.of()))
            .code());
    var error = new StartupWarnings.Issue("ERROR", "cloth_config", KNOWN.key(), "Loading failed");
    assertEquals(
        "STARTUP_ERRORS",
        assertThrows(
                DriverException.class,
                () ->
                    StartupWarnings.requireAccepted(
                        List.of(error), true, List.of(error.identity())))
            .code());
  }
}
