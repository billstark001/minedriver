package org.lwjgl.sdl;

public final class SDLMouse {
  public static int buttons;

  public static int SDL_GetMouseState(java.nio.FloatBuffer x, java.nio.FloatBuffer y) {
    return buttons;
  }
}
