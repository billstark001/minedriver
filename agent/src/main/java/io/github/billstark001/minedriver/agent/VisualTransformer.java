package io.github.billstark001.minedriver.agent;

import io.github.billstark001.minedriver.hooks.VisualHooks;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.Set;
import org.objectweb.asm.*;

final class VisualTransformer implements ClassFileTransformer {
  private static final String HOOK = "io/github/billstark001/minedriver/hooks/VisualHooks";
  static final Set<String> TARGETS =
      Set.of(
          "net/minecraft/client/renderer/state/gui/GuiTextRenderState",
          "net/minecraft/client/renderer/state/gui/GuiItemRenderState");

  @Override
  public byte[] transform(
      Module module,
      ClassLoader loader,
      String name,
      Class<?> type,
      ProtectionDomain domain,
      byte[] bytes) {
    if (!TARGETS.contains(name)) return null;
    try {
      var reader = new ClassReader(bytes);
      boolean[] installed = {false};
      reader.accept(
          new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                int access,
                String method,
                String descriptor,
                String signature,
                String[] exceptions) {
              return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public void visitMethodInsn(
                    int opcode, String owner, String called, String desc, boolean itf) {
                  if (owner.equals(HOOK)) installed[0] = true;
                }
              };
            }
          },
          ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
      if (installed[0]) {
        VisualHooks.installed(name);
        return null;
      }
      var writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
      int[] count = {0};
      reader.accept(
          new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(
                int access,
                String method,
                String descriptor,
                String signature,
                String[] exceptions) {
              MethodVisitor next =
                  super.visitMethod(access, method, descriptor, signature, exceptions);
              if (!method.equals("bounds")
                  || !descriptor.equals("()Lnet/minecraft/client/gui/navigation/ScreenRectangle;"))
                return next;
              count[0]++;
              return new MethodVisitor(Opcodes.ASM9, next) {
                @Override
                public void visitInsn(int opcode) {
                  if (opcode == Opcodes.ARETURN) {
                    super.visitInsn(Opcodes.DUP);
                    super.visitVarInsn(Opcodes.ALOAD, 0);
                    super.visitInsn(Opcodes.SWAP);
                    super.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        HOOK,
                        "capture",
                        "(Ljava/lang/Object;Ljava/lang/Object;)V",
                        false);
                  }
                  super.visitInsn(opcode);
                }
              };
            }
          },
          0);
      if (count[0] != 1) {
        VisualHooks.failure(name + ": expected bounds method, found " + count[0]);
        return null;
      }
      VisualHooks.installed(name);
      return writer.toByteArray();
    } catch (RuntimeException failure) {
      VisualHooks.failure(name + ": " + failure);
      return null;
    }
  }
}
