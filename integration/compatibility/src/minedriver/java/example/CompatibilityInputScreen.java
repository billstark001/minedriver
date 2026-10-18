package example;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** A native text widget for testing actual keyboard character callbacks. */
final class CompatibilityInputScreen extends Screen {
  private EditBox input;

  CompatibilityInputScreen() {
    super(Component.literal("MineDriver input"));
  }

  @Override
  protected void init() {
    input = addRenderableWidget(new EditBox(font, 20, 30, 180, 20, Component.literal("Input")));
    setInitialFocus(input);
  }

  String value() {
    return input.getValue();
  }
}
