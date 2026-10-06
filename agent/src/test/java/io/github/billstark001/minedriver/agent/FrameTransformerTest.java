package io.github.billstark001.minedriver.agent;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;

class FrameTransformerTest {
  private byte[] minecraft(String... methods) {
    var writer = new ClassWriter(0);
    writer.visit(
        Opcodes.V17,
        Opcodes.ACC_PUBLIC,
        "net/minecraft/client/Minecraft",
        null,
        "java/lang/Object",
        null);
    for (String name : methods) {
      var method = writer.visitMethod(Opcodes.ACC_PUBLIC, name, "(Z)V", null, null);
      method.visitCode();
      method.visitInsn(Opcodes.RETURN);
      method.visitMaxs(0, 2);
      method.visitEnd();
    }
    writer.visitEnd();
    return writer.toByteArray();
  }

  @Test
  void choosesRenderFrameOnceAndTransformationIsIdempotent() {
    var transformer = new FrameTransformer();
    byte[] result =
        transformer.transform(
            null,
            null,
            "net/minecraft/client/Minecraft",
            null,
            null,
            minecraft("runTick", "renderFrame"));
    assertNotNull(result);
    assertTrue(transformer.installed());
    var calls = new java.util.ArrayList<String>();
    new ClassReader(result)
        .accept(
            new ClassVisitor(Opcodes.ASM9) {
              @Override
              public MethodVisitor visitMethod(
                  int access,
                  String name,
                  String descriptor,
                  String signature,
                  String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                  @Override
                  public void visitMethodInsn(
                      int opcode, String owner, String method, String desc, boolean itf) {
                    if (owner.endsWith("/FrameClock")) calls.add(name + ":" + method);
                  }
                };
              }
            },
            0);
    assertEquals(java.util.List.of("renderFrame:begin", "renderFrame:end"), calls);
    assertNull(
        transformer.transform(null, null, "net/minecraft/client/Minecraft", null, null, result));
  }

  @Test
  void unknownSignatureIsAReportedCapabilityFailure() {
    var transformer = new FrameTransformer();
    assertNull(
        transformer.transform(
            null, null, "net/minecraft/client/Minecraft", null, null, minecraft("unknown")));
    assertFalse(transformer.installed());
    assertTrue(transformer.failure().contains("found 0"));
  }
}
