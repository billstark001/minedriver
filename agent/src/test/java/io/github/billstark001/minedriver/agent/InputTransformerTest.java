package io.github.billstark001.minedriver.agent;

import static org.junit.jupiter.api.Assertions.*;

import io.github.billstark001.minedriver.hooks.InputGate;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;

class InputTransformerTest {
  private static final class Loader extends ClassLoader {
    Class<?> define(byte[] bytes) {
      return defineClass(null, bytes, 0, bytes.length);
    }
  }

  @Test
  void transformedPollingVerifiesAndUsesOnlyVirtualStateDuringIsolation() throws Exception {
    var writer = new ClassWriter(0);
    writer.visit(
        Opcodes.V17,
        Opcodes.ACC_PUBLIC,
        "com/mojang/blaze3d/platform/InputConstants",
        null,
        "java/lang/Object",
        null);
    var method =
        writer.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "isKeyDown", "(I)Z", null, null);
    method.visitCode();
    method.visitInsn(Opcodes.ICONST_1);
    method.visitInsn(Opcodes.IRETURN);
    method.visitMaxs(1, 1);
    method.visitEnd();
    writer.visitEnd();
    var transformer = new InputTransformer();
    byte[] modified =
        transformer.transform(
            null,
            null,
            "com/mojang/blaze3d/platform/InputConstants",
            null,
            null,
            writer.toByteArray());
    assertNotNull(modified);
    assertNull(
        transformer.transform(
            null, null, "com/mojang/blaze3d/platform/InputConstants", null, null, modified));
    var poll = new Loader().define(modified).getMethod("isKeyDown", int.class);
    InputGate.isolation(true);
    try {
      assertEquals(false, poll.invoke(null, 10));
      InputGate.key(10, true);
      assertEquals(true, poll.invoke(null, 10));
      InputGate.key(10, false);
      assertEquals(false, poll.invoke(null, 10));
    } finally {
      InputGate.isolation(false);
    }
    assertEquals(true, poll.invoke(null, 10));
    InputGate.isolation(true);
    InputGate.blockKey(10, 1);
    InputGate.isolation(false);
    assertEquals(false, poll.invoke(null, 10));
    InputGate.rawQuery(true);
    try {
      assertEquals(true, poll.invoke(null, 10));
    } finally {
      InputGate.rawQuery(false);
    }
    InputGate.blockKey(10, 0);
    assertEquals(true, poll.invoke(null, 10));
    InputGate.physicalKey(10, false);
  }
}
