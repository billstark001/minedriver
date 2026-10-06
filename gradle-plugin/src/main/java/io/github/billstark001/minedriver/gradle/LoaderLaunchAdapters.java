package io.github.billstark001.minedriver.gradle;

import java.lang.reflect.Method;
import java.util.Map;
import org.gradle.api.GradleException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.JavaExec;

/** Explicit adapters for launch-time fields which upstream JavaExec subclasses apply in exec(). */
final class LoaderLaunchAdapters {
  private LoaderLaunchAdapters() {}

  static void copy(JavaExec source, MineDriverRun target) {
    if (source.getClass().getName().startsWith("net.neoforged.moddevgradle.internal.RunGameTask")) {
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
