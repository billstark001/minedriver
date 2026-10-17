package org.lwjgl.sdl;

/** Native focus query test double; no actual OS window is created. */
public final class SDLVideo {
  public static final long SDL_WINDOW_INPUT_FOCUS = 0x200L;
  public static volatile long flags = SDL_WINDOW_INPUT_FOCUS;

  public static long SDL_GetWindowFlags(long window) {
    return flags;
  }
}
