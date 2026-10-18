package io.github.billstark001.minedriver.gradle;

import java.io.IOException;
import java.io.StreamTokenizer;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.JavaExec;

/** Explicit adapters for launch-time fields which upstream JavaExec subclasses apply in exec(). */
final class LoaderLaunchAdapters {
  private LoaderLaunchAdapters() {}

  /** Expand DevLaunch's application @files before replacing --gameDir, preserving its syntax. */
  static List<String> arguments(String mainClass, List<String> arguments, Path directory)
      throws IOException {
    if (!mainClass.equals("net.neoforged.devlaunch.Main")) return new ArrayList<>(arguments);
    var expanded = new ArrayList<String>();
    for (String argument : arguments) expand(argument, directory, new HashSet<>(), expanded);
    // DevLaunch still runs; protect literal @ arguments from being expanded a second time.
    return new ArrayList<>(
        expanded.stream().map(value -> value.startsWith("@") ? "@" + value : value).toList());
  }

  private static void expand(
      String argument, Path directory, Set<Path> visited, List<String> output) throws IOException {
    if (!argument.startsWith("@") || argument.equals("@")) {
      output.add(argument);
      return;
    }
    if (argument.startsWith("@@")) {
      output.add(argument.substring(1));
      return;
    }
    Path file = directory.resolve(argument.substring(1)).toAbsolutePath().normalize();
    if (!Files.isReadable(file)) {
      output.add(argument);
      return;
    }
    if (!visited.add(file)) return;
    try (var reader = Files.newBufferedReader(file)) {
      var tokens = new StreamTokenizer(reader);
      tokens.resetSyntax();
      tokens.wordChars(32, 255);
      tokens.whitespaceChars(0, 32);
      tokens.quoteChar('"');
      tokens.quoteChar('\'');
      tokens.commentChar('#');
      while (tokens.nextToken() != StreamTokenizer.TT_EOF)
        expand(tokens.sval, directory, visited, output);
    }
  }

  static void copy(JavaExec source, MineDriverRun target) {
    if (source.getClass().getName().startsWith("net.neoforged.moddevgradle.internal.RunGameTask")) {
      target
          .getSourceWorkingDirectory()
          .set(((DirectoryProperty) property(source, "getGameDirectory")).get());
      target.classpath((FileCollection) property(source, "getClasspathProvider"));
      Object environment = ((Provider<?>) property(source, "getEnvironmentProperty")).get();
      if (environment instanceof Map<?, ?> values)
        values.forEach((key, value) -> target.environment(key.toString(), value));
    } else if (source.getClass().getName().startsWith("net.fabricmc.loom.task.")) {
      Object environment = ((Provider<?>) property(source, "getInternalEnvironmentVars")).get();
      if (environment instanceof Map<?, ?> values)
        values.forEach((key, value) -> target.environment(key.toString(), value));
      // MineDriver uses an ordinary JavaExec to retain its own lifecycle and result verification.
      // An outer xvfb-run works for both loaders without copying private launcher implementations.
      if (Boolean.TRUE.equals(((Provider<?>) property(source, "getUseXvfb")).get()))
        target
            .getLogger()
            .lifecycle(
                "MineDriver Linux client: launch Gradle under xvfb-run --auto-servernum when DISPLAY is absent");
    }
  }

  private static Object property(Object object, String name) {
    for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
      try {
        Method method = type.getDeclaredMethod(name);
        if (!method.trySetAccessible())
          throw new GradleException("Loader launch property is inaccessible: " + name);
        return method.invoke(object);
      } catch (NoSuchMethodException absent) {
        /* Continue to the declared upstream task class. */
      } catch (ReflectiveOperationException error) {
        throw new GradleException("Loader launch adapter failed for " + name, error);
      }
    }
    throw new GradleException(
        "Unsupported loader plugin launch API: " + object.getClass().getName() + "." + name);
  }
}
