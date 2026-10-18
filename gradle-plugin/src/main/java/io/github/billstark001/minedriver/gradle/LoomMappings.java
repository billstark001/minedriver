package io.github.billstark001.minedriver.gradle;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;

/** The public mappings file plus the pinned Loom 1.17 metadata/client-provider contract. */
final class LoomMappings {
  private LoomMappings() {}

  static void configure(Project project, MineDriverRun run) {
    TaskProvider<PrepareMappings> task = prepare(project);
    if (task == null) return;
    run.dependsOn(task);
    run.getRuntimeMappings().set(task.flatMap(PrepareMappings::getOutputFile));
  }

  static TaskProvider<PrepareMappings> prepare(Project project) {
    Object loom = project.getExtensions().findByName("loom");
    if (loom == null) return null;
    String version = ((Provider<?>) call(loom, "getMinecraftVersion")).get().toString();
    if (!List.of("1.21.1", "1.21.11").contains(version)) return null;
    TaskProvider<PrepareMappings> task;
    if (project.getTasks().findByName("mineDriverMappings") != null)
      task = project.getTasks().named("mineDriverMappings", PrepareMappings.class);
    else {
      Object provider = call(loom, "getMinecraftProvider");
      Object info = call(provider, "getVersionInfo");
      Object download = call(info, "download", "client_mappings");
      File tiny = (File) call(loom, "getMappingsFile");
      File client = (File) call(provider, "getMinecraftClientJar");
      String url = call(download, "url").toString(), sha1 = call(download, "sha1").toString();
      task =
          project
              .getTasks()
              .register(
                  "mineDriverMappings",
                  PrepareMappings.class,
                  mapping -> {
                    mapping.setGroup("minedriver");
                    mapping.setDescription(
                        "Prepare names for the consuming Fabric development mappings");
                    mapping.getLoomMappings().set(tiny);
                    mapping.getClientJar().set(client);
                    mapping.getMinecraftVersion().set(version);
                    mapping.getMappingsUrl().set(url);
                    mapping.getMappingsSha1().set(sha1);
                    mapping.getOffline().set(project.getGradle().getStartParameter().isOffline());
                    mapping
                        .getMojangCache()
                        .set(
                            project
                                .getLayout()
                                .getBuildDirectory()
                                .file("minedriver/mappings/" + version + "-client.txt"));
                    mapping
                        .getOutputFile()
                        .set(
                            project
                                .getLayout()
                                .getBuildDirectory()
                                .file("minedriver/mappings/" + version + "-runtime.json"));
                  });
    }
    return task;
  }

  private static Object call(Object object, String name, Object... arguments) {
    try {
      var types = new Class<?>[arguments.length];
      java.util.Arrays.fill(types, String.class);
      var method = object.getClass().getMethod(name, types);
      if (!method.canAccess(object) && !method.trySetAccessible())
        throw new IllegalAccessException(name);
      return method.invoke(object, arguments);
    } catch (InvocationTargetException error) {
      throw new GradleException("Loom mapping contract failed: " + name, error.getCause());
    } catch (ReflectiveOperationException error) {
      throw new GradleException("Unsupported Loom mapping API: " + name, error);
    }
  }
}
