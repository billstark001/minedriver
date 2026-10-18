package io.github.billstark001.minedriver.agent;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;

/** A canonical view for our hooks; the JVM always receives the original runtime namespace. */
final class MappedTransformer implements ClassFileTransformer {
  private static final Set<String> TARGETS =
      Set.of(
          "net/minecraft/client/Minecraft",
          "net/minecraft/client/KeyboardHandler",
          "net/minecraft/client/MouseHandler",
          "com/mojang/blaze3d/platform/InputConstants",
          "com/mojang/blaze3d/platform/Window",
          "com/mojang/blaze3d/platform/SDLEventHandler",
          "net/minecraft/client/renderer/state/gui/GuiTextRenderState",
          "net/minecraft/client/renderer/state/gui/GuiItemRenderState",
          "net/minecraft/client/gui/render/state/GuiTextRenderState",
          "net/minecraft/client/gui/render/state/GuiItemRenderState",
          "net/minecraft/client/gui/GuiGraphics");
  private final ClassFileTransformer delegate;
  private final RuntimeNames names;

  MappedTransformer(ClassFileTransformer delegate, RuntimeNames names) {
    this.delegate = delegate;
    this.names = names;
  }

  @Override
  public byte[] transform(
      Module module,
      ClassLoader loader,
      String name,
      Class<?> type,
      ProtectionDomain domain,
      byte[] bytes)
      throws IllegalClassFormatException {
    String canonical = names.canonical(name);
    if (canonical == null || !TARGETS.contains(canonical)) return null;
    byte[] input = names.mapped() ? remap(bytes, false) : bytes;
    byte[] output = delegate.transform(module, loader, canonical, type, domain, input);
    return output == null ? null : names.mapped() ? remap(output, true) : output;
  }

  private byte[] remap(byte[] bytes, boolean toRuntime) {
    var reader = new ClassReader(bytes);
    var writer = new ClassWriter(0);
    reader.accept(new ClassRemapper(writer, names.remapper(toRuntime)), 0);
    return writer.toByteArray();
  }

  static boolean target(Class<?> type) {
    return TARGETS.contains(RuntimeNames.current().canonical(type.getName().replace('.', '/')));
  }
}
