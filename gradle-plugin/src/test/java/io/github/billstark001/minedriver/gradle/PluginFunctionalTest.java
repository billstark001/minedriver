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
}
