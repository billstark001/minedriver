package me.shedaniel.clothconfig2.gui.widget;

/** Shape-only fixture, never evidence of real Cloth compatibility. */
public final class DynamicEntryListWidget {
  public static class Rectangle {
    public int x, y, width, height;

    public Rectangle(int x, int y, int width, int height) {
      this.x = x;
      this.y = y;
      this.width = width;
      this.height = height;
    }
  }

  public abstract static class Entry {
    protected final Rectangle bounds = new Rectangle(20, 30, 180, 100);

    public boolean isEnabled() {
      return true;
    }

    public boolean isDisplayed() {
      return true;
    }
  }
}
