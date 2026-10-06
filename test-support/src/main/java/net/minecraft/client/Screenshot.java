package net.minecraft.client;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.function.Consumer;
import javax.imageio.ImageIO;

public final class Screenshot {
  private Screenshot() {}

  public static void grab(
      File output, String name, Object target, int scale, Consumer<Object> callback) {
    try {
      var directory = output.toPath().resolve("screenshots");
      Files.createDirectories(directory);
      ImageIO.write(
          new BufferedImage(320, 240, BufferedImage.TYPE_INT_RGB),
          "png",
          directory.resolve(name).toFile());
      callback.accept(new net.minecraft.client.gui.screens.Screen.Component("screenshot.success"));
    } catch (java.io.IOException failure) {
      throw new java.io.UncheckedIOException(failure);
    }
  }
}
