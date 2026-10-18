package io.github.billstark001.minedriver.agent;

import static org.junit.jupiter.api.Assertions.*;

import io.github.billstark001.minedriver.hooks.InputGate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.objectweb.asm.*;

class InputTransformerTest {
  private static final class Loader extends ClassLoader {
    Class<?> define(byte[] bytes) {
      return defineClass(null, bytes, 0, bytes.length);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"(I)Z", "(JI)Z"})
  void pollingUsesTheCorrectKeySlotWithLongWindowHandles(String descriptor) throws Exception {
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
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "isKeyDown", descriptor, null, null);
    method.visitCode();
    var nativeFalse = new Label();
    var returned = new Label();
    method.visitInsn(Opcodes.ICONST_0);
    method.visitJumpInsn(Opcodes.IFEQ, nativeFalse);
    method.visitInsn(Opcodes.ICONST_1);
    method.visitJumpInsn(Opcodes.GOTO, returned);
    method.visitLabel(nativeFalse);
    method.visitFrame(Opcodes.F_CHOP, descriptor.equals("(I)Z") ? 1 : 2, null, 0, null);
    method.visitInsn(Opcodes.ICONST_0);
    method.visitLabel(returned);
    method.visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {Opcodes.INTEGER});
    method.visitInsn(Opcodes.IRETURN);
    method.visitMaxs(1, descriptor.equals("(I)Z") ? 1 : 3);
    method.visitEnd();
    writer.visitEnd();
    byte[] modified =
        new InputTransformer()
            .transform(
                null,
                null,
                "com/mojang/blaze3d/platform/InputConstants",
                null,
                null,
                writer.toByteArray());
    assertNotNull(modified);
    Class<?> type = new Loader().define(modified);
    var poll =
        descriptor.equals("(I)Z")
            ? type.getMethod("isKeyDown", int.class)
            : type.getMethod("isKeyDown", long.class, int.class);
    Object[] arguments =
        descriptor.equals("(I)Z") ? new Object[] {87} : new Object[] {0x100000001L, 87};
    InputGate.isolation(true);
    try {
      assertEquals(false, poll.invoke(null, arguments));
      InputGate.key(87, true);
      assertEquals(true, poll.invoke(null, arguments));
      InputGate.key(87, false);
      assertEquals(false, poll.invoke(null, arguments));
    } finally {
      InputGate.isolation(false);
    }
  }

  @Test
  void legacyKeyboardAndMouseCallbacksVerifyAndQuarantineTheCorrectCodes() throws Exception {
    verifyLegacyCallback(
        "net/minecraft/client/KeyboardHandler",
        "keyPress",
        "(JIIII)V",
        new Class<?>[] {long.class, int.class, int.class, int.class, int.class},
        new Object[] {0x100000001L, 87, 9, 1, 0},
        new Object[] {0x100000001L, 87, 9, 0, 0});
    verifyLegacyCallback(
        "net/minecraft/client/MouseHandler",
        "onPress",
        "(JIII)V",
        new Class<?>[] {long.class, int.class, int.class, int.class},
        new Object[] {0x100000001L, 0, 1, 0},
        new Object[] {0x100000001L, 0, 0, 0});
  }

  private static void verifyLegacyCallback(
      String owner,
      String name,
      String descriptor,
      Class<?>[] signature,
      Object[] press,
      Object[] release)
      throws Exception {
    var writer = new ClassWriter(0);
    writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, owner, null, "java/lang/Object", null);
    writer.visitField(Opcodes.ACC_PUBLIC, "calls", "I", null, null).visitEnd();
    var constructor = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
    constructor.visitCode();
    constructor.visitVarInsn(Opcodes.ALOAD, 0);
    constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
    constructor.visitInsn(Opcodes.RETURN);
    constructor.visitMaxs(1, 1);
    constructor.visitEnd();
    var method = writer.visitMethod(Opcodes.ACC_PUBLIC, name, descriptor, null, null);
    method.visitCode();
    var returned = new Label();
    method.visitVarInsn(Opcodes.ALOAD, 0);
    method.visitFieldInsn(Opcodes.GETFIELD, owner, "calls", "I");
    method.visitInsn(Opcodes.ICONST_M1);
    method.visitJumpInsn(Opcodes.IF_ICMPEQ, returned);
    method.visitVarInsn(Opcodes.ALOAD, 0);
    method.visitInsn(Opcodes.DUP);
    method.visitFieldInsn(Opcodes.GETFIELD, owner, "calls", "I");
    method.visitInsn(Opcodes.ICONST_1);
    method.visitInsn(Opcodes.IADD);
    method.visitFieldInsn(Opcodes.PUTFIELD, owner, "calls", "I");
    method.visitLabel(returned);
    method.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
    method.visitInsn(Opcodes.RETURN);
    method.visitMaxs(3, signature.length + 2);
    method.visitEnd();
    writer.visitEnd();
    var transformer = new InputTransformer();
    byte[] modified = transformer.transform(null, null, owner, null, null, writer.toByteArray());
    assertNotNull(modified);
    assertNull(transformer.transform(null, null, owner, null, null, modified));
    Class<?> type = new Loader().define(modified);
    Object instance = type.getConstructor().newInstance();
    var callback = type.getMethod(name, signature);
    InputGate.isolation(true);
    try {
      callback.invoke(instance, press);
      assertEquals(0, type.getField("calls").getInt(instance));
      InputIsolation.dispatch(
          () -> {
            callback.invoke(instance, press);
            return null;
          });
      assertEquals(1, type.getField("calls").getInt(instance));
    } finally {
      InputGate.isolation(false);
    }
    callback.invoke(instance, release);
    callback.invoke(instance, press);
    assertEquals(2, type.getField("calls").getInt(instance));
    callback.invoke(instance, release);
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
