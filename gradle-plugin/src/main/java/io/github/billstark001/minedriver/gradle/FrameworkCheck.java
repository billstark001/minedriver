package io.github.billstark001.minedriver.gradle;

import io.github.billstark001.minedriver.protocol.Json;
import java.io.File;
import java.util.ArrayList;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/** Consumes native Fabric/NeoForge/JUnit framework XML; does not replace native test lifecycles. */
@DisableCachingByDefault(because = "Verifies native test report freshness")
public abstract class FrameworkCheck extends DefaultTask {
  @InputFiles
  @PathSensitive(PathSensitivity.RELATIVE)
  public abstract ConfigurableFileCollection getReports();

  @OutputFile
  public abstract RegularFileProperty getSummary();

  @TaskAction
  public void verify() throws Exception {
    var factory = DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    long tests = 0, failed = 0, skipped = 0;
    var files = new ArrayList<String>();
    for (File report : getReports()) {
      if (!report.isFile()) throw new GradleException("Native framework report missing: " + report);
      var document = factory.newDocumentBuilder().parse(report);
      var cases = document.getElementsByTagName("testcase");
      for (int i = 0; i < cases.getLength(); i++) {
        var element = (org.w3c.dom.Element) cases.item(i);
        tests++;
        if (element.getElementsByTagName("failure").getLength() > 0
            || element.getElementsByTagName("error").getLength() > 0) failed++;
        if (element.getElementsByTagName("skipped").getLength() > 0) skipped++;
      }
      files.add(report.getAbsolutePath());
    }
    Json.write(
        getSummary().get().getAsFile().toPath(),
        Map.of(
            "protocol", 1, "tests", tests, "failed", failed, "skipped", skipped, "reports", files));
    if (tests == 0 || tests == skipped || failed > 0)
      throw new GradleException(
          "Native framework: "
              + tests
              + " tests, "
              + failed
              + " failures, "
              + skipped
              + " skipped");
    getLogger().lifecycle("MineDriver native framework: {} tests, {} skipped", tests, skipped);
  }
}
