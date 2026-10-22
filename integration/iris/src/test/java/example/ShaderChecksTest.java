package example;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.billstark001.minedriver.api.Driver;
import io.github.billstark001.minedriver.api.DriverException;
import io.github.billstark001.minedriver.api.ProbeContext;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Exercises the actual scenario helpers without starting a game or invoking native callbacks. */
final class ShaderChecksTest {
  @TempDir Path output;

  static Stream<Arguments> partialMarkers() {
    return Stream.of(
        Arguments.of(72, 71), // Only one sampled magenta row is missing: 98.61% remains.
        Arguments.of(71, 72),
        Arguments.of(72, 0),
        Arguments.of(0, 72),
        Arguments.of(2, 0), // Even a small residue must fail independently in each region.
        Arguments.of(0, 2));
  }

  @ParameterizedTest(name = "reject remaining green rows={0}, magenta rows={1}")
  @MethodSource("partialMarkers")
  void partialMarkerIsNotAbsence(int greenRows, int magentaRows) throws Exception {
    var driver = imageDriver(greenRows, magentaRows);
    DriverException failure =
        assertThrows(
            DriverException.class,
            () -> ShaderChecks.capture(driver.context(), "partial-marker", false));
    assertEquals("ASSERTION_FAILED", failure.code());
  }

  @Test
  void emptyRegionsAreAbsent() throws Exception {
    var driver = imageDriver(0, 0);
    var evidence = ShaderChecks.capture(driver.context(), "absent-marker", false);
    assertEquals(0.0, evidence.get("greenFraction"));
    assertEquals(0.0, evidence.get("magentaFraction"));
  }

  @Test
  void completeMarkerIsPresent() throws Exception {
    var driver = imageDriver(72, 72);
    var evidence = ShaderChecks.capture(driver.context(), "present-marker", true);
    assertEquals(1.0, evidence.get("greenFraction"));
    assertEquals(1.0, evidence.get("magentaFraction"));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void bothRestoreFailuresAreRetained(boolean useErrors) {
    var driver = new TestDriver(output);
    Throwable shader =
        useErrors ? new AssertionError("shader") : new IllegalStateException("shader");
    Throwable hud = useErrors ? new AssertionError("hud") : new IllegalStateException("hud");
    driver.shaderFailure = shader;
    driver.hudFailure = hud;
    var restore = ShaderChecks.restoreAfter(driver.context());

    Throwable failure = assertThrows(Throwable.class, restore::close);
    assertSame(shader, failure);
    assertArrayEquals(new Throwable[] {hud}, failure.getSuppressed());
    assertEquals(List.of("shader", "hud"), driver.cleanupAttempts);
    assertFalse(Files.exists(output.resolve("restored.json")));
  }

  @Test
  void sceneFailureStaysPrimaryWithBothCleanupFailuresAttached() {
    var driver = new TestDriver(output);
    var shader = new IllegalStateException("shader");
    var hud = new IllegalStateException("hud");
    var scene = new DriverException("ASSERTION_FAILED", "scene");
    driver.shaderFailure = shader;
    driver.hudFailure = hud;

    DriverException failure =
        assertThrows(
            DriverException.class,
            () -> {
              try (var restore = ShaderChecks.restoreAfter(driver.context())) {
                throw scene;
              }
            });
    assertSame(scene, failure);
    assertArrayEquals(new Throwable[] {shader}, failure.getSuppressed());
    assertArrayEquals(new Throwable[] {hud}, shader.getSuppressed());
    assertEquals(List.of("shader", "hud"), driver.cleanupAttempts);
    assertFalse(Files.exists(output.resolve("restored.json")));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void oneRestoreFailureStillAttemptsBothSteps(boolean shaderFails) {
    var driver = new TestDriver(output);
    var original = new IllegalStateException(shaderFails ? "shader" : "hud");
    if (shaderFails) driver.shaderFailure = original;
    else driver.hudFailure = original;

    var restore = ShaderChecks.restoreAfter(driver.context());
    assertSame(original, assertThrows(IllegalStateException.class, restore::close));
    assertEquals(List.of("shader", "hud"), driver.cleanupAttempts);
    assertEquals(0, original.getSuppressed().length);
    assertFalse(Files.exists(output.resolve("restored.json")));
  }

  private TestDriver imageDriver(int greenRows, int magentaRows) throws Exception {
    var image = new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB);
    // Fixed regions of the fixture's 640x480 final-pass marker.
    for (int x = 480; x < 576; x++) {
      for (int y = 72; y < 72 + greenRows; y++) image.setRGB(x, y, 0x20c060);
      for (int y = 312; y < 312 + magentaRows; y++) image.setRGB(x, y, 0xe020c0);
    }
    var driver = new TestDriver(output);
    driver.image = output.resolve("capture.png");
    ImageIO.write(image, "png", driver.image.toFile());
    return driver;
  }

  private static final class TestDriver implements Driver {
    private final Path output;
    private final List<String> cleanupAttempts = new ArrayList<>();
    private Throwable shaderFailure;
    private Throwable hudFailure;
    private Path image;
    private int clientCalls;

    private TestDriver(Path output) {
      this.output = output;
    }

    private ProbeContext context() {
      return new ProbeContext(this, Duration.ofSeconds(1));
    }

    @Override
    public Object call(String command, Map<String, ?> parameters) {
      return switch (command) {
        case "custom.iris.status" ->
            Map.of("configuredPack", "", "configuredEnabled", false, "active", false);
        case "screenshot.capture" -> Map.of("path", image.toString());
        case "custom.iris.select" -> {
          cleanupAttempts.add("shader");
          failIfRequested(shaderFailure);
          yield Map.of();
        }
        default -> throw new AssertionError("Unexpected command: " + command);
      };
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T onClient(Callable<T> action, Duration timeout) {
      if (++clientCalls > 1) {
        cleanupAttempts.add("hud");
        failIfRequested(hudFailure);
      }
      // The first call reads the initial HUD flag; later calls exercise cleanup.
      // Native game callbacks are deliberately never executed by this test double.
      return (T) Boolean.FALSE;
    }

    private static void failIfRequested(Throwable failure) {
      if (failure instanceof RuntimeException exception) throw exception;
      if (failure instanceof Error error) throw error;
    }

    @Override
    public <T> T onServer(Callable<T> action, Duration timeout) {
      throw new AssertionError("Unexpected server callback");
    }

    @Override
    public void await(String description, Duration timeout, BooleanSupplier condition) {
      throw new AssertionError("Unexpected wait");
    }

    @Override
    public Path outputDirectory() {
      return output;
    }

    @Override
    public boolean isGameThread() {
      return false;
    }
  }
}
