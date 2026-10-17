package me.shedaniel.clothconfig2.gui.entries;

import java.util.List;
import me.shedaniel.clothconfig2.gui.widget.DynamicEntryListWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;

/** Models the independent header hit area, without normal widget geometry getters. */
public final class SubCategoryListEntry extends DynamicEntryListWidget.Entry {
  private final CategoryLabelWidget widget = new CategoryLabelWidget();
  private boolean expanded;

  public boolean isExpanded() {
    return expanded;
  }

  public void setExpanded(boolean value) {
    expanded = value;
  }

  public Screen.Component getFieldName() {
    return new Screen.Component("fixture.category");
  }

  public DynamicEntryListWidget.Rectangle getEntryArea(int x, int y, int width, int height) {
    return widget.rectangle;
  }

  public List<CategoryLabelWidget> children() {
    return List.of(widget);
  }

  public boolean mouseClicked(MouseButtonEvent event, boolean twice) {
    return widget.mouseClicked(event, twice);
  }

  public final class CategoryLabelWidget {
    private final DynamicEntryListWidget.Rectangle rectangle =
        new DynamicEntryListWidget.Rectangle(20, 30, 180, 24);

    public boolean mouseClicked(MouseButtonEvent event, boolean twice) {
      if (event.x() < 20 || event.x() >= 200 || event.y() < 30 || event.y() >= 54) return false;
      expanded = !expanded;
      return true;
    }
  }
}
