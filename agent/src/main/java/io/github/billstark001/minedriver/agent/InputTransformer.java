package io.github.billstark001.minedriver.agent;

import io.github.billstark001.minedriver.hooks.InputGate;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.HashSet;
import java.util.Set;
import org.objectweb.asm.*;

/** Exact native signatures for the supported GLFW and SDL clients. */
final class InputTransformer implements ClassFileTransformer {
  private static final String GATE = "io/github/billstark001/minedriver/hooks/InputGate";
  private static final String INPUT = "com/mojang/blaze3d/platform/InputConstants";
  private static final String WINDOW = "com/mojang/blaze3d/platform/Window";
  private static final String KEYBOARD = "net/minecraft/client/KeyboardHandler";
  private static final String MOUSE = "net/minecraft/client/MouseHandler";
  private static final String SDL = "com/mojang/blaze3d/platform/SDLEventHandler";
  private static final String EVENT = "net/minecraft/client/input/KeyEvent";

  @Override
  public byte[] transform(
      Module module,
      ClassLoader loader,
      String name,
      Class<?> type,
      ProtectionDomain domain,
      byte[] bytes) {
    if (!Set.of(INPUT, WINDOW, KEYBOARD, MOUSE, SDL).contains(name)) return null;
    try {
      var reader = new ClassReader(bytes);
      Set<String> already = new HashSet<>();
      reader.accept(
          new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(
                int access, String method, String desc, String signature, String[] exceptions) {
              return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public void visitMethodInsn(
                    int opcode, String owner, String called, String descriptor, boolean itf) {
                  if (owner.equals(GATE)) already.add(method + desc);
                }
              };
            }
          },
          ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
      var writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
      int[] changes = {0};
      reader.accept(
          new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(
                int access, String method, String desc, String signature, String[] exceptions) {
              MethodVisitor next = super.visitMethod(access, method, desc, signature, exceptions);
              String hook = hook(name, method, desc);
              if (hook != null) InputGate.installed(hook);
              if (already.contains(method + desc)) return next;
              return new MethodVisitor(Opcodes.ASM9, next) {
                @Override
                public void visitInsn(int opcode) {
                  if ("poll".equals(hook) && opcode == Opcodes.IRETURN) {
                    super.visitVarInsn(Opcodes.ILOAD, desc.equals("(I)Z") ? 0 : 1);
                    super.visitInsn(Opcodes.SWAP);
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, GATE, "polled", "(IZ)Z", false);
                  }
                  super.visitInsn(opcode);
                }

                @Override
                public void visitCode() {
                  super.visitCode();
                  if (hook == null) return;
                  changes[0]++;
                  Label original = new Label();
                  if (hook.equals("key")) {
                    super.visitVarInsn(Opcodes.ALOAD, 4);
                    super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, EVENT, "key", "()I", false);
                    super.visitVarInsn(Opcodes.ILOAD, 3);
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, GATE, "blockKey", "(II)Z", false);
                  } else if (hook.equals("button")) {
                    super.visitVarInsn(Opcodes.ALOAD, 3);
                    super.visitMethodInsn(
                        Opcodes.INVOKEVIRTUAL,
                        "net/minecraft/client/input/MouseButtonInfo",
                        "button",
                        "()I",
                        false);
                    super.visitVarInsn(Opcodes.ILOAD, 4);
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, GATE, "blockMouse", "(II)Z", false);
                  } else {
                    String predicate =
                        hook.equals("focus")
                            ? "automation"
                            : Set.of("poll", "cursor", "grab", "release").contains(hook)
                                ? "isolated"
                                : "blockExternal";
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, GATE, predicate, "()Z", false);
                  }
                  super.visitJumpInsn(Opcodes.IFEQ, original);
                  if (hook.equals("poll")) {
                    super.visitVarInsn(Opcodes.ILOAD, desc.equals("(I)Z") ? 0 : 1);
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, GATE, "keyDown", "(I)Z", false);
                    super.visitInsn(Opcodes.IRETURN);
                  } else if (hook.equals("focus")) {
                    super.visitInsn(Opcodes.ICONST_1);
                    super.visitInsn(Opcodes.IRETURN);
                  } else super.visitInsn(Opcodes.RETURN);
                  super.visitLabel(original);
                  super.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                }

                @Override
                public void visitMethodInsn(
                    int opcode, String owner, String called, String descriptor, boolean itf) {
                  if ((name.equals(KEYBOARD) || name.equals(MOUSE) || name.equals(SDL))
                      && called.equals("execute")
                      && descriptor.equals("(Ljava/lang/Runnable;)V")) {
                    super.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        GATE,
                        "queued",
                        "(Ljava/lang/Runnable;)Ljava/lang/Runnable;",
                        false);
                    InputGate.installed("queue");
                    changes[0]++;
                  }
                  super.visitMethodInsn(opcode, owner, called, descriptor, itf);
                }
              };
            }
          },
          0);
      return changes[0] == 0 ? null : writer.toByteArray();
    } catch (RuntimeException failure) {
      InputGate.failure(name + ": " + failure);
      return null;
    }
  }

  private static String hook(String owner, String method, String descriptor) {
    if (owner.equals(INPUT)) {
      if (method.equals("isKeyDown") && Set.of("(I)Z", "(L" + WINDOW + ";I)Z").contains(descriptor))
        return "poll";
      if (method.equals("grabOrReleaseMouse") && descriptor.equals("(L" + WINDOW + ";IDD)V"))
        return "grab";
      if (method.equals("grabMouse") && descriptor.equals("(L" + WINDOW + ";DD)V")) return "grab";
      if (method.equals("releaseMouse") && descriptor.equals("(L" + WINDOW + ";DD)V"))
        return "release";
    }
    if (owner.equals(WINDOW) && method.equals("isFocused") && descriptor.equals("()Z"))
      return "focus";
    if (owner.equals(KEYBOARD)) {
      if (method.equals("keyPress") && descriptor.equals("(JIL" + EVENT + ";)V")) return "key";
      if (method.equals("charTyped")
          && descriptor.equals("(JLnet/minecraft/client/input/CharacterEvent;)V"))
        return "character";
      if (method.equals("textInput") && descriptor.equals("(JLjava/lang/String;)V")) return "text";
      if (Set.of("textEditing", "preeditCallback").contains(method)
          && descriptor.equals("(JLnet/minecraft/client/input/PreeditEvent;)V")) return "preedit";
    }
    if (owner.equals(MOUSE)) {
      if (method.equals("onButton")
          && descriptor.equals("(JLnet/minecraft/client/input/MouseButtonInfo;I)V"))
        return "button";
      if (method.equals("onScroll") && descriptor.equals("(JDD)V")) return "scroll";
      if (method.equals("onMove") && Set.of("(JDD)V", "(JDDDD)V").contains(descriptor))
        return "move";
      if (method.equals("onDrop")
          && Set.of("(JLjava/util/List;)V", "(JLjava/util/List;I)V").contains(descriptor))
        return "drop";
      if (method.equals("resyncMousePosition") && descriptor.equals("()V")) return "cursor";
    }
    return null;
  }
}
