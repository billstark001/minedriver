package io.github.billstark001.minedriver.agent;

import io.github.billstark001.minedriver.hooks.VisualHooks;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.HashSet;
import java.util.Set;
import org.objectweb.asm.*;

/** 1.21.1 immediate GUI rendering has no GuiTextRenderState/GuiItemRenderState. */
final class LegacyVisualTransformer implements ClassFileTransformer {
  private static final String HOOK = "io/github/billstark001/minedriver/hooks/VisualHooks";
  private static final String GRAPHICS = "net/minecraft/client/gui/GuiGraphics";

  @Override
  public byte[] transform(
      Module module,
      ClassLoader loader,
      String name,
      Class<?> type,
      ProtectionDomain domain,
      byte[] bytes) {
    if (!GRAPHICS.equals(name)) return null;
    try {
      var reader = new ClassReader(bytes);
      // The legacy descriptor disappeared when the extracted GUI renderer was introduced.
      // Only instrument the exact 1.21.1 contracts, even though GuiGraphics still exists.
      boolean[] legacy = {false};
      Set<String> installed = new HashSet<>();
      reader.accept(
          new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                int access, String method, String desc, String signature, String[] exceptions) {
              if (item(method, desc)) legacy[0] = true;
              return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public void visitMethodInsn(
                    int opcode, String owner, String called, String descriptor, boolean itf) {
                  if (HOOK.equals(owner)) installed.add(method + desc);
                }
              };
            }
          },
          ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
      if (!legacy[0]) return null;
      var writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
      int[] changes = {0};
      reader.accept(
          new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(
                int access, String method, String desc, String signature, String[] exceptions) {
              MethodVisitor next = super.visitMethod(access, method, desc, signature, exceptions);
              boolean text =
                  method.equals("drawString")
                      && Set.of(
                              "(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I",
                              "(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)I")
                          .contains(desc);
              if (!text && !item(method, desc)) return next;
              VisualHooks.installed("legacy-gui-" + (text ? "text" : "item"));
              if (installed.contains(method + desc)) return next;
              changes[0]++;
              return new MethodVisitor(Opcodes.ASM9, next) {
                @Override
                public void visitCode() {
                  super.visitCode();
                  super.visitVarInsn(Opcodes.ALOAD, 0);
                  if (text) super.visitVarInsn(Opcodes.ALOAD, 1);
                  else super.visitInsn(Opcodes.ACONST_NULL);
                  super.visitVarInsn(Opcodes.ALOAD, text ? 2 : 3);
                  super.visitVarInsn(Opcodes.ILOAD, text ? 3 : 4);
                  super.visitVarInsn(Opcodes.ILOAD, text ? 4 : 5);
                  super.visitMethodInsn(
                      Opcodes.INVOKESTATIC,
                      HOOK,
                      "legacy",
                      "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;II)V",
                      false);
                }
              };
            }
          },
          0);
      return changes[0] == 0 ? null : writer.toByteArray();
    } catch (RuntimeException error) {
      VisualHooks.failure("legacy-gui: " + error);
      return null;
    }
  }

  private static boolean item(String method, String desc) {
    return method.equals("renderItem")
        && desc.equals(
            "(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;IIII)V");
  }
}
