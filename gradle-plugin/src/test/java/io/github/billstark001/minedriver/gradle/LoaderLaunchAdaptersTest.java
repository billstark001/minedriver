package io.github.billstark001.minedriver.gradle;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LoaderLaunchAdaptersTest {
  @TempDir Path directory;

  @Test
  void devLaunchProgramFilesExposeTheExistingGameDirectoryWithoutMutatingUpstreamFiles()
      throws Exception {
    Path program = directory.resolve("client args.txt");
    String content =
        "# launch\nexample.Main\n--gameDir\n\"original directory\"\n@nested.txt\n@@literal\n";
    Files.writeString(program, content);
    Files.writeString(
        directory.resolve("nested.txt"), "--assetsDir\n'asset directory'\n@nested.txt\n");
    assertEquals(
        List.of(
            "example.Main",
            "--gameDir",
            "original directory",
            "--assetsDir",
            "asset directory",
            "@@literal"),
        LoaderLaunchAdapters.arguments(
            "net.neoforged.devlaunch.Main", List.of("@" + program), directory));
    assertEquals(content, Files.readString(program));
  }

  @Test
  void otherLaunchersRetainTheirOwnArgumentFileSemantics() throws Exception {
    assertEquals(
        List.of("@loader-config.txt"),
        LoaderLaunchAdapters.arguments(
            "net.fabricmc.devlaunchinjector.Main", List.of("@loader-config.txt"), directory));
  }

  @Test
  void separateRootArgumentFilesEachExpandTheirSharedNestedFile() throws Exception {
    Files.writeString(directory.resolve("shared.txt"), "--shared value\n");
    Files.writeString(directory.resolve("first.txt"), "@shared.txt\n");
    Files.writeString(directory.resolve("second.txt"), "@shared.txt\n");
    assertEquals(
        List.of("--shared", "value", "--shared", "value"),
        LoaderLaunchAdapters.arguments(
            "net.neoforged.devlaunch.Main", List.of("@first.txt", "@second.txt"), directory));
  }
}
