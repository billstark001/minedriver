package net.minecraft.client.gui.screens;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.options.OptionsScreen;

public final class TitleScreen extends Screen {
  public TitleScreen() {
    super(
        List.of(
            new Button(
                "menu.options", () -> Minecraft.getInstance().gui.setScreen(new OptionsScreen()))));
  }
}
