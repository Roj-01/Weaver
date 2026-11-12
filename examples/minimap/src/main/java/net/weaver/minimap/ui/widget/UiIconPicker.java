package net.weaver.minimap.ui.widget;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.weaver.minimap.render.IconAtlas;
import net.weaver.minimap.render.UiDraw;
import net.weaver.minimap.ui.theme.ScissorGuard;
import net.weaver.minimap.ui.theme.Theme;

import java.util.function.Consumer;

/**
 * Grid picker for the generated icon catalogue.
 */
public class UiIconPicker extends AbstractUiWidget
{
    private static final int COLUMNS = 8;
    private static final int GAP = 2;
    private static final IconAtlas.Icon[] ICONS = IconAtlas.Icon.values();

    private final Consumer<IconAtlas.Icon> onChange;
    private IconAtlas.Icon selected;
    private int scroll;

    /**
     * Creates an icon picker.
     *
     * @param x        the left edge
     * @param y        the top edge
     * @param width    the width
     * @param height   the height
     * @param selected the initial icon
     * @param onChange the change callback, possibly {@code null}
     */
    public UiIconPicker(int x, int y, int width, int height, IconAtlas.Icon selected,
                        Consumer<IconAtlas.Icon> onChange)
    {
        super(x, y, width, height, Component.literal("Icon"));
        this.selected = selected == null ? IconAtlas.Icon.PIN : selected;
        this.onChange = onChange == null ? ignored ->
        {
        } : onChange;
        syncMessage();
    }

    /** The selected icon. */
    public IconAtlas.Icon selected()
    {
        return selected;
    }

