package io.github.billstark001.minedriver.gradle;

import static org.junit.jupiter.api.Assertions.*;

import io.github.billstark001.minedriver.protocol.Json;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

class RuntimeMappingsTest {
  @TempDir Path directory;

  @Test
  void joinsDescriptorsThroughOfficialNamesAndUsesTheOfflineVerifiedCache() throws Exception {
    Path tiny = directory.resolve("mappings.tiny"),
        mojang = directory.resolve("client.txt"),
        client = directory.resolve("client.jar");
    Files.writeString(
        tiny,
        """
        tiny\t2\t0\tofficial\tintermediary\tnamed
        c\ta\tclass_1\tyarn/Client
        \tf\tI\tb\tfield_1\tcounter
        \tm\t(Z)V\tc\tmethod_1\ttick
        c\td\tclass_2\tyarn/Base
        \tm\t(La;)Ld;\te\tmethod_2\tapply
        """);
    Files.writeString(
        mojang,
        """
        net.minecraft.client.Minecraft -> a:
            int value -> b
            void runTick(boolean) -> c
            fixture.Base transform(net.minecraft.client.Minecraft) -> e
        fixture.Base -> d:
            fixture.Base transform(net.minecraft.client.Minecraft) -> e
        """);
    var header = new ClassWriter(0);
    header.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "a", null, "d", null);
    header.visitEnd();
    try (var jar = new JarOutputStream(Files.newOutputStream(client))) {
      jar.putNextEntry(new JarEntry("a.class"));
      jar.write(header.toByteArray());
      jar.closeEntry();
    }
    var project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build();
    var task = project.getTasks().register("mapping", PrepareMappings.class).get();
    task.getLoomMappings().set(tiny.toFile());
    task.getClientJar().set(client.toFile());
    task.getMinecraftVersion().set("1.21.1");
    task.getMojangCache().set(mojang.toFile());
    task.getMappingsSha1()
        .set(
            HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(mojang))));
    task.getMappingsUrl().set("https://unused.invalid/client.txt");
    task.getOffline().set(true);
    Path output = directory.resolve("runtime.json");
    task.getOutputFile().set(output.toFile());
    task.prepare();
    var data = Json.read(output).getAsJsonObject();
    var type = data.getAsJsonArray("classes").get(0).getAsJsonObject();
    assertEquals("yarn/Client", type.get("target").getAsString());
    assertEquals("fixture/Base", type.getAsJsonArray("parents").get(0).getAsString());
    assertEquals(
        "counter", type.getAsJsonArray("fields").get(0).getAsJsonArray().get(2).getAsString());
    assertEquals(
        "(Z)V", type.getAsJsonArray("methods").get(0).getAsJsonArray().get(1).getAsString());
    assertEquals(
        "tick", type.getAsJsonArray("methods").get(0).getAsJsonArray().get(2).getAsString());
    var inherited = type.getAsJsonArray("methods").get(1).getAsJsonArray();
    assertEquals(
        "(Lnet/minecraft/client/Minecraft;)Lfixture/Base;", inherited.get(1).getAsString());
    assertEquals("apply", inherited.get(2).getAsString());
    Files.writeString(mojang, "tampered");
    assertThrows(java.io.IOException.class, task::prepare);
  }

  @Test
  void mojangNamedDevelopmentNeedsNoMappingDownload() throws Exception {
    Path tiny = directory.resolve("mojang.tiny");
    Files.writeString(tiny, "tiny\t2\t0\tofficial\tnamed\nc\ta\tnet/minecraft/client/Minecraft\n");
    var project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build();
    var task = project.getTasks().register("mapping", PrepareMappings.class).get();
    task.getLoomMappings().set(tiny.toFile());
    task.getMinecraftVersion().set("1.21.1");
    Path output = directory.resolve("identity.json");
    task.getOutputFile().set(output.toFile());
    task.prepare();
    var data = Json.read(output).getAsJsonObject();
    assertEquals("mojang", data.get("namespace").getAsString());
    assertTrue(data.getAsJsonArray("classes").isEmpty());
  }
}
