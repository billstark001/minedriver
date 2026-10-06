package net.minecraft;

/** Simulated API for process-level agent tests; this is not a Minecraft implementation. */
public final class SharedConstants {
  private SharedConstants() {}

  public record Version(String name) {}

  public static Version getCurrentVersion() {
    return new Version("26.3");
  }
}
