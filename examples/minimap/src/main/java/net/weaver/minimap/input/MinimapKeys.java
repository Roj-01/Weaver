package net.weaver.minimap.input;

/**
 * GLFW-compatible key and mouse codes used by the minimap.
 *
 * <p>These are the stable values from the GLFW header. Keeping them local
 * avoids a compile-time LWJGL dependency while still matching the values the
 * game's input layer delivers.</p>
 */
public final class MinimapKeys
{
    public static final int KEY_B = 66;
    public static final int KEY_H = 72;
    public static final int KEY_J = 74;
    public static final int KEY_K = 75;
    public static final int KEY_L = 76;
    public static final int KEY_M = 77;
    public static final int KEY_N = 78;
    public static final int KEY_O = 79;
    public static final int KEY_P = 80;
    public static final int KEY_EQUAL = 61;
    public static final int KEY_MINUS = 45;
    public static final int KEY_LEFT_BRACKET = 91;
    public static final int KEY_RIGHT_BRACKET = 93;
    public static final int KEY_ESCAPE = 256;
    public static final int KEY_RIGHT = 262;
    public static final int KEY_LEFT = 263;
    public static final int KEY_DOWN = 264;
    public static final int KEY_UP = 265;
    public static final int KEY_HOME = 268;
    public static final int KEY_END = 269;
    public static final int KEY_F1 = 290;
    public static final int KEY_F6 = 295;

    public static final int MOUSE_LEFT = 0;
    public static final int MOUSE_RIGHT = 1;
    public static final int MOUSE_MIDDLE = 2;

    private MinimapKeys()
    {
    }
}
