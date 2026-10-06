package io.github.billstark001.minedriver.gradle;

import org.gradle.api.Named;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

public final class RunProfile implements Named {
  private final String name;
  private final Property<String> sourceRunTask;
  private final RegularFileProperty plan;
  private final ListProperty<String> scenarios;

  public RunProfile(String name, ObjectFactory objects) {
    if (!name.matches("[a-zA-Z][a-zA-Z0-9]*"))
      throw new IllegalArgumentException("Profile name must be alphanumeric");
    this.name = name;
    sourceRunTask = objects.property(String.class);
    plan = objects.fileProperty();
    scenarios = objects.listProperty(String.class);
  }

  @Override
  public String getName() {
    return name;
  }

  public Property<String> getSourceRunTask() {
    return sourceRunTask;
  }

  public RegularFileProperty getPlan() {
    return plan;
  }

  public ListProperty<String> getScenarios() {
    return scenarios;
  }
}
