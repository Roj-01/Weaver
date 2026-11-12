package net.weaver.minimap.ui.theme;

import java.util.Objects;
import java.util.function.ToIntFunction;

/**
 * Pure layout and hit-test maths shared by the drawing toolkit and widgets.
 *
 * <p>This class deliberately has no game imports so the behaviour can be tested
 * without launching the client. The widget classes delegate to these methods so
 * the tested maths is the same maths used on screen.</p>
 */
public final class UiMath
{
    /** The grid unit. */
    public static final int GRID = 8;
    /** The ellipsis character appended by the clipping helpers. */
    public static final String ELLIPSIS = "\u2026";

    private UiMath()
    {
    }

    /** Rounds a value to the nearest 8 px grid line. */
    public static int grid(int value)
    {
        return Math.round(value / (float) GRID) * GRID;
    }

    /** Rounds a value up to the next 8 px grid line. */
    public static int gridCeil(int value)
    {
        return Math.floorDiv(value + GRID - 1, GRID) * GRID;
    }

    /** Rounds a value down to the previous 8 px grid line. */
    public static int gridFloor(int value)
    {
        return Math.floorDiv(value, GRID) * GRID;
    }

    /** Clamps an integer into an inclusive range. */
    public static int clamp(int value, int min, int max)
    {
        return Math.max(min, Math.min(max, value));
    }

    /** Clamps a double into an inclusive range. */
    public static double clamp(double value, double min, double max)
    {
        return Math.max(min, Math.min(max, value));
    }

    /** Clamps a double into 0..1. */
    public static double clamp01(double value)
    {
        return value < 0.0D ? 0.0D : Math.min(value, 1.0D);
    }

    /** Whether a point is inside an axis-aligned rectangle. */
    public static boolean inside(double px, double py, int x, int y, int width, int height)
    {
        return px >= x && px < x + width && py >= y && py < y + height;
    }

    /** Whether a point is inside a rounded rectangle. */
    public static boolean hitRoundedRect(double px, double py, int x, int y, int width,
                                         int height, int radius)
    {
        if (width <= 0 || height <= 0 || !inside(px, py, x, y, width, height))
        {
            return false;
        }
        int r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        if (r == 0)
        {
            return true;
        }
        double localX = px - x;
        double localY = py - y;
        if ((localX >= r && localX <= width - r) || (localY >= r && localY <= height - r))
        {
            return true;
        }
        double cornerX = localX < r ? r : width - r;
        double cornerY = localY < r ? r : height - r;
        double dx = localX - cornerX;
        double dy = localY - cornerY;
        return dx * dx + dy * dy <= (double) r * r;
    }

    /** The value a slider takes at a mouse position. */
    public static double sliderValue(double mouseX, int x, int width, double min, double max)
    {
        if (width <= 0 || max <= min)
        {
            return min;
        }
        return min + (max - min) * clamp01((mouseX - x) / (double) width);
    }

    /** The track position for a slider value. */
    public static double sliderX(double value, int x, int width, double min, double max)
    {
        if (max <= min)
        {
            return x;
        }
        return x + width * clamp01((value - min) / (max - min));
    }

    /**
     * Clips a label against a width function without needing a live font.
     *
     * @param value   the full label
     * @param width   the available width
     * @param widthOf measures a candidate string
     * @return the clipped label, or an empty string when nothing fits
     */
    public static String ellipsize(String value, int width, ToIntFunction<String> widthOf)
    {
        Objects.requireNonNull(widthOf, "widthOf");
        if (value == null || value.isEmpty() || width <= 0)
        {
            return "";
        }
        if (widthOf.applyAsInt(value) <= width)
        {
            return value;
        }
        int ellipsisWidth = widthOf.applyAsInt(ELLIPSIS);
        if (ellipsisWidth > width)
        {
            return "";
        }
        int end = safeCut(value, value.length());
        while (end > 0)
        {
            if (widthOf.applyAsInt(value.substring(0, end)) + ellipsisWidth <= width)
            {
                return value.substring(0, end) + ELLIPSIS;
            }
            end = safeCut(value, end - 1);
        }
        return "";
    }

    /**
     * Resolves a tab index under the cursor.
     *
     * @return the tab index, or {@code -1}
     */
    public static int tabIndexAt(double mouseX, double mouseY, int x, int y, int width,
                                 int height, int count)
    {
        if (count <= 0 || width <= 0 || !inside(mouseX, mouseY, x, y, width, height))
        {
            return -1;
        }
        int index = (int) (((long) (mouseX - x) * count) / width);
        return clamp(index, 0, count - 1);
    }

