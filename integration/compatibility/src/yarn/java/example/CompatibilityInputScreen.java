package example;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** A native text widget for testing actual keyboard character callbacks. */
final class CompatibilityInputScreen extends Screen {
  private TextFieldWidget input;

  CompatibilityInputScreen() {
    super(Text.literal("MineDriver input"));
  }

  @Override
  protected void init() {
    input =
        addDrawableChild(new TextFieldWidget(textRenderer, 20, 30, 180, 20, Text.literal("Input")));
    setInitialFocus(input);
  }

  String value() {
    return input.getText();
  }
}
