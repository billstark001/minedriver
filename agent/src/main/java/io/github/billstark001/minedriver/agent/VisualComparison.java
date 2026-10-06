package io.github.billstark001.minedriver.agent;

import io.github.billstark001.minedriver.api.DriverException;
import io.github.billstark001.minedriver.protocol.Paths;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import javax.imageio.ImageIO;

final class VisualComparison {
  private VisualComparison() {}

  static Object compare(Map<String, Object> parameters, Path output) throws Exception {
    BufferedImage actual = read(Parameters.string(parameters, "actual", null));
    BufferedImage expected = read(Parameters.string(parameters, "expected", null));
    if (parameters.containsKey("region")) {
      Rectangle r = rectangle(Parameters.object(parameters, "region"));
      if (r.x < 0
          || r.y < 0
          || r.x + r.width > actual.getWidth()
          || r.y + r.height > actual.getHeight()
          || r.x + r.width > expected.getWidth()
          || r.y + r.height > expected.getHeight())
        throw Parameters.invalid("Region exceeds image bounds");
      actual = actual.getSubimage(r.x, r.y, r.width, r.height);
      expected = expected.getSubimage(r.x, r.y, r.width, r.height);
    }
    if (actual.getWidth() != expected.getWidth() || actual.getHeight() != expected.getHeight())
      throw new DriverException(
          "IMAGE_SIZE_MISMATCH",
          "Expected "
              + expected.getWidth()
              + "x"
              + expected.getHeight()
              + ", got "
              + actual.getWidth()
              + "x"
              + actual.getHeight());
    var masks = new ArrayList<Rectangle>();
    for (Object mask : Parameters.list(parameters, "masks"))
      masks.add(rectangle(Parameters.object(Map.of("mask", mask), "mask")));
    int tolerance = Parameters.integer(parameters, "channelTolerance", 0, 0, 255);
    double allowed = Parameters.decimal(parameters, "maxChangedFraction", 0);
    if (allowed < 0 || allowed > 1) throw Parameters.invalid("maxChangedFraction must be in 0..1");
    BufferedImage diff =
        new BufferedImage(actual.getWidth(), actual.getHeight(), BufferedImage.TYPE_INT_ARGB);
    long changed = 0;
    long compared = 0;
    for (int y = 0; y < actual.getHeight(); y++) {
      for (int x = 0; x < actual.getWidth(); x++) {
        boolean masked = false;
        for (Rectangle mask : masks)
          if (mask.contains(x, y)) {
            masked = true;
            break;
          }
        if (masked) {
          diff.setRGB(x, y, 0xff444444);
          continue;
        }
        compared++;
        int a = actual.getRGB(x, y), e = expected.getRGB(x, y);
        boolean mismatch = false;
        for (int shift : new int[] {0, 8, 16, 24})
          if (Math.abs((a >>> shift & 255) - (e >>> shift & 255)) > tolerance) mismatch = true;
        if (mismatch) changed++;
        diff.setRGB(x, y, mismatch ? 0xffff00ff : (a & 0x00ffffff) | 0x55000000);
      }
    }
    if (compared == 0) throw Parameters.invalid("Masks exclude every pixel");
    Path path =
        Paths.child(
            output.resolve("diffs"),
            Parameters.string(parameters, "name", "diff-" + System.nanoTime()) + ".png");
    Files.createDirectories(path.getParent());
    ImageIO.write(diff, "png", path.toFile());
    double fraction = (double) changed / compared;
    var result =
        Map.of(
            "changed",
            changed,
            "compared",
            compared,
            "changedFraction",
            fraction,
            "diff",
            path.toString(),
            "passed",
            fraction <= allowed);
    if (fraction > allowed) throw new DriverException("VISUAL_MISMATCH", result.toString());
    return result;
  }

  private static BufferedImage read(String file) throws Exception {
    var image = ImageIO.read(Path.of(file).toFile());
    if (image == null) throw Parameters.invalid("Not an image: " + file);
    return image;
  }

  private static Rectangle rectangle(Map<String, Object> p) {
    return new Rectangle(
        Parameters.integer(p, "x", 0, 0, 100000),
        Parameters.integer(p, "y", 0, 0, 100000),
        Parameters.integer(p, "width", 1, 1, 100000),
        Parameters.integer(p, "height", 1, 1, 100000));
  }
}
