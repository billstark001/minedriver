package io.github.billstark001.minedriver.gradle;

import java.util.ArrayList;
import java.util.List;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;

public final class MineDriverPlugin implements Plugin<Project> {
  @Override
  public void apply(Project project) {
    project.getPluginManager().apply(JavaPlugin.class);
    MineDriverExtension extension =
        project
            .getExtensions()
            .create("mineDriver", MineDriverExtension.class, project.getObjects());
    var artifacts =
        project
            .getTasks()
            .register(
                "mineDriverArtifacts",
                ExtractArtifacts.class,
                task -> {
                  task.setGroup("minedriver");
                  task.setDescription("Extract immutable development agent and API artifacts");
                  task.getAgentJar()
                      .set(
                          project
                              .getLayout()
                              .getBuildDirectory()
                              .file(
                                  "minedriver/artifacts/agent-"
                                      + ExtractArtifacts.hash("agent.jar")
                                      + ".jar"));
                  task.getApiJar()
                      .set(
                          project
                              .getLayout()
                              .getBuildDirectory()
                              .file(
                                  "minedriver/artifacts/api-"
                                      + ExtractArtifacts.hash("api.jar")
                                      + ".jar"));
                });
    var java = project.getExtensions().getByType(JavaPluginExtension.class);
    SourceSet main = java.getSourceSets().getByName("main");
    SourceSet scenarios = java.getSourceSets().create("minedriver");
    scenarios.setCompileClasspath(
        scenarios
            .getCompileClasspath()
            .plus(main.getCompileClasspath())
            .plus(main.getOutput())
            .plus(project.files(artifacts.flatMap(ExtractArtifacts::getApiJar))));
    scenarios.setRuntimeClasspath(scenarios.getRuntimeClasspath().plus(main.getRuntimeClasspath()));
    project
        .getTasks()
        .named(scenarios.getCompileJavaTaskName())
        .configure(task -> task.dependsOn(artifacts));
    var check = registerRun(project, "mineDriverCheck", "check", extension, artifacts, scenarios);
    var interactive =
        registerRun(project, "mineDriverRun", "interactive", extension, artifacts, scenarios);
    var matrix =
        project
            .getTasks()
            .register(
                "mineDriverMatrix",
                task -> {
                  task.setGroup("verification");
                  task.setDescription("Run all configured MineDriver profiles sequentially");
                });
    var framework =
        project
            .getTasks()
            .register(
                "mineDriverFrameworkCheck",
                FrameworkCheck.class,
                task -> {
                  task.setGroup("verification");
                  task.setDescription("Execute and verify native Fabric/NeoForge test frameworks");
                  task.getReports().from(extension.getFrameworkReports());
                  task.getSummary()
                      .set(
                          project
                              .getLayout()
                              .getBuildDirectory()
                              .file("reports/minedriver/framework-summary.json"));
                });
    var prepareFramework =
        project
            .getTasks()
            .register(
                "mineDriverPrepareFramework",
                PrepareFramework.class,
                before -> before.getReports().from(extension.getFrameworkReports()));
    project
        .getGradle()
        .projectsEvaluated(
            ignored -> {
              check.configure(task -> cloneRun(project, extension.getSourceRunTask().get(), task));
              interactive.configure(
                  task -> cloneRun(project, extension.getSourceRunTask().get(), task));
              TaskProvider<MineDriverRun> previous = null;
              for (RunProfile profile : extension.getProfiles()) {
                String name =
                    "mineDriver"
                        + Character.toUpperCase(profile.getName().charAt(0))
                        + profile.getName().substring(1)
                        + "Check";
                var run = registerRun(project, name, "check", extension, artifacts, scenarios);
                run.configure(
                    task -> {
                      task.getPlan().set(profile.getPlan().orElse(extension.getPlan()));
                      task.getScenarios()
                          .set(profile.getScenarios().orElse(extension.getScenarios()));
                    });
                run.configure(
                    task ->
                        cloneRun(
                            project,
                            profile
                                .getSourceRunTask()
                                .getOrElse(extension.getSourceRunTask().get()),
                            task));
                if (previous != null) {
                  var predecessor = previous;
                  run.configure(task -> task.mustRunAfter(predecessor));
                }
                previous = run;
                matrix.configure(task -> task.dependsOn(run));
              }
              for (String name : extension.getFrameworkTasks().get()) {
                var nativeTask = project.getTasks().named(name);
                nativeTask.configure(
                    run -> {
                      run.dependsOn(prepareFramework);
                      run.getOutputs().upToDateWhen(unused -> false);
                    });
                framework.configure(task -> task.dependsOn(nativeTask));
              }
            });
  }

