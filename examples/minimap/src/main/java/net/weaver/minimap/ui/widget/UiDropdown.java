package net.weaver.minimap.ui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.weaver.minimap.render.IconAtlas;
import net.weaver.minimap.render.UiDraw;
import net.weaver.minimap.ui.theme.Theme;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * Closed dropdown control with an overlay option list.
 *
 * <p>The control itself is a normal widget; the open list is drawn through
 * {@link UiOverlay#drawAll} after the regular widget pass. The integrator
 * forwards mouse and key events to the dropdown first and uses the boolean
 * return values to stop event propagation.</p>
 */
public class UiDropdown extends AbstractUiWidget implements UiOverlay
{
    /** The overlay row height. */
    public static final int ROW_HEIGHT = 20;
    /** The maximum number of rows shown at once. */
    public static final int MAX_ROWS = 8;

    private final List<String> options;
    private final IntConsumer onChange;
    private int selected;
    private boolean open;
    private int scroll;
    private int keyIndex;

    /**
     * Creates a dropdown.
     *
     * @param x        the left edge
     * @param y        the top edge
     * @param width    the width
     * @param height   the height
     * @param options  the option labels, never {@code null}
     * @param selected the selected index
     * @param onChange the selection callback, possibly {@code null}
     */
    public UiDropdown(int x, int y, int width, int height, List<String> options, int selected,
                      IntConsumer onChange)
    {
        super(x, y, width, height, Component.literal(selectedLabel(options, selected)));
        this.options = options == null ? List.of() : List.copyOf(options);
        this.selected = this.options.isEmpty()
                ? 0 : UiDraw.clamp(selected, 0, this.options.size() - 1);
        this.onChange = onChange == null ? ignored ->
        {
        } : onChange;
        syncMessage();
    }

    /** The selected index. */
    public int selected()
    {
        return selected;
    }

    /** The option labels. */
    public List<String> options()
    {
        return options;
    }

    /** Selects an index without firing the callback. */
    public UiDropdown setSelected(int index)
    {
        if (options.isEmpty())
        {
            selected = 0;
        }
        else
        {
            selected = UiDraw.clamp(index, 0, options.size() - 1);
        }
        syncMessage();
        return this;
    }

    @Override
    public boolean isOpen()
    {
        return open;
    }

    @Override
    public void close()
    {
        open = false;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX,
                                            int mouseY, float partialTick)
    {
        Font font = font();
        if (font == null)
        {
            return;
        }
        Theme theme = theme();
        int x = getX();
        int y = getY();
        int width = getWidth();
        int height = getHeight();
        int radius = Math.min(UiDraw.RADIUS_SMALL, height / 2);
        int background = UiDraw.mix(theme.surfaceAlt(), theme.surfaceElevated(), hoverValue());
        if (open)
        {
            background = UiDraw.mix(background, theme.accentMuted(), 0.35D);
        }
        UiDraw.roundRect(graphics, x, y, width, height, radius, background);
        UiDraw.roundRectBorder(graphics, x, y, width, height, radius, 1,
                open ? theme.accent() : theme.border());
        int textLeft = x + 8;
        int textRight = x + width - 22;
        String label = options.isEmpty() ? "\u2014" : options.get(selected);
        UiDraw.textClipped(graphics, font, label, textLeft,
                y + (height - font.lineHeight) / 2, Math.max(1, textRight - textLeft),
                theme.text(), false);
        IconAtlas.draw(graphics, open ? IconAtlas.Icon.CHEVRON_UP : IconAtlas.Icon.CHEVRON_DOWN,
                x + width - 18, y + (height - 10) / 2, 10, theme.textMuted());
        drawFocusRing(graphics, radius);
    }

    @Override
    public void drawOverlay(GuiGraphicsExtractor graphics, int mouseX, int mouseY)
    {
        Font font = font();
        if (!open || font == null || options.isEmpty())
        {
            return;
        }
        Theme theme = theme();
        Overlay overlay = overlay();
        UiDraw.shadow(graphics, overlay.x, overlay.y, overlay.width, overlay.height,
                UiDraw.RADIUS_SMALL, theme.shadow(), 3);
        UiDraw.roundRect(graphics, overlay.x, overlay.y, overlay.width, overlay.height,
                UiDraw.RADIUS_SMALL, theme.surfaceElevated());
        UiDraw.roundRectBorder(graphics, overlay.x, overlay.y, overlay.width, overlay.height,
                UiDraw.RADIUS_SMALL, 1, theme.borderStrong());
        int hoveredRow = rowAt(mouseX, mouseY, overlay);
        for (int row = 0; row < overlay.rows; row++)
        {
            int index = scroll + row;
            if (index >= options.size())
            {
                break;
            }
            int rowY = overlay.y + 2 + row * ROW_HEIGHT;
            boolean highlighted = index == hoveredRow || index == keyIndex;
            if (highlighted)
            {
                UiDraw.roundRect(graphics, overlay.x + 3, rowY, overlay.width - 6,
                        ROW_HEIGHT - 1, 5, index == hoveredRow
                                ? UiDraw.withAlpha(theme.accent(), 0x33)
                                : UiDraw.withAlpha(theme.text(), 0x12));
            }
            if (index == selected)
            {
                IconAtlas.draw(graphics, IconAtlas.Icon.CHECK, overlay.x + 7,
                        rowY + (ROW_HEIGHT - 10) / 2, 10, theme.accent());
            }
            UiDraw.textClipped(graphics, font, options.get(index), overlay.x + 22,
                    rowY + (ROW_HEIGHT - font.lineHeight) / 2, overlay.width - 30,
                    index == selected ? theme.text() : theme.textMuted(), false);
        }
        if (options.size() > overlay.rows)
        {
            int trackHeight = overlay.height - 8;
            int thumbHeight = Math.max(16, trackHeight * overlay.rows / options.size());
            int maxScroll = Math.max(1, options.size() - overlay.rows);
            int thumbY = overlay.y + 4
                    + (trackHeight - thumbHeight) * scroll / maxScroll;
            UiDraw.scrollbar(graphics, overlay.x + overlay.width - 6, overlay.y + 4, 3,
                    trackHeight, thumbY, thumbHeight,
                    UiDraw.withAlpha(theme.text(), 0x14),
                    UiDraw.withAlpha(theme.text(), 0x55));
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled)
    {
        if (!active || event.button() != 0)
        {
            return false;
        }
        if (open)
        {
            Overlay overlay = overlay();
            int row = rowAt(event.x(), event.y(), overlay);
            if (row >= 0)
            {
                choose(row);
            }
            else
            {
                open = false;
            }
            return true;
        }
        if (!isMouseOver(event.x(), event.y()))
        {
            return false;
        }
        if (options.isEmpty())
        {
            return false;
        }
        open = true;
        keyIndex = selected;
        scroll = UiDraw.clamp(selected - MAX_ROWS / 2, 0, maxScroll());
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY)
    {
        if (!open || options.size() <= MAX_ROWS)
        {
            return false;
        }
        Overlay overlay = overlay();
        boolean overControl = UiDraw.inside(mouseX, mouseY, getX(), getY(), getWidth(),
                getHeight());
        boolean overOverlay = UiDraw.inside(mouseX, mouseY, overlay.x, overlay.y,
                overlay.width, overlay.height);
        if (!overControl && !overOverlay)
        {
            return false;
        }
        if (scrollY != 0.0D)
        {
            scroll = UiDraw.clamp(scroll + (scrollY > 0.0D ? -1 : 1), 0, maxScroll());
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event)
    {
        if (!active)
        {
            return false;
        }
        if (!open)
        {
            if (!isFocused())
            {
                return false;
            }
            if (event.key() == 257 || event.key() == 32 || event.key() == 264)
            {
                if (options.isEmpty())
                {
                    return false;
                }
                open = true;
                keyIndex = selected;
                scroll = UiDraw.clamp(selected - MAX_ROWS / 2, 0, maxScroll());
                return true;
            }
            return false;
        }
        switch (event.key())
        {
            case 256 -> open = false;
            case 265 -> keyIndex = Math.max(0, keyIndex - 1);
            case 264 -> keyIndex = Math.min(options.size() - 1, keyIndex + 1);
            case 268 -> keyIndex = 0;
            case 269 -> keyIndex = options.size() - 1;
            case 257, 32 -> choose(keyIndex);
            default -> {
                return true;
            }
        }
        if (keyIndex < scroll)
        {
            scroll = keyIndex;
        }
        else if (keyIndex >= scroll + Math.min(options.size(), MAX_ROWS))
        {
            scroll = keyIndex - Math.min(options.size(), MAX_ROWS) + 1;
        }
        scroll = UiDraw.clamp(scroll, 0, maxScroll());
        return true;
    }

    /**
     * Resolves a row index under the cursor.
     *
     * @param mouseX    the cursor x
     * @param mouseY    the cursor y
     * @param overlay   the overlay rectangle
     * @return the option index, or {@code -1}
     */
    private int rowAt(double mouseX, double mouseY, Overlay overlay)
    {
        return net.weaver.minimap.ui.theme.UiMath.dropdownRowAt(mouseX, mouseY, overlay.x,
                overlay.y, overlay.width, ROW_HEIGHT, scroll, options.size(), overlay.rows);
    }

    /**
     * Pure row hit test used by tests and custom overlays.
     *
     * @param mouseX     the cursor x
     * @param mouseY     the cursor y
     * @param x          the panel x
     * @param y          the panel y
     * @param width      the panel width
     * @param rowHeight  the row height
     * @param scroll     the first visible row
     * @param count      the option count
     * @param maxVisible the maximum visible rows
     * @return the option index, or {@code -1}
     */
    public static int rowAt(double mouseX, double mouseY, int x, int y, int width,
                            int rowHeight, int scroll, int count, int maxVisible)
    {
        return net.weaver.minimap.ui.theme.UiMath.dropdownRowAt(mouseX, mouseY, x, y, width,
                rowHeight, scroll, count, maxVisible);
    }

    private void choose(int index)
    {
        open = false;
        if (index < 0 || index >= options.size())
        {
            return;
        }
        boolean changed = index != selected;
        selected = index;
        syncMessage();
        if (changed)
        {
            onChange.accept(index);
        }
    }

    private void syncMessage()
    {
        setMessage(Component.literal(options.isEmpty() ? "" : options.get(selected)));
    }

    private int maxScroll()
    {
        return Math.max(0, options.size() - Math.min(options.size(), MAX_ROWS));
    }

    private Overlay overlay()
    {
        int rows = Math.min(options.size(), MAX_ROWS);
        int height = Math.max(ROW_HEIGHT + 4, rows * ROW_HEIGHT + 4);
        int x = getX();
        int screenHeight = screenHeight();
        int y = getY() + getHeight() + 2;
        if (y + height > screenHeight - 4)
        {
            y = getY() - height - 2;
        }
        y = UiDraw.clamp(y, 4, Math.max(4, screenHeight - height - 4));
        return new Overlay(x, y, getWidth(), height, rows);
    }

    private static int screenHeight()
    {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null)
        {
            return 320;
        }
        return minecraft.getWindow().getGuiScaledHeight();
    }

    private static String selectedLabel(List<String> options, int selected)
    {
        if (options == null || options.isEmpty())
        {
            return "";
        }
        return options.get(UiDraw.clamp(selected, 0, options.size() - 1));
    }

    private record Overlay(int x, int y, int width, int height, int rows)
    {
    }
}
