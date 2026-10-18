package io.github.billstark001.minedriver.agent;

import static org.junit.jupiter.api.Assertions.*;

import io.github.billstark001.minedriver.hooks.FrameClock;
import io.github.billstark001.minedriver.hooks.InputGate;
import io.github.billstark001.minedriver.protocol.Json;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.*;

class RuntimeNamesTest {
  @TempDir Path directory;

  private static final class Loader extends ClassLoader {
    Class<?> define(byte[] bytes) {
      return defineClass(null, bytes, 0, bytes.length);
    }
  }

  @AfterEach
  void restore() throws Exception {
    RuntimeNames.install(null);
    InputGate.key(87, false);
    InputGate.isolation(false);
  }

  private RuntimeNames mappings() throws Exception {
    Path file = directory.resolve("names.json");
    Json.write(
        file,
        Map.of(
            "format",
            1,
            "minecraft",
            "1.21.1",
            "namespace",
            "named",
            "classes",
            List.of(
                Map.of(
                    "source",
                    "net/minecraft/client/Minecraft",
                    "target",
                    "fixture/YarnClient",
                    "parents",
                    List.of("fixture/CanonicalBase"),
                    "fields",
                    List.of(List.of("value", "I", "number")),
                    "methods",
                    List.of(List.of("runTick", "(Z)V", "drawFrame"))),
                Map.of(
                    "source",
                    "fixture/CanonicalBase",
                    "target",
                    "fixture/YarnBase",
                    "parents",
                    List.of(),
                    "fields",
                    List.of(),
                    "methods",
                    List.of(List.of("getCount", "()I", "count"))),
                Map.of(
                    "source",
                    "com/mojang/blaze3d/platform/InputConstants",
                    "target",
                    "fixture/YarnInput",
                    "parents",
                    List.of(),
                    "fields",
                    List.of(),
                    "methods",
                    List.of(List.of("isKeyDown", "(JI)Z", "poll"))))));
    RuntimeNames.install(file);
    return RuntimeNames.current();
  }

  @Test
  void canonicalReflectionAndFrameHooksPreserveTheRuntimeClassAndNames() throws Exception {
    RuntimeNames names = mappings();
    var loader = new Loader();
    var base = new ClassWriter(0);
    base.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "fixture/YarnBase", null, "java/lang/Object", null);
    constructor(base, "java/lang/Object");
    var count = base.visitMethod(Opcodes.ACC_PUBLIC, "count", "()I", null, null);
    count.visitCode();
    count.visitIntInsn(Opcodes.BIPUSH, 7);
    count.visitInsn(Opcodes.IRETURN);
    count.visitMaxs(1, 1);
    count.visitEnd();
    base.visitEnd();
    loader.define(base.toByteArray());
    var client = new ClassWriter(0);
    client.visit(
        Opcodes.V17, Opcodes.ACC_PUBLIC, "fixture/YarnClient", null, "fixture/YarnBase", null);
    constructor(client, "fixture/YarnBase");
    client.visitField(Opcodes.ACC_PUBLIC, "number", "I", null, null).visitEnd();
    var frame = client.visitMethod(Opcodes.ACC_PUBLIC, "drawFrame", "(Z)V", null, null);
    frame.visitCode();
    frame.visitInsn(Opcodes.RETURN);
    frame.visitMaxs(0, 2);
    frame.visitEnd();
    client.visitEnd();
    var transformer = new MappedTransformer(new FrameTransformer(), names);
    assertNull(transformer.transform(null, loader, null, null, null, client.toByteArray()));
    byte[] modified =
        transformer.transform(null, loader, "fixture/YarnClient", null, null, client.toByteArray());
    assertNotNull(modified);
    assertNull(transformer.transform(null, loader, "fixture/YarnClient", null, null, modified));
    Class<?> type = loader.define(modified);
    assertEquals("fixture.YarnClient", type.getName());
    assertSame(type, Reflect.type(loader, "net.minecraft.client.Minecraft"));
    Object instance = type.getConstructor().newInstance();
    Reflect.set(instance, "value", 11);
    assertEquals(11, Reflect.field(instance, "value"));
    assertEquals("value", Reflect.fieldName(type.getDeclaredField("number")));
    assertEquals("net.minecraft.client.Minecraft", Reflect.className(type));
    assertEquals(7, Reflect.call(instance, "getCount"));
    assertEquals(
        "count",
        names.remapper(true).mapMethodName("net/minecraft/client/Minecraft", "getCount", "()I"));
    long before = FrameClock.count();
    Reflect.callback(instance, "runTick", new Class<?>[] {boolean.class}, true);
    assertEquals(before + 1, FrameClock.count());

    // Mods implement mapped game methods without having entries in the game's mapping tree.
    var mod = new ClassWriter(0);
    mod.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "fixture/ModClient", null, "fixture/YarnBase", null);
    constructor(mod, "fixture/YarnBase");
    var override = mod.visitMethod(Opcodes.ACC_PUBLIC, "count", "()I", null, null);
    override.visitCode();
    override.visitIntInsn(Opcodes.BIPUSH, 9);
    override.visitInsn(Opcodes.IRETURN);
    override.visitMaxs(1, 1);
    override.visitEnd();
    mod.visitEnd();
    Object modClient = loader.define(mod.toByteArray()).getConstructor().newInstance();
    assertEquals(9, Reflect.call(modClient, "getCount"));
    assertEquals("fixture.ModClient", Reflect.className(modClient.getClass()));
  }

  @Test
  void mappedPollingRetainsLongHandleSlotsAndVirtualInput() throws Exception {
    var names = mappings();
    var writer = new ClassWriter(0);
    writer.visit(
        Opcodes.V17, Opcodes.ACC_PUBLIC, "fixture/YarnInput", null, "java/lang/Object", null);
    var poll =
        writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "poll", "(JI)Z", null, null);
    poll.visitCode();
    poll.visitInsn(Opcodes.ICONST_0);
    poll.visitInsn(Opcodes.IRETURN);
    poll.visitMaxs(1, 3);
    poll.visitEnd();
    writer.visitEnd();
    var transformer = new MappedTransformer(new InputTransformer(), names);
    byte[] transformed =
        transformer.transform(null, null, "fixture/YarnInput", null, null, writer.toByteArray());
    var method = new Loader().define(transformed).getMethod("poll", long.class, int.class);
    InputGate.isolation(true);
    assertEquals(false, method.invoke(null, 0x100000001L, 87));
    InputGate.key(87, true);
    assertEquals(true, method.invoke(null, 0x100000001L, 87));
    InputGate.key(87, false);
    assertEquals(false, method.invoke(null, 0x100000001L, 87));
  }

  private static void constructor(ClassWriter writer, String parent) {
    var method = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
    method.visitCode();
    method.visitVarInsn(Opcodes.ALOAD, 0);
    method.visitMethodInsn(Opcodes.INVOKESPECIAL, parent, "<init>", "()V", false);
    method.visitInsn(Opcodes.RETURN);
    method.visitMaxs(1, 1);
    method.visitEnd();
  }
}