    /**
     * Resolves a dropdown row under the cursor.
     *
     * @param rowHeight  the row height
     * @param scroll     the first visible row
     * @param count      the option count
     * @param maxVisible the maximum visible rows
     * @return the option index, or {@code -1}
     */
    public static int dropdownRowAt(double mouseX, double mouseY, int x, int y, int width,
                                    int rowHeight, int scroll, int count, int maxVisible)
    {
        int visible = Math.min(count, maxVisible);
        int height = visible * rowHeight + 4;
        if (!inside(mouseX, mouseY, x, y, width, height))
        {
            return -1;
        }
        double relative = mouseY - y - 2;
        if (relative < 0.0D)
        {
            return -1;
        }
        int row = (int) (relative / rowHeight);
        if (row < 0 || row >= visible)
        {
            return -1;
        }
        int index = scroll + row;
        return index < count ? index : -1;
    }

    /**
     * Resolves a context-menu row under the cursor.
     *
     * @return the item index, or {@code -1}
     */
    public static int contextMenuItemAt(double mouseX, double mouseY, int x, int y, int width,
                                        int rowHeight, int padding, int count)
    {
        int height = count * rowHeight + padding * 2;
        if (!inside(mouseX, mouseY, x, y, width, height))
        {
            return -1;
        }
        double relative = mouseY - y - padding;
        if (relative < 0.0D)
        {
            return -1;
        }
        int index = (int) (relative / rowHeight);
        return index >= 0 && index < count ? index : -1;
    }

    /**
     * Resolves a colour swatch under the cursor.
     *
     * @return the swatch index, or {@code -1}
     */
    public static int swatchIndexAt(double mouseX, double mouseY, int x, int y, int width,
                                    int height, int count, int columns, int padding)
    {
        if (count <= 0 || width <= 0 || height <= 0 || columns <= 0)
        {
            return -1;
        }
        int rows = (count + columns - 1) / columns;
        int cellWidth = Math.max(1, (width - padding * 2) / columns);
        int cellHeight = Math.max(1, (height - padding * 2) / rows);
        int gridWidth = columns * cellWidth;
        int gridHeight = rows * cellHeight;
        int originX = x + (width - gridWidth) / 2;
        int originY = y + (height - gridHeight) / 2;
        if (mouseX < originX || mouseY < originY)
        {
            return -1;
        }
        int column = (int) ((mouseX - originX) / cellWidth);
        int row = (int) ((mouseY - originY) / cellHeight);
        if (column < 0 || column >= columns || row < 0 || row >= rows)
        {
            return -1;
        }
        int index = row * columns + column;
        return index < count ? index : -1;
    }

    /** Whether a point hits a scrollbar thumb. */
    public static boolean hitScrollbar(double px, double py, int x, int y, int width,
                                       int height, int thumbY, int thumbHeight)
    {
        return px >= x - 2 && px < x + width + 2
                && py >= thumbY && py < thumbY + thumbHeight;
    }

    /**
     * Maps a GLFW key code to a short display label without a LWJGL dependency.
     *
     * @param key the GLFW key code
     * @return a human-readable label
     */
    public static String keyLabel(int key)
    {
        if (key >= 65 && key <= 90)
        {
            return Character.toString((char) key);
        }
        if (key >= 48 && key <= 57)
        {
            return Character.toString((char) key);
        }
        return switch (key)
        {
            case 32 -> "Space";
            case 256 -> "Escape";
            case 257 -> "Enter";
            case 258 -> "Tab";
            case 259 -> "Backspace";
            case 260 -> "Insert";
            case 261 -> "Delete";
            case 262 -> "Right";
            case 263 -> "Left";
            case 264 -> "Down";
            case 265 -> "Up";
            case 266 -> "Page Up";
            case 267 -> "Page Down";
            case 268 -> "Home";
            case 269 -> "End";
            case 280 -> "Caps Lock";
            case 340 -> "Left Shift";
            case 341 -> "Left Ctrl";
            case 342 -> "Left Alt";
            case 344 -> "Right Shift";
            case 345 -> "Right Ctrl";
            case 346 -> "Right Alt";
            default -> "Key " + key;
        };
    }

    private static int safeCut(String value, int index)
    {
        int cut = Math.max(0, Math.min(index, value.length()));
        if (cut > 0 && cut < value.length() && Character.isHighSurrogate(value.charAt(cut - 1)))
        {
            cut--;
        }
        return cut;
    }
}