  private static TaskProvider<MineDriverRun> registerRun(
      Project project,
      String name,
      String mode,
      MineDriverExtension extension,
      TaskProvider<ExtractArtifacts> artifacts,
      SourceSet scenarios) {
    return project
        .getTasks()
        .register(
            name,
            MineDriverRun.class,
            task -> {
              task.setGroup(mode.equals("check") ? "verification" : "minedriver");
              task.setDescription("Launch an isolated " + mode + " MineDriver client");
              task.dependsOn(artifacts, project.getTasks().named(scenarios.getClassesTaskName()));
              task.getAgentJar().set(artifacts.flatMap(ExtractArtifacts::getAgentJar));
              task.getPlan().set(extension.getPlan());
              task.getMode().set(mode);
              task.getScenarios().set(extension.getScenarios());
              task.getExtensionClasses().set(extension.getExtensionClasses());
              task.getDisabledModIds().set(extension.getDisabledModIds());
              task.getTimeoutMillis()
                  .set(
                      mode.equals("interactive")
                          ? project.provider(() -> 0L)
                          : extension.getTimeoutMillis());
              task.getOperationTimeoutMillis().set(extension.getOperationTimeoutMillis());
              task.getAllowProduction().set(extension.getAllowProduction());
              task.getAllowUnsupportedRuntime().set(extension.getAllowUnsupportedRuntime());
              task.getAllowStartupWarnings().set(extension.getAllowStartupWarnings());
              task.getUnsafeReflection().set(extension.getUnsafeReflection());
              task.getScenarioClasspath().from(scenarios.getRuntimeClasspath());
              task.getSeedFiles().from(extension.getSeedFiles());
              task.getSessionRoot()
                  .set(project.getLayout().getBuildDirectory().dir("minedriver/runs/" + name));
              task.getReportRoot()
                  .set(project.getLayout().getBuildDirectory().dir("reports/minedriver/" + name));
              task.getOutputs().upToDateWhen(unused -> false);
            });
  }

  private static void cloneRun(Project project, String name, MineDriverRun target) {
    Task raw = project.getTasks().findByPath(name);
    if (!(raw instanceof JavaExec source))
      throw new GradleException(
          "MineDriver sourceRunTask must name a JavaExec client task: " + name);
    if (source instanceof MineDriverRun)
      throw new GradleException("MineDriver cannot clone another MineDriver task");
    target.dependsOn(source.getDependsOn());
    target.setClasspath(source.getClasspath());
    LoaderLaunchAdapters.copy(source, target);
    target.getMainClass().set(source.getMainClass());
    target.getMainModule().set(source.getMainModule());
    target.getJavaLauncher().set(source.getJavaLauncher());
    target.setEnvironment(source.getEnvironment());
    var arguments =
        new ArrayList<>(source.getArgs() == null ? List.<String>of() : source.getArgs());
    source
        .getArgumentProviders()
        .forEach(provider -> provider.asArguments().forEach(arguments::add));
    target.setArgs(arguments);
    // Snapshot providers while configuring, avoiding retained upstream Task/Project references.
    var jvm = new ArrayList<>(source.getAllJvmArgs());
    source
        .getJvmArgumentProviders()
        .forEach(
            provider -> {
              var supplied = new ArrayList<String>();
              provider.asArguments().forEach(supplied::add);
              if (!jvm.containsAll(supplied)) jvm.addAll(supplied);
            });
    target.setJvmArgs(jvm);
    target.getModularity().getInferModulePath().set(source.getModularity().getInferModulePath());
    // Loom prepares environment in its exec override. Its public run model is the adapter boundary.
    if (source.getClass().getName().contains("net.fabricmc.loom")
        || project.getExtensions().findByName("loom") != null) {
      target.notCompatibleWithConfigurationCache(
          "Loom run providers are evaluated during configuration; use native Loom for cached builds");
    }
  }
}
