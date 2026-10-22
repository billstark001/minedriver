package example;

import io.github.billstark001.minedriver.api.DriverException;
import io.github.billstark001.minedriver.api.DriverExtension;
import io.github.billstark001.minedriver.api.ExtensionRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.loader.api.FabricLoader;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.api.v0.IrisApi;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.lwjgl.opengl.GL11;

/** Test-only Iris 1.11.4 adapter. No Iris contract is added to MineDriver itself. */
public final class IrisExtension implements DriverExtension {
  private List<Map<String, String>> lastError = List.of();
  private long reload;

  @Override
  public void register(ExtensionRegistry registry) {
    registry.register(
        "custom.iris.status",
        "Read actual Iris pipeline, configuration and the last reload error",
        true,
        (context, parameters) -> {
          if (!parameters.isEmpty()) throw invalid("status takes no parameters");
          return context.onClient(this::status);
        });
    registry.register(
        "custom.iris.select",
        "Select a fixture pack and reload Iris in this isolated client",
        false,
        (context, parameters) -> {
          if (!parameters.keySet().equals(Set.of("pack", "enabled"))
              || !(parameters.get("pack") instanceof String pack)
              || !(parameters.get("enabled") instanceof Boolean enabled)
              || !Set.of("", ShaderChecks.VALID, ShaderChecks.INVALID).contains(pack)
              || (enabled && pack.isEmpty())) {
            throw invalid("Requires a fixture pack name and boolean enabled");
          }
          // Iris 1.11.4 only stores errors when no player exists. Capture this reload's
          // actual logged throwable as well; do not consume Iris's error getter.
          try (var errors = new ReloadErrors()) {
            context.onClient(
                () -> {
                  lastError = List.of();
                  var config = Iris.getIrisConfig();
                  config.setShaderPackName(pack);
                  config.setShadersEnabled(enabled);
                  config.save();
                  reload++;
                  // Iris's native reload builds the GL pipeline on the render thread. No
                  // polling, frame waits or cross-thread waits run inside this callback.
                  Iris.reload();
                  lastError = errors.error.get();
                  return null;
                });
            return context.onClient(this::status);
          }
        });
  }

  private Map<String, Object> status() {
    var config = Iris.getIrisConfig();
    var pipeline = Iris.getPipelineManager().getPipelineNullable();
    var result = new LinkedHashMap<String, Object>();
    result.put("iris", version("iris"));
    result.put("sodium", version("sodium"));
    result.put("apiRevision", IrisApi.getInstance().getMinorApiRevision());
    result.put("configuredPack", config.getShaderPackName().orElse(""));
    result.put("configuredEnabled", config.areShadersEnabled());
    result.put("currentPack", Iris.getCurrentPackName());
    result.put("active", IrisApi.getInstance().isShaderPackInUse());
    result.put("fallback", Iris.isFallback());
    result.put("pipeline", pipeline == null ? null : pipeline.getClass().getName());
    result.put("reload", reload);
    result.put("error", lastError);
    result.put("gpuVendor", GL11.glGetString(GL11.GL_VENDOR));
    result.put("gpuRenderer", GL11.glGetString(GL11.GL_RENDERER));
    result.put("glVersion", GL11.glGetString(GL11.GL_VERSION));
    return result;
  }

  private static String version(String id) {
    return FabricLoader.getInstance()
        .getModContainer(id)
        .orElseThrow()
        .getMetadata()
        .getVersion()
        .getFriendlyString();
  }

  private static List<Map<String, String>> causes(Throwable error) {
    var result = new ArrayList<Map<String, String>>();
    for (Throwable cause = error; cause != null && result.size() < 8; cause = cause.getCause()) {
      result.add(
          Map.of(
              "type", cause.getClass().getName(), "message", String.valueOf(cause.getMessage())));
    }
    return List.copyOf(result);
  }

  private static DriverException invalid(String message) {
    return new DriverException("INVALID_PARAMETERS", message);
  }

  /** Bounded diagnostic values only; installed and removed within one select command. */
  private static final class ReloadErrors extends AbstractAppender implements AutoCloseable {
    private final Logger logger = (Logger) LogManager.getLogger("Iris");
    private final AtomicReference<List<Map<String, String>>> error =
        new AtomicReference<>(List.of());

    private ReloadErrors() {
      super("MineDriver-Iris-reload", null, null, true, Property.EMPTY_ARRAY);
      start();
      logger.addAppender(this);
    }

    @Override
    public void append(LogEvent event) {
      if (event.getLevel().isMoreSpecificThan(org.apache.logging.log4j.Level.ERROR)
          && event.getThrown() != null) {
        error.set(causes(event.getThrown()));
      }
    }

    @Override
    public void close() {
      logger.removeAppender(this);
      stop();
    }
  }
}
