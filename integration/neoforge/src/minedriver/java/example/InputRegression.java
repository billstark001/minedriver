package example;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.billstark001.minedriver.api.ProbeContext;
import io.github.billstark001.minedriver.api.Scenario;
import java.time.Duration;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;

public final class InputRegression implements Scenario {
  @Override
  public void run(ProbeContext context) throws Exception {
    boolean baseline = (Boolean) status(context).get("isolated");
    String first = acquire(context, 60_000), second = acquire(context, 60_000);
    context.call("input.isolation.release", Map.of("leaseId", first));
    context.require(
        (Boolean) status(context).get("isolated"), "A second lease keeps isolation active");
    context.call("input.isolation.renew", Map.of("leaseId", second, "ttlMillis", 60_000));
    context.call("input.click", Map.of("selector", Map.of("key", "menu.options")));
    context.call("render.await", Map.of("frames", 2));
    context.onClient(
        () -> {
          var client = Minecraft.getInstance();
          var method =
              client
                  .keyboardHandler
                  .getClass()
                  .getDeclaredMethod("keyPress", long.class, int.class, KeyEvent.class);
          method.setAccessible(true);
          method.invoke(
              client.keyboardHandler,
              client.getWindow().handle(),
              1,
              new KeyEvent(InputConstants.KEY_ESCAPE, 0, 0));
          method.invoke(
              client.keyboardHandler,
              client.getWindow().handle(),
              0,
              new KeyEvent(InputConstants.KEY_ESCAPE, 0, 0));
          return null;
        });
    context.call(
        "assert.state",
        Map.of(
            "path", "screen", "equals", "net.minecraft.client.gui.screens.options.OptionsScreen"));
    context.call("input.key", Map.of("key", "W", "action", "PRESS"));
    context.require(
        context.onClient(() -> polled(InputConstants.KEY_W)),
        "Virtual W must be visible to native polling");
    context.call("input.key", Map.of("key", "W", "action", "RELEASE"));
    context.require(
        !context.onClient(() -> polled(InputConstants.KEY_W)),
        "Released virtual W must not remain held");
    Map<?, ?> sizeBefore = (Map<?, ?>) context.call("window.configure");
    context.onClient(
        () -> {
          visibility(false);
          return null;
        });
    try {
      context.call(
          "wait.state", Map.of("path", "focused", "equals", false, "timeoutMillis", 10_000));
      context.call("input.key", Map.of("key", "ESCAPE"));
      context.call(
          "assert.state",
          Map.of("path", "screen", "equals", "net.minecraft.client.gui.screens.TitleScreen"));
      context.call("assert.state", Map.of("path", "focused", "equals", false));
      context.require(
          sizeBefore.equals(context.call("window.configure")),
          "Focus loss preserves window geometry");
      context.screenshot("background-focus");
    } finally {
      context.onClient(
          () -> {
            visibility(true);
            return null;
          });
    }
    context.require(
        sizeBefore.equals(context.call("window.configure")),
        "Focus recovery preserves window geometry");
    context.call("input.isolation.release", Map.of("leaseId", second));
    context.require(
        status(context).get("isolated").equals(baseline),
        "Releasing owned leases preserves session policy");
    if (!baseline) {
      acquire(context, 100);
      context.await(
          "input lease expiry",
          Duration.ofSeconds(5),
          () -> !(Boolean) status(context).get("isolated"));
      context.require(
          "INPUT_LEASE_EXPIRED".equals(status(context).get("expiredLease")),
          "Expiry must be observable");
      String recovered = acquire(context, 60_000);
      context.call("input.isolation.release", Map.of("leaseId", recovered));
    }
    context.require(
        ((Number) status(context).get("droppedEvents")).longValue() >= 2,
        "Injected external callbacks must be discarded");
  }

  private static Map<?, ?> status(ProbeContext context) {
    return (Map<?, ?>) context.call("input.isolation.status");
  }

  private static String acquire(ProbeContext context, int ttl) {
    return (String)
        ((Map<?, ?>) context.call("input.isolation.acquire", Map.of("ttlMillis", ttl)))
            .get("leaseId");
  }

  private static boolean sdl() {
    return net.minecraft.SharedConstants.getCurrentVersion().name().equals("26.3");
  }

  private static boolean polled(int key) throws Exception {
    if (sdl())
      return (Boolean) InputConstants.class.getMethod("isKeyDown", int.class).invoke(null, key);
    return (Boolean)
        InputConstants.class
            .getMethod("isKeyDown", Minecraft.getInstance().getWindow().getClass(), int.class)
            .invoke(null, Minecraft.getInstance().getWindow(), key);
  }

  static void visibility(boolean visible) throws Exception {
    long handle = Minecraft.getInstance().getWindow().handle();
    Class<?> api = Class.forName(sdl() ? "org.lwjgl.sdl.SDLVideo" : "org.lwjgl.glfw.GLFW");
    String method =
        sdl()
            ? (visible ? "SDL_ShowWindow" : "SDL_HideWindow")
            : (visible ? "glfwShowWindow" : "glfwHideWindow");
    api.getMethod(method, long.class).invoke(null, handle);
  }
}
