package net.minecraft.client.gui.screens;

import java.util.List;
import net.minecraft.client.input.MouseButtonEvent;

public class Screen {
  public record Contents(String key) {
    public String getKey() {
      return key;
    }
  }

  public record Component(String key) {
    public String getString() {
      return key;
    }

    public Contents getContents() {
      return new Contents(key);
    }
  }

  public static class Button {
    public boolean active = true;
    public boolean visible = true;
    private final String key;
    private final Runnable action;

    public Button(String key, Runnable action) {
      this.key = key;
      this.action = action;
    }

    public Component getMessage() {
      return new Component(key);
    }

    public int getX() {
      return 10;
    }

    public int getY() {
      return 10;
    }

    public int getWidth() {
      return 100;
    }

    public int getHeight() {
      return 20;
    }

    public boolean isFocused() {
      return false;
    }

    public void onPress(net.minecraft.client.input.KeyEvent event) {
      action.run();
    }
  }

  private final List<Button> children;

  public Screen(List<Button> children) {
    this.children = children;
  }

  public Component getTitle() {
    return new Component("Test screen");
  }

  public List<Button> children() {
    return children;
  }

  public void mouseMoved(double x, double y) {}

  public boolean mouseClicked(MouseButtonEvent event, boolean twice) {
    if (children.isEmpty()) return false;
    children.get(0).onPress(new net.minecraft.client.input.KeyEvent(257, 0, 0));
    return true;
  }

  public boolean mouseReleased(MouseButtonEvent event) {
    return true;
  }

  public boolean mouseScrolled(double x, double y, double h, double v) {
    return true;
  }
}
