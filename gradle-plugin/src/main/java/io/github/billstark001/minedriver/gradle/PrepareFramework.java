package io.github.billstark001.minedriver.gradle;

import java.nio.file.Files;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

@DisableCachingByDefault(
    because =
        "Removes only explicitly configured native report files before fresh framework execution")
public abstract class PrepareFramework extends DefaultTask {
  @Internal
  public abstract ConfigurableFileCollection getReports();

  @TaskAction
  public void prepare() throws Exception {
    for (java.io.File file : getReports()) {
      if (file.isDirectory())
        throw new org.gradle.api.GradleException(
            "Configure exact native report files, not directories: " + file);
      Files.deleteIfExists(file.toPath());
    }
  }
}
