package io.github.billstark001.minedriver.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BuildInfoTest {
  @Test
  void generatedVersionMatchesTheConfiguredBuild() {
    assertEquals(System.getProperty("minedriver.expectedVersion"), BuildInfo.VERSION);
  }
}