    /** Selects an icon without firing the callback. */
    public UiIconPicker setSelected(IconAtlas.Icon icon)
    {
        selected = icon == null ? IconAtlas.Icon.PIN : icon;
        syncMessage();
        return this;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX,
                                            int mouseY, float partialTick)
    {
        Theme theme = theme();
        int radius = Math.min(UiDraw.RADIUS_SMALL, getHeight() / 2);
        UiDraw.roundRect(graphics, getX(), getY(), getWidth(), getHeight(), radius,
                theme.surfaceAlt());
        UiDraw.roundRectBorder(graphics, getX(), getY(), getWidth(), getHeight(), radius, 1,
                theme.border());
        scroll = UiDraw.clamp(scroll, 0, maxScroll());
        IconAtlas.Icon hovered = iconAt(mouseX, mouseY);
        IconAtlas.Icon[] icons = ICONS;
        int cell = cellSize();
        int columns = COLUMNS;
        int gridWidth = columns * cell;
        int originX = getX() + (getWidth() - gridWidth) / 2;
        int originY = getY() - scroll * cell;
        int firstRow = Math.max(0, (getY() - originY) / cell);
        int lastRow = Math.min((icons.length + columns - 1) / columns,
                (getY() + getHeight() - originY) / cell + 1);
        try (ScissorGuard ignored = UiDraw.scissor(graphics, getX() + 1, getY() + 1,
                getWidth() - 2, getHeight() - 2))
        {
            for (int row = firstRow; row < lastRow; row++)
            {
                for (int column = 0; column < columns; column++)
                {
                    int index = row * columns + column;
                    if (index >= icons.length)
                    {
                        break;
                    }
                    int left = originX + column * cell;
                    int top = originY + row * cell;
                    IconAtlas.Icon icon = icons[index];
                    if (icon == selected)
                    {
                        UiDraw.roundRect(graphics, left + GAP, top + GAP, cell - GAP * 2,
                                cell - GAP * 2, 4, UiDraw.withAlpha(theme.accent(), 0x33));
                        UiDraw.roundRectBorder(graphics, left + GAP, top + GAP,
                                cell - GAP * 2, cell - GAP * 2, 4, 1, theme.accent());
                    }
                    else if (icon == hovered)
                    {
                        UiDraw.roundRect(graphics, left + GAP, top + GAP, cell - GAP * 2,
                                cell - GAP * 2, 4, UiDraw.withAlpha(theme.text(), 0x14));
                    }
                    int iconSize = Math.max(8, cell - 10);
                    IconAtlas.draw(graphics, icon, left + (cell - iconSize) / 2,
                            top + (cell - iconSize) / 2, iconSize,
                            icon == selected ? theme.accent() : theme.text());
                }
            }
        }
        if (maxScroll() > 0)
        {
            int trackHeight = getHeight() - 8;
            int thumbHeight = Math.max(16, trackHeight * getHeight()
                    / Math.max(getHeight(), contentHeight()));
            int thumbY = getY() + 4 + (trackHeight - thumbHeight) * scroll / maxScroll();
            UiDraw.scrollbar(graphics, getX() + getWidth() - 6, getY() + 4, 3, trackHeight,
                    thumbY, thumbHeight, UiDraw.withAlpha(theme.text(), 0x12),
                    UiDraw.withAlpha(theme.text(), 0x55));
        }
        drawFocusRing(graphics, radius);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled)
    {
        if (!active || event.button() != 0 || !isMouseOver(event.x(), event.y()))
        {
            return false;
        }
        IconAtlas.Icon icon = iconAt(event.x(), event.y());
        if (icon == null)
        {
            return false;
        }
        boolean changed = icon != selected;
        selected = icon;
        syncMessage();
        if (changed)
        {
            onChange.accept(icon);
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY)
    {
        if (!isMouseOver(mouseX, mouseY) || maxScroll() <= 0 || scrollY == 0.0D)
        {
            return false;
        }
        scroll = UiDraw.clamp(scroll + (scrollY > 0.0D ? -1 : 1), 0, maxScroll());
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event)
    {
        if (!active || !isFocused())
        {
            return false;
        }
        IconAtlas.Icon[] icons = ICONS;
        int index = selected.ordinal();
        int columns = COLUMNS;
        int next;
        switch (event.key())
        {
            case 263 -> next = index - 1;
            case 262 -> next = index + 1;
            case 265 -> next = index - columns;
            case 264 -> next = index + columns;
            default -> {
                return false;
            }
        }
        next = UiDraw.clamp(next, 0, icons.length - 1);
        if (next != index)
        {
            selected = icons[next];
            syncMessage();
            onChange.accept(selected);
            ensureVisible(next, columns);
        }
        return true;
    }

    /** The y position of the first grid row, accounting for scrolling. */
    public int gridOriginY()
    {
        return getY() - scroll * cellSize();
    }

    private void ensureVisible(int index, int columns)
    {
        int cell = cellSize();
        int row = index / Math.max(1, columns);
        int visibleRows = Math.max(1, getHeight() / Math.max(1, cell));
        if (row < scroll)
        {
            scroll = row;
        }
        else if (row >= scroll + visibleRows)
        {
            scroll = row - visibleRows + 1;
        }
        scroll = UiDraw.clamp(scroll, 0, maxScroll());
    }

    private IconAtlas.Icon iconAt(double mouseX, double mouseY)
    {
        if (!UiDraw.inside(mouseX, mouseY, getX(), getY(), getWidth(), getHeight()))
        {
            return null;
        }
        int cell = cellSize();
        int columns = COLUMNS;
        int gridWidth = columns * cell;
        int originX = getX() + (getWidth() - gridWidth) / 2;
        int originY = getY() - scroll * cell;
        if (mouseX < originX || mouseY < originY)
        {
            return null;
        }
        int column = (int) ((mouseX - originX) / cell);
        int row = (int) ((mouseY - originY) / cell);
        if (column < 0 || column >= columns || row < 0)
        {
            return null;
        }
        int index = row * columns + column;
        IconAtlas.Icon[] icons = ICONS;
        return index < icons.length ? icons[index] : null;
    }

    private void syncMessage()
    {
        setMessage(Component.literal(selected.name()));
    }

    private int cellSize()
    {
        int rows = (ICONS.length + COLUMNS - 1) / COLUMNS;
        int horizontal = (getWidth() - GAP * 2) / COLUMNS;
        int vertical = (getHeight() - GAP * 2) / rows;
        return Math.max(10, Math.min(horizontal, vertical));
    }

    private int maxScroll()
    {
        int rows = (ICONS.length + COLUMNS - 1) / COLUMNS;
        int visibleRows = Math.max(1, getHeight() / Math.max(1, cellSize()));
        return Math.max(0, rows - visibleRows);
    }

    private int contentHeight()
    {
        int rows = (ICONS.length + COLUMNS - 1) / COLUMNS;
        return rows * cellSize();
    }
}
