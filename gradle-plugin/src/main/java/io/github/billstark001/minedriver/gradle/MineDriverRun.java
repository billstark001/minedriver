package io.github.billstark001.minedriver.gradle;

import io.github.billstark001.minedriver.protocol.Json;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.work.DisableCachingByDefault;

/** A fresh, owned JavaExec launch. Success requires an authenticated run-id completion marker. */
@DisableCachingByDefault(because = "Runs a live Minecraft client and always creates a new session")
public abstract class MineDriverRun extends JavaExec {
  @InputFile
  @PathSensitive(PathSensitivity.NONE)
  public abstract RegularFileProperty getAgentJar();

  @Optional
  @InputFile
  @PathSensitive(PathSensitivity.RELATIVE)
  public abstract RegularFileProperty getPlan();

  @Input
  public abstract Property<String> getMode();

  @Input
  public abstract ListProperty<String> getScenarios();

  @Input
  public abstract ListProperty<String> getExtensionClasses();

  @Input
  public abstract ListProperty<String> getDisabledModIds();

  @Input
  public abstract Property<Long> getTimeoutMillis();

  @Input
  public abstract Property<Long> getOperationTimeoutMillis();

  @Input
  public abstract Property<Boolean> getAllowProduction();

  @Input
  public abstract Property<Boolean> getAllowUnsupportedRuntime();

  @Input
  public abstract Property<Boolean> getAllowStartupWarnings();

  @Input
  public abstract Property<Boolean> getUnsafeReflection();

  @InputFiles
  @PathSensitive(PathSensitivity.RELATIVE)
  public abstract ConfigurableFileCollection getScenarioClasspath();

  @InputFiles
  @PathSensitive(PathSensitivity.RELATIVE)
  public abstract ConfigurableFileCollection getSeedFiles();

  @Internal
  public abstract DirectoryProperty getSessionRoot();

  @Internal
  public abstract DirectoryProperty getReportRoot();

  @Override
  public void exec() {
    String runId = Instant.now().toEpochMilli() + "-" + UUID.randomUUID();
    Path output = getReportRoot().get().getAsFile().toPath().resolve(runId);
    Path run = getSessionRoot().get().getAsFile().toPath().resolve(runId);
    try {
      Files.createDirectories(output);
      Files.createDirectories(run);
      seed(run);
      var config = new LinkedHashMap<String, Object>();
      config.put("protocol", 1);
      config.put("runId", runId);
      config.put("mode", getMode().get());
      config.put("outputDirectory", output.toAbsolutePath().toString());
      config.put("runDirectory", run.toAbsolutePath().toString());
      config.put("timeoutMillis", getTimeoutMillis().get());
      config.put("operationTimeoutMillis", getOperationTimeoutMillis().get());
      config.put("allowProduction", getAllowProduction().get());
      config.put("allowUnsupportedRuntime", getAllowUnsupportedRuntime().get());
      config.put("allowStartupWarnings", getAllowStartupWarnings().get());
      config.put("unsafeReflection", getUnsafeReflection().get());
      config.put("scenarios", getScenarios().get());
      config.put("extensions", getExtensionClasses().get());
      config.put(
          "scenarioClasspath",
          getScenarioClasspath().getFiles().stream().map(java.io.File::getAbsolutePath).toList());
      config.put(
          "plan",
          getPlan().isPresent()
              ? Json.read(getPlan().get().getAsFile().toPath())
              : java.util.Map.of());
      if (getMode().get().equals("check")
          && getScenarios().get().isEmpty()
          && (!getPlan().isPresent()
              || !Json.read(getPlan().get().getAsFile().toPath()).getAsJsonObject().has("steps")
              || Json.read(getPlan().get().getAsFile().toPath())
                  .getAsJsonObject()
                  .getAsJsonArray("steps")
                  .isEmpty()))
        throw new GradleException(
            "Configure mineDriver.plan or mineDriver.scenarios; an empty check is not a test");
      Path configFile = output.resolve("config.json");
      Json.write(configFile, config);
      Json.write(
          getReportRoot().get().getAsFile().toPath().resolve("latest.json"),
          java.util.Map.of(
              "runId",
              runId,
              "directory",
              output.toAbsolutePath().toString(),
              "connectionFile",
              output.resolve("connection.json").toAbsolutePath().toString()));
      setWorkingDir(run.toFile());
      List<String> args = new ArrayList<>(getArgs() == null ? List.of() : getArgs());
      for (int i = 0; i < args.size(); i++)
        if (args.get(i).equals("--gameDir")) {
          if (i + 1 >= args.size()) throw new GradleException("Invalid upstream --gameDir");
          args.set(++i, run.toAbsolutePath().toString());
        }
      if (!args.contains("--gameDir"))
        args.addAll(List.of("--gameDir", run.toAbsolutePath().toString()));
      setArgs(args);
      if (getMode().get().equals("check")) systemProperty("fabric.noGui", "true");
      jvmArgs(
          "-javaagent:"
              + getAgentJar().get().getAsFile().getAbsolutePath()
              + "="
              + configFile.toAbsolutePath());
      if (!getDisabledModIds().get().isEmpty())
        systemProperty("fabric.debug.disableModIds", String.join(",", getDisabledModIds().get()));
      getLogger()
          .lifecycle(
              "MineDriver session: {}\nConnection: {}", output, output.resolve("connection.json"));
      try (OutputStream log = Files.newOutputStream(output.resolve("client.log"))) {
        setStandardOutput(new Tee(System.out, log));
        setErrorOutput(new Tee(System.err, log));
        // Verify structured results even when the game returns zero; nonzero also remains a
        // failure.
        setIgnoreExitValue(true);
        super.exec();
        ResultVerifier.verify(output.resolve("result.json"), runId);
        if (getExecutionResult().get().getExitValue() != 0)
          throw new GradleException(
              "MineDriver client exit "
                  + getExecutionResult().get().getExitValue()
                  + "; reports: "
                  + output);
      }
    } catch (IOException error) {
      throw new GradleException("MineDriver session setup/report failed: " + output, error);
    }
  }

  private void seed(Path run) throws IOException {
    for (java.io.File file : getSeedFiles()) {
      Path source = file.toPath().toAbsolutePath().normalize();
      if (!Files.exists(source)) throw new IOException("Seed file missing: " + source);
      if (Files.isSymbolicLink(source))
        throw new IOException("Seed symlinks are not supported: " + source);
      Path target = run.resolve(source.getFileName());
      if (Files.isDirectory(source)) {
        try (var paths = Files.walk(source)) {
          for (Path child : paths.toList()) {
            if (Files.isSymbolicLink(child))
              throw new IOException("Seed symlinks are not supported: " + child);
            Path destination = target.resolve(source.relativize(child));
            if (Files.isDirectory(child)) Files.createDirectories(destination);
            else Files.copy(child, destination);
          }
        }
      } else Files.copy(source, target);
    }
  }

  private static final class Tee extends OutputStream {
    private final OutputStream console;
    private final OutputStream log;

    Tee(OutputStream console, OutputStream log) {
      this.console = console;
      this.log = log;
    }

    @Override
    public synchronized void write(int value) throws IOException {
      console.write(value);
      log.write(value);
    }

    @Override
    public synchronized void write(byte[] bytes, int offset, int count) throws IOException {
      console.write(bytes, offset, count);
      log.write(bytes, offset, count);
    }

    @Override
    public synchronized void flush() throws IOException {
      console.flush();
      log.flush();
    }
  }
}
