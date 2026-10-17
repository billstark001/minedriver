package io.github.billstark001.minedriver.agent;

import static org.junit.jupiter.api.Assertions.*;

import io.github.billstark001.minedriver.api.DriverException;
import java.util.List;
import org.junit.jupiter.api.Test;

class NeoForgeIssuesTest {
  private static final NeoForgeIssues.Issue KNOWN =
      new NeoForgeIssues.Issue("WARNING", "cloth_config", "deprecated.logo", "Old logo metadata");

  @Test
  void exactAllowlistAcceptsOnlyTheDiagnosedModAndIssue() {
    assertDoesNotThrow(
        () -> NeoForgeIssues.requireAccepted(List.of(KNOWN), List.of(KNOWN.identity())));
    for (var issue :
        List.of(
            KNOWN,
            new NeoForgeIssues.Issue("WARNING", "other", KNOWN.key(), KNOWN.message()),
            new NeoForgeIssues.Issue("WARNING", KNOWN.modId(), "unknown", "New problem"))) {
      List<String> accepted = issue == KNOWN ? List.of() : List.of(KNOWN.identity());
      assertEquals(
          "STARTUP_WARNINGS",
          assertThrows(
                  DriverException.class,
                  () -> NeoForgeIssues.requireAccepted(List.of(issue), accepted))
              .code());
    }
    var unknown = new NeoForgeIssues.Issue("WARNING", "other", "new", "New problem");
    assertThrows(
        DriverException.class,
        () -> NeoForgeIssues.requireAccepted(List.of(KNOWN, unknown), List.of(KNOWN.identity())));
  }

  @Test
  void errorsAndUninspectableScreensNeverProceed() {
    assertEquals(
        "STARTUP_BLOCKED",
        assertThrows(
                DriverException.class, () -> NeoForgeIssues.requireAccepted(List.of(), List.of()))
            .code());
    var error = new NeoForgeIssues.Issue("ERROR", "cloth_config", KNOWN.key(), "Loading failed");
    assertEquals(
        "STARTUP_ERRORS",
        assertThrows(
                DriverException.class,
                () -> NeoForgeIssues.requireAccepted(List.of(error), List.of(error.identity())))
            .code());
  }
}
