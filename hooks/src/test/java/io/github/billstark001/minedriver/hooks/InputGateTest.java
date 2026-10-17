package io.github.billstark001.minedriver.hooks;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class InputGateTest {
  @Test
  void externalInputsAreBlockedWhileScopedAutomationAndVirtualStateRemainUsable() {
    InputGate.isolation(true);
    try {
      assertTrue(InputGate.blockKey(65, 1));
      assertFalse(InputGate.keyDown(65));
      InputGate.enter();
      try {
        assertFalse(InputGate.blockKey(65, 1));
        InputGate.key(65, true);
        assertTrue(InputGate.keyDown(65));
      } finally {
        InputGate.exit();
      }
      assertTrue(InputGate.blockExternal());
      InputGate.rawQuery(true);
      try {
        assertFalse(InputGate.isolated());
      } finally {
        InputGate.rawQuery(false);
      }
    } finally {
      InputGate.isolation(false);
      InputGate.blockKey(65, 0);
    }
  }

  @Test
  void switchingIsolationInvalidatesQueuedInputAndQuarantinesHeldKeysAndMouseButtons() {
    var ran = new AtomicBoolean();
    Runnable queued = InputGate.queued(() -> ran.set(true));
    InputGate.isolation(true);
    try {
      queued.run();
      assertFalse(ran.get());
      InputGate.blockKey(70, 1);
      InputGate.blockMouse(0, 1);
    } finally {
      InputGate.isolation(false);
    }
    assertTrue(InputGate.blockKey(70, 2));
    assertTrue(InputGate.blockKey(70, 0));
    assertFalse(InputGate.blockKey(70, 1));
    assertTrue(InputGate.blockMouse(0, 1));
    assertTrue(InputGate.blockMouse(0, 0));
    assertFalse(InputGate.blockMouse(0, 1));
    InputGate.blockKey(70, 0);
    InputGate.blockMouse(0, 0);
  }
}
