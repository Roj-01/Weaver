package net.weaver.minimap.ui.widget;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.weaver.minimap.render.UiDraw;
import net.weaver.minimap.ui.theme.Colors;
import net.weaver.minimap.ui.theme.Theme;

import java.util.function.IntConsumer;

/**
 * Compact swatch grid for waypoint and marker colours.
 *
 * <p>The first twelve swatches are the active theme's waypoint palette; the
 * remaining twelve are neutral and utility colours. Click or use the arrow
 * keys to choose a colour.</p>
 */
public class UiColorPicker extends AbstractUiWidget
{
    private static final int COLUMNS = 12;
    private static final int PADDING = 4;
    private static final int[] NEUTRALS = {
            0xFF1B1F27, 0xFF2C323D, 0xFF4A5260, 0xFF6B7482,
            0xFF9AA3B0, 0xFFC7CDD6, 0xFFE9EDF2, 0xFFFFFFFF,
            0xFF000000, 0xFFFF5C6C, 0xFF5CC8FF, 0xFFF5A524
    };

    private final IntConsumer onChange;
    private int color;
    private int selected;

    /**
     * Creates a colour picker.
     *
     * @param x        the left edge
     * @param y        the top edge
     * @param width    the width
     * @param height   the height
     * @param color    the initial colour
     * @param onChange the change callback, possibly {@code null}
     */
    public UiColorPicker(int x, int y, int width, int height, int color, IntConsumer onChange)
    {
        super(x, y, width, height, Component.literal("Colour"));
        this.color = color;
        this.onChange = onChange == null ? ignored ->
        {
        } : onChange;
        this.selected = closestIndex();
    }

    /** The current colour. */
    public int color()
    {
        return color;
    }

    /** Sets the colour without firing the callback. */
    public UiColorPicker setColor(int color)
    {
        this.color = color;
        this.selected = closestIndex();
        return this;
    }

    /** The number of swatches. */
    public int swatchCount()
    {
        return Theme.PALETTE_SIZE + NEUTRALS.length;
    }

    /** The colour of one swatch. */
    public int swatchColor(int index)
    {
        Theme theme = theme();
        if (index < Theme.PALETTE_SIZE)
        {
            return theme.waypointColor(index);
        }
        return NEUTRALS[Math.floorMod(index - Theme.PALETTE_SIZE, NEUTRALS.length)];
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX,
                                            int mouseY, float partialTick)
    {
        Geometry geometry = geometry();
        Theme theme = theme();
        int hovered = swatchIndexAt(mouseX, mouseY, getX(), getY(), getWidth(), getHeight(),
                swatchCount());
        for (int index = 0; index < swatchCount(); index++)
        {
            int left = geometry.left(index);
            int top = geometry.top(index);
            int colorValue = swatchColor(index);
            UiDraw.roundRect(graphics, left, top, geometry.size, geometry.size,
                    Math.min(4, geometry.size / 3), colorValue);
            if (index == selected || index == hovered)
            {
                int border = index == selected ? theme.accent() : theme.text();
                UiDraw.roundRectBorder(graphics, left - 1, top - 1, geometry.size + 2,
                        geometry.size + 2, Math.min(5, geometry.size / 3), 2, border);
            }
        }
        drawFocusRing(graphics, Math.min(UiDraw.RADIUS_SMALL, getHeight() / 2));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled)
    {
        if (!active || event.button() != 0)
        {
            return false;
        }
        int index = swatchIndexAt(event.x(), event.y(), getX(), getY(), getWidth(),
                getHeight(), swatchCount());
        if (index < 0)
        {
            return false;
        }
        choose(index);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event)
    {
        if (!active || !isFocused())
        {
            return false;
        }
        int next;
        switch (event.key())
        {
            case 263 -> next = selected - 1;
            case 262 -> next = selected + 1;
            case 265 -> next = selected - COLUMNS;
            case 264 -> next = selected + COLUMNS;
            default -> {
                return false;
            }
        }
        choose(UiDraw.clamp(next, 0, swatchCount() - 1));
        return true;
    }

    /**
     * Resolves a swatch index under the cursor.
     *
     * @return the swatch index, or {@code -1}
     */
    public static int swatchIndexAt(double mouseX, double mouseY, int x, int y, int width,
                                    int height, int count)
    {
        return net.weaver.minimap.ui.theme.UiMath.swatchIndexAt(mouseX, mouseY, x, y, width,
                height, count, COLUMNS, PADDING);
    }

    private void choose(int index)
    {
        int next = swatchColor(index);
        boolean changed = next != color;
        selected = index;
        color = next;
        if (changed)
        {
            onChange.accept(color);
        }
    }

    private int closestIndex()
    {
        int best = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int index = 0; index < swatchCount(); index++)
        {
            int candidate = swatchColor(index);
            double distance = distance(candidate, color);
            if (distance < bestDistance)
            {
                bestDistance = distance;
                best = index;
            }
        }
        return best;
    }

    private static double distance(int first, int second)
    {
        int red = Colors.red(first) - Colors.red(second);
        int green = Colors.green(first) - Colors.green(second);
        int blue = Colors.blue(first) - Colors.blue(second);
        return red * red + green * green + blue * blue;
    }

    private Geometry geometry()
    {
        int rows = (swatchCount() + COLUMNS - 1) / COLUMNS;
        int cellWidth = Math.max(1, (getWidth() - PADDING * 2) / COLUMNS);
        int cellHeight = Math.max(1, (getHeight() - PADDING * 2) / rows);
        int size = Math.min(cellWidth, cellHeight);
        int gridWidth = COLUMNS * cellWidth;
        int gridHeight = rows * cellHeight;
        int originX = getX() + (getWidth() - gridWidth) / 2;
        int originY = getY() + (getHeight() - gridHeight) / 2;
        return new Geometry(originX, originY, cellWidth, cellHeight, size);
    }

    private record Geometry(int originX, int originY, int cellWidth, int cellHeight, int size)
    {
        private int left(int index)
        {
            return originX + (index % COLUMNS) * cellWidth + (cellWidth - size) / 2;
        }

        private int top(int index)
        {
            return originY + (index / COLUMNS) * cellHeight + (cellHeight - size) / 2;
        }
    }
}
