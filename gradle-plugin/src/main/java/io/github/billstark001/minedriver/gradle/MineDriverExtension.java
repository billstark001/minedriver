package io.github.billstark001.minedriver.gradle;

import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

/** Public configuration shared by interactive/check runs; profiles override launch and plan. */
public class MineDriverExtension {
  private final Property<String> sourceRunTask;
  private final RegularFileProperty plan;
  private final ListProperty<String> scenarios;
  private final ListProperty<String> extensions;
  private final ListProperty<String> disabledModIds;
  private final Property<Long> timeoutMillis;
  private final Property<Long> operationTimeoutMillis;
  private final Property<Boolean> allowProduction;
  private final Property<Boolean> allowUnsupportedRuntime;
  private final ListProperty<String> acceptedStartupWarnings;
  private final Property<Boolean> unsafeReflection;
  private final Property<Boolean> inputIsolation;
  private final Property<Boolean> backgroundExecution;
  private final ConfigurableFileCollection seedFiles;
  private final NamedDomainObjectContainer<RunProfile> profiles;
  private final ListProperty<String> frameworkTasks;
  private final ConfigurableFileCollection frameworkReports;

  public MineDriverExtension(ObjectFactory objects) {
    sourceRunTask = objects.property(String.class).convention("runClient");
    plan = objects.fileProperty();
    scenarios = objects.listProperty(String.class).convention(java.util.List.of());
    extensions = objects.listProperty(String.class).convention(java.util.List.of());
    disabledModIds = objects.listProperty(String.class).convention(java.util.List.of());
    timeoutMillis = objects.property(Long.class).convention(300_000L);
    operationTimeoutMillis = objects.property(Long.class).convention(30_000L);
    allowProduction = objects.property(Boolean.class).convention(false);
    allowUnsupportedRuntime = objects.property(Boolean.class).convention(false);
    acceptedStartupWarnings = objects.listProperty(String.class).convention(java.util.List.of());
    unsafeReflection = objects.property(Boolean.class).convention(false);
    inputIsolation = objects.property(Boolean.class).convention(true);
    backgroundExecution = objects.property(Boolean.class).convention(true);
    seedFiles = objects.fileCollection();
    profiles =
        objects.domainObjectContainer(RunProfile.class, name -> new RunProfile(name, objects));
    frameworkTasks = objects.listProperty(String.class).convention(java.util.List.of());
    frameworkReports = objects.fileCollection();
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

  public ListProperty<String> getExtensionClasses() {
    return extensions;
  }

  public ListProperty<String> getDisabledModIds() {
    return disabledModIds;
  }

  public Property<Long> getTimeoutMillis() {
    return timeoutMillis;
  }

  public Property<Long> getOperationTimeoutMillis() {
    return operationTimeoutMillis;
  }

  public Property<Boolean> getAllowProduction() {
    return allowProduction;
  }

  public Property<Boolean> getAllowUnsupportedRuntime() {
    return allowUnsupportedRuntime;
  }

  /** Exact {@code modId:translationKey} pairs; errors and unmatched warnings always block. */
  public ListProperty<String> getAcceptedStartupWarnings() {
    return acceptedStartupWarnings;
  }

  public Property<Boolean> getInputIsolation() {
    return inputIsolation;
  }

  public Property<Boolean> getBackgroundExecution() {
    return backgroundExecution;
  }

  public Property<Boolean> getUnsafeReflection() {
    return unsafeReflection;
  }

  public ConfigurableFileCollection getSeedFiles() {
    return seedFiles;
  }

  public NamedDomainObjectContainer<RunProfile> getProfiles() {
    return profiles;
  }

  public ListProperty<String> getFrameworkTasks() {
    return frameworkTasks;
  }

  public ConfigurableFileCollection getFrameworkReports() {
    return frameworkReports;
  }
}
