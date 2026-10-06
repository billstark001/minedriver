package io.github.billstark001.minedriver.agent;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;

final class BootstrapHooks {
  private BootstrapHooks() {}

  static FrameTransformer install(Instrumentation instrumentation, Path output) throws IOException {
    Files.createDirectories(output);
    Path jar = output.resolve("bootstrap-hooks.jar");
    try (var input = BootstrapHooks.class.getResourceAsStream("bootstrap-hooks.jar")) {
      if (input == null) throw new IOException("MineDriver bootstrap hooks artifact is missing");
      Files.copy(input, jar);
    }
    instrumentation.appendToBootstrapClassLoaderSearch(new JarFile(jar.toFile()));
    var transformer = new FrameTransformer();
    instrumentation.addTransformer(transformer, true);
    for (Class<?> type : instrumentation.getAllLoadedClasses()) {
      if (type.getName().equals("net.minecraft.client.Minecraft")) {
        try {
          instrumentation.retransformClasses(type);
        } catch (java.lang.instrument.UnmodifiableClassException error) {
          throw new IOException("Minecraft cannot be retransformed in this runtime", error);
        }
      }
    }
    return transformer;
  }
}
