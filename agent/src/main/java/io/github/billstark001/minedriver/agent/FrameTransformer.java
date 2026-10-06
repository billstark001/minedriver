package io.github.billstark001.minedriver.agent;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.concurrent.atomic.AtomicBoolean;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * A bounded hook into the two explicitly supported frame-method names and their exact descriptor.
 */
final class FrameTransformer implements ClassFileTransformer {
  private static final String OWNER = "io/github/billstark001/minedriver/hooks/FrameClock";
  private final AtomicBoolean installed = new AtomicBoolean();
  private volatile String failure;

  @Override
  public byte[] transform(
      Module module,
      ClassLoader loader,
      String name,
      Class<?> redefined,
      ProtectionDomain protection,
      byte[] bytes) {
    if (!"net/minecraft/client/Minecraft".equals(name)) return null;
    try {
      var reader = new ClassReader(bytes);
      boolean[] already = {false};
      boolean[] renderFrame = {false};
      reader.accept(
          new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                int access,
                String method,
                String descriptor,
                String signature,
                String[] exceptions) {
              if (method.equals("renderFrame") && descriptor.equals("(Z)V")) renderFrame[0] = true;
              return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public void visitMethodInsn(
                    int opcode,
                    String owner,
                    String method,
                    String descriptor,
                    boolean isInterface) {
                  if (OWNER.equals(owner)) already[0] = true;
                }
              };
            }
          },
          ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
      if (already[0]) {
        installed.set(true);
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
              var next = super.visitMethod(access, method, descriptor, signature, exceptions);
              if (!method.equals(renderFrame[0] ? "renderFrame" : "runTick")
                  || !descriptor.equals("(Z)V")) return next;
              count[0]++;
              return new MethodVisitor(Opcodes.ASM9, next) {
                @Override
                public void visitCode() {
                  super.visitCode();
                  super.visitMethodInsn(Opcodes.INVOKESTATIC, OWNER, "begin", "()V", false);
                }

                @Override
                public void visitInsn(int opcode) {
                  if (opcode == Opcodes.RETURN)
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, OWNER, "end", "()V", false);
                  super.visitInsn(opcode);
                }
              };
            }
          },
          0);
      if (count[0] != 1) {
        failure = "Expected one supported frame method, found " + count[0];
        return null;
      }
      installed.set(true);
      return writer.toByteArray();
    } catch (RuntimeException error) {
      failure = error.toString();
      return null;
    }
  }

  boolean installed() {
    return installed.get();
  }

  String failure() {
    return failure;
  }
}
