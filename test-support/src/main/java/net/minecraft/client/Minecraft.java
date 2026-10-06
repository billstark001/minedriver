package net.minecraft.client;

import java.util.concurrent.LinkedBlockingQueue;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;

/** Test double: does not render, load mods, or establish a Minecraft support claim. */
public final class Minecraft {
  private static volatile Minecraft instance;
  private final Thread clientThread = Thread.currentThread();
  private final LinkedBlockingQueue<Runnable> actions = new LinkedBlockingQueue<>();
  private volatile boolean running = true;
  public final Gui gui = new Gui();
  public final Options options = new Options();
  public final Renderer gameRenderer = new Renderer();
  public final Keyboard keyboardHandler = new Keyboard();
  public Object player;
  public Object level;

  public static Minecraft getInstance() {
    return instance;
  }

  public static void main(String[] arguments) throws Exception {
    instance = new Minecraft();
    instance.gui.setScreen(new TitleScreen());
    while (instance.running) {
      Runnable action;
      while ((action = instance.actions.poll()) != null) action.run();
      instance.renderFrame(true);
      Thread.sleep(10);
    }
  }

  public void renderFrame(boolean tick) {}

  public void execute(Runnable action) {
    actions.add(action);
  }

  public boolean isSameThread() {
    return clientThread == Thread.currentThread();
  }

  public Object getSingleplayerServer() {
    return null;
  }

  public int getFps() {
    return 100;
  }

  public long getFrameTimeNs() {
    return 10000000;
  }

  public Window getWindow() {
    return new Window();
  }

  public void stop() {
    running = false;
  }

  public static final class Gui {
    private Screen screen;

    public Screen screen() {
      return screen;
    }

    public Object overlay() {
      return null;
    }

    public void setScreen(Screen value) {
      screen = value;
    }
  }

  public static final class Options {
    public String languageCode = "en_us";
  }

  public static final class Renderer {
    public Object mainRenderTarget() {
      return this;
    }
  }

  public static final class Window {
    public long handle() {
      return 1;
    }

    public int getGuiScaledWidth() {
      return 320;
    }

    public int getGuiScaledHeight() {
      return 240;
    }
  }

  public static final class Keyboard {
    public void keyPress(long handle, int action, KeyEvent event) {
      if (event.key() == 41 && action == 1) instance.gui.setScreen(new TitleScreen());
    }

    public void textInput(long handle, String value) {}
  }
}
