package io.github.billstark001.minedriver.gradle;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginFunctionalTest {
  @TempDir Path temporary;

  private GradleRunner fixture(String steps) throws Exception {
    Files.writeString(
        temporary.resolve("settings.gradle"), "rootProject.name = 'driver-fixture'\n");
    String support = System.getProperty("minedriver.testSupportJar").replace('\\', '/');
    Files.writeString(
        temporary.resolve("build.gradle"),
        """
      plugins { id 'io.github.billstark001.minedriver' }
      tasks.register('runClient', JavaExec) {
        classpath = files('%s')
        mainClass = 'net.minecraft.client.Minecraft'
      }
      mineDriver {
        plan.set(layout.projectDirectory.file('plan.json'))
        allowProduction.set(true)
        timeoutMillis.set(15000L)
        operationTimeoutMillis.set(2000L)
      }
      """
            .formatted(support));
    Files.writeString(temporary.resolve("plan.json"), "{\"steps\":" + steps + "}");
    return GradleRunner.create()
        .withProjectDir(temporary.toFile())
        .withPluginClasspath()
        .withArguments("mineDriverCheck", "--stacktrace", "--max-workers=2");
  }

  @Test
  void agentProcessInputFramesScreenshotsAndStructuredSuccess() throws Exception {
    var result =
        fixture(
                """
      [
       {"command":"assert.widget","parameters":{"selector":{"key":"menu.options"}}},
       {"command":"input.click","parameters":{"selector":{"key":"menu.options"}}},
       {"command":"assert.state","parameters":{"path":"screen","equals":"net.minecraft.client.gui.screens.options.OptionsScreen"}},
       {"command":"screenshot.capture","parameters":{"name":"options"}},
       {"command":"input.key","parameters":{"key":"ESCAPE"}},
       {"command":"assert.state","parameters":{"path":"screen","equals":"net.minecraft.client.gui.screens.TitleScreen"}}
      ]
      """)
            .build();
    assertEquals(TaskOutcome.SUCCESS, result.task(":mineDriverCheck").getOutcome());
    try (var paths = Files.walk(temporary.resolve("build/reports/minedriver/mineDriverCheck"))) {
      assertTrue(paths.anyMatch(path -> path.endsWith("options.png")));
    }
    assertNull(result.task(":runClient"), "Source run task must not launch a second client");
  }

  @Test
  void zeroClientExitDoesNotHideScenarioFailure() throws Exception {
    var result =
        fixture(
                "[{\"command\":\"assert.state\",\"parameters\":{\"path\":\"screen\",\"equals\":\"wrong\"}}]")
            .buildAndFail();
    assertTrue(result.getOutput().contains("ASSERTION_FAILED"));
    assertEquals(TaskOutcome.FAILED, result.task(":mineDriverCheck").getOutcome());
  }

  @Test
  void scenariosStayOutOfMainJarAndConfigurationCacheCanBeReused() throws Exception {
    var runner = fixture("[{\"command\":\"session.inspect\"}]");
    Path scenario = temporary.resolve("src/minedriver/java/example/Separate.java");
    Files.createDirectories(scenario.getParent());
    Files.writeString(scenario, "package example; public class Separate {}\n");
    runner
        .withArguments("jar", "minedriverClasses", "--configuration-cache", "--max-workers=2")
        .build();
    var second = runner.build();
    assertTrue(second.getOutput().contains("Reusing configuration cache"));
    try (var jar =
        new java.util.jar.JarFile(temporary.resolve("build/libs/driver-fixture.jar").toFile())) {
      assertNull(jar.getEntry("example/Separate.class"));
      assertNull(jar.getEntry("fabric.mod.json"));
    }
  }

  @Test
  void asynchronousScenarioFailureFailsInteractiveSession() throws Exception {
    var runner = fixture("[{\"command\":\"session.inspect\"}]");
    Path scenario = temporary.resolve("src/minedriver/java/example/Fail.java");
    Files.createDirectories(scenario.getParent());
    Files.writeString(
        scenario,
        "package example; import io.github.billstark001.minedriver.api.*; public class Fail implements Scenario { public void run(ProbeContext c) { c.require(false, \"async failure\"); } }");
    Files.writeString(
        temporary.resolve("build.gradle"),
        "\ntasks.named('mineDriverRun') { timeoutMillis.set(20000L) }\n",
        java.nio.file.StandardOpenOption.APPEND);
    var worker = java.util.concurrent.Executors.newSingleThreadExecutor();
    try {
      var build =
          worker.submit(
              () -> runner.withArguments("mineDriverRun", "--max-workers=2").buildAndFail());
      long deadline = System.nanoTime() + java.time.Duration.ofSeconds(40).toNanos();
      io.github.billstark001.minedriver.protocol.RpcClient rpc = null;
      while (rpc == null) {
        Path pointer = temporary.resolve("build/reports/minedriver/mineDriverRun/latest.json");
        if (Files.exists(pointer)) {
          Path connection =
              Path.of(
                  io.github.billstark001.minedriver.protocol.Json.read(pointer)
                      .getAsJsonObject()
                      .get("connectionFile")
                      .getAsString());
          if (Files.exists(connection)) {
            var candidate =
                new io.github.billstark001.minedriver.protocol.RpcClient(
                    io.github.billstark001.minedriver.protocol.Connection.read(connection));
            if (candidate
                .call("agent.status", java.util.Map.of())
                .getAsJsonObject()
                .get("state")
                .getAsString()
                .equals("READY")) rpc = candidate;
          }
        }
        if (System.nanoTime() > deadline) fail("Interactive agent did not become ready");
        Thread.sleep(50);
      }
      rpc.call("scenario.run", java.util.Map.of("class", "example.Fail"));
      while (!rpc.call("scenario.status", java.util.Map.of())
          .getAsJsonObject()
          .get("status")
          .getAsString()
          .equals("FAIL")) {
        if (System.nanoTime() > deadline) fail("Scenario failure was not reported");
        Thread.sleep(50);
      }
      rpc.call("session.close", java.util.Map.of());
      assertTrue(
          build
              .get(40, java.util.concurrent.TimeUnit.SECONDS)
              .getOutput()
              .contains("SCENARIO_FAILED"));
    } finally {
      worker.shutdownNow();
    }
  }

  @Test
  void matrixProfilesHaveIndependentSessions() throws Exception {
    var runner = fixture("[{\"command\":\"session.inspect\"}]");
    Files.writeString(
        temporary.resolve("build.gradle"),
        """
      mineDriver.profiles {
        create('one') { plan.set(layout.projectDirectory.file('plan.json')) }
        create('two') { plan.set(layout.projectDirectory.file('plan.json')) }
      }
      """,
        java.nio.file.StandardOpenOption.APPEND);
    var result = runner.withArguments("mineDriverMatrix", "--max-workers=2").build();
    assertEquals(TaskOutcome.SUCCESS, result.task(":mineDriverOneCheck").getOutcome());
    assertEquals(TaskOutcome.SUCCESS, result.task(":mineDriverTwoCheck").getOutcome());
    var a =
        io.github.billstark001.minedriver.protocol.Json.read(
            temporary.resolve("build/reports/minedriver/mineDriverOneCheck/latest.json"));
    var b =
        io.github.billstark001.minedriver.protocol.Json.read(
            temporary.resolve("build/reports/minedriver/mineDriverTwoCheck/latest.json"));
    assertNotEquals(a.getAsJsonObject().get("runId"), b.getAsJsonObject().get("runId"));
  }

  @Test
  void freshNativeReportReplacesStalePassAndAllSkippedFails() throws Exception {
    var runner = fixture("[{\"command\":\"session.inspect\"}]");
    Files.writeString(
        temporary.resolve("build.gradle"),
        """
      tasks.register('nativeTest') {
        def report = layout.buildDirectory.file('native.xml')
        outputs.file(report)
        doLast {
          def skipped = providers.gradleProperty('skip').getOrElse('false') == 'true' ? '<skipped/>' : ''
          report.get().asFile.text = '<testsuite><testcase name="native">' + skipped + '</testcase></testsuite>'
        }
      }
      mineDriver {
        frameworkTasks.add('nativeTest')
        frameworkReports.from(layout.buildDirectory.file('native.xml'))
        extensionClasses.set([])
      }
      """,
        java.nio.file.StandardOpenOption.APPEND);
    runner.withArguments("mineDriverFrameworkCheck", "--max-workers=2").build();
    var failed =
        runner
            .withArguments("mineDriverFrameworkCheck", "-Pskip=true", "--max-workers=2")
            .buildAndFail();
    assertEquals(TaskOutcome.SUCCESS, failed.task(":nativeTest").getOutcome());
    assertTrue(failed.getOutput().contains("1 tests, 0 failures, 1 skipped"));
  }
}
