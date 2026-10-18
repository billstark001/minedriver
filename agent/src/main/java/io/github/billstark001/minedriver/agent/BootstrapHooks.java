package io.github.billstark001.minedriver.agent;

import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
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
    RuntimeNames names = RuntimeNames.current();
    instrumentation.addTransformer(adapt(transformer, names), true);
    instrumentation.addTransformer(adapt(new InputTransformer(), names), true);
    instrumentation.addTransformer(adapt(new VisualTransformer(), names), true);
    instrumentation.addTransformer(adapt(new LegacyVisualTransformer(), names), true);
    for (Class<?> type : instrumentation.getAllLoadedClasses()) {
      if (MappedTransformer.target(type)) {
        try {
          instrumentation.retransformClasses(type);
        } catch (java.lang.instrument.UnmodifiableClassException error) {
          throw new IOException("Minecraft cannot be retransformed in this runtime", error);
        }
      }
    }
    return transformer;
  }

  private static ClassFileTransformer adapt(ClassFileTransformer transformer, RuntimeNames names) {
    return names.mapped() ? new MappedTransformer(transformer, names) : transformer;
  }
}
