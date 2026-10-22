package example;

import com.google.gson.GsonBuilder;
import io.github.billstark001.minedriver.api.ProbeContext;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;

/** Ordinary scenario helpers, deliberately outside the public driver API. */
final class ShaderChecks {
  static final String VALID = "minedriver-valid";
  static final String INVALID = "minedriver-invalid";
  static final String COMPILE_MARKER = "MINEDRIVER_EXPECTED_COMPILE_FAILURE";

  private ShaderChecks() {}

  static Restore restoreAfter(ProbeContext context) {
    return new Restore(context);
  }

  static void hideHud(ProbeContext context) {
    context.onClient(
        () -> {
          var hud = Minecraft.getInstance().gui.hud;
          if (!hud.isHidden()) hud.toggle();
          return null;
        });
  }

  /** Scope-owned cleanup preserves the primary assertion if restoration also fails. */
  static final class Restore implements AutoCloseable {
    private final ProbeContext context;
    private final Map<?, ?> original;
    private final boolean hidden;

    private Restore(ProbeContext context) {
      this.context = context;
      original = status(context);
      hidden = context.onClient(() -> Minecraft.getInstance().gui.hud.isHidden());
    }

    @Override
    public void close() throws Exception {
      Throwable failure = null;
      try {
        select(
            context,
            original.get("configuredPack").toString(),
            (Boolean) original.get("configuredEnabled"));
      } catch (RuntimeException | Error error) {
        failure = error;
      }
      try {
        context.onClient(
            () -> {
              var hud = Minecraft.getInstance().gui.hud;
              if (hud.isHidden() != hidden) hud.toggle();
              return null;
            });
      } catch (RuntimeException | Error error) {
        if (failure == null) failure = error;
        else if (failure != error) failure.addSuppressed(error);
      }
      if (failure instanceof RuntimeException exception) throw exception;
      if (failure instanceof Error error) throw error;
      Map<?, ?> restored = status(context);
      context.require(
          original.get("configuredPack").equals(restored.get("configuredPack"))
              && original.get("configuredEnabled").equals(restored.get("configuredEnabled"))
              && original.get("active").equals(restored.get("active")),
          "Restore the original shader policy and activation: " + restored);
      context.require(
          context.onClient(() -> Minecraft.getInstance().gui.hud.isHidden()) == hidden,
          "Restore original HUD visibility");
      write(context, "restored.json", Map.of("iris", restored, "hudHidden", hidden));
    }
  }

  static Map<?, ?> status(ProbeContext context) {
    return (Map<?, ?>) context.call("custom.iris.status");
  }

  static void select(ProbeContext context, String pack, boolean enabled) {
    context.call("custom.iris.select", Map.of("pack", pack, "enabled", enabled));
  }

  static void requireActive(ProbeContext context, String pack) {
    Map<?, ?> state = status(context);
    context.require(
        Boolean.TRUE.equals(state.get("configuredEnabled"))
            && pack.equals(state.get("configuredPack"))
            && pack.equals(state.get("currentPack"))
            && Boolean.TRUE.equals(state.get("active"))
            && Boolean.FALSE.equals(state.get("fallback"))
            && ((List<?>) state.get("error")).isEmpty(),
        "Requested shader pack must actually render without fallback or errors: " + state);
  }

  static void awaitActive(ProbeContext context, String pack) {
    context.await(
        "Iris uses " + pack,
        Duration.ofSeconds(20),
        () -> {
          Map<?, ?> state = status(context);
          context.require(((List<?>) state.get("error")).isEmpty(), "Iris reload failed: " + state);
          context.require(!Boolean.TRUE.equals(state.get("fallback")), "Iris fell back: " + state);
          return pack.equals(state.get("currentPack")) && Boolean.TRUE.equals(state.get("active"));
        });
    context.call("render.await", Map.of("frames", 8));
    requireActive(context, pack);
  }

  static void world(ProbeContext context) throws Exception {
    Map<?, ?> runtime = (Map<?, ?>) context.inspect();
    Map<?, ?> iris = status(context);
    context.require("26.2".equals(runtime.get("minecraft")), "Fixture requires Minecraft 26.2");
    context.require(
        "1.11.4+mc26.2".equals(iris.get("iris")), "Fixture requires Iris 1.11.4+mc26.2");
    context.require(
        "0.9.2+mc26.2".equals(iris.get("sodium")), "Fixture requires Sodium 0.9.2+mc26.2");
    context.require(
        !Boolean.TRUE.equals(iris.get("configuredEnabled")), "Seed starts with shaders off");
    context.call("window.configure", Map.of("width", 640, "height", 480, "guiScale", 2));
    writeInputs(context, runtime, iris);
    context.call("world.create", Map.of("flat", true, "seed", "734", "timeoutMillis", 180000));
  }

  static Map<String, Object> capture(ProbeContext context, String name, boolean expectedMarker)
      throws Exception {
    Map<?, ?> before = status(context);
    Map<?, ?> capture = (Map<?, ?>) context.screenshot(name);
    Map<?, ?> after = status(context);
    context.require(before.equals(after), "Iris state stays unchanged across capture");
    BufferedImage image = ImageIO.read(Path.of(capture.get("path").toString()).toFile());
    context.require(
        image != null && image.getWidth() == 640 && image.getHeight() == 480,
        "640x480 game-frame PNG");
    double green = markerFraction(image, 0.15, true);
    double magenta = markerFraction(image, 0.65, false);
    var evidence = new LinkedHashMap<String, Object>();
    evidence.put("state", after);
    evidence.put("capture", capture);
    evidence.put("greenFraction", green);
    evidence.put("magentaFraction", magenta);
    write(context, name + ".json", evidence);
    // Absence must reject partial/old marker pixels independently in both regions.
    boolean matches =
        expectedMarker ? green >= 0.99 && magenta >= 0.99 : green <= 0.01 && magenta <= 0.01;
    context.require(matches, "Final-pass marker expected=" + expectedMarker + ": " + evidence);
    return evidence;
  }

  private static double markerFraction(BufferedImage image, double top, boolean green) {
    int matched = 0, count = 0;
    for (int y = (int) (image.getHeight() * top);
        y < (int) (image.getHeight() * (top + 0.15));
        y++) {
      for (int x = (int) (image.getWidth() * 0.75); x < (int) (image.getWidth() * 0.9); x++) {
        int rgb = image.getRGB(x, y);
        int r = rgb >>> 16 & 255, g = rgb >>> 8 & 255, b = rgb & 255;
        if (green ? g > 150 && r < 100 && b < 140 : r > 150 && g < 100 && b > 100) matched++;
        count++;
      }
    }
    return (double) matched / count;
  }

  static void write(ProbeContext context, String name, Object value) throws Exception {
    Files.writeString(
        context.outputDirectory().resolve(name),
        new GsonBuilder().setPrettyPrinting().create().toJson(value));
  }

  private static void writeInputs(ProbeContext context, Map<?, ?> runtime, Map<?, ?> iris)
      throws Exception {
    Path directory = context.onClient(() -> Minecraft.getInstance().gameDirectory.toPath());
    var inputs = new ArrayList<Map<String, String>>();
    for (String child : List.of("mods", "shaderpacks", "config")) {
      try (var paths = Files.walk(directory.resolve(child))) {
        for (Path file : paths.filter(Files::isRegularFile).sorted().toList()) {
          inputs.add(
              Map.of(
                  "file",
                  directory.relativize(file).toString().replace('\\', '/'),
                  "sha256",
                  HexFormat.of()
                      .formatHex(
                          MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)))));
        }
      }
    }
    write(context, "shader-inputs.json", Map.of("runtime", runtime, "iris", iris, "files", inputs));
  }
}
