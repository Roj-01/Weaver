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
import net.weaver.minimap.ui.theme.ThemeManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Cursor-anchored context menu.
 *
 * <p>The menu is a pure overlay: it owns no screen space until
 * {@link #open} is called. Draw it with {@link UiOverlay#drawAll} after the
 * normal widget pass and route mouse and key events to it before any other
 * widgets while it is open.</p>
 */
public final class UiContextMenu implements UiOverlay
{
    /** The row height. */
    public static final int ROW_HEIGHT = 22;
    private static final int PADDING = 4;

    /**
     * One menu entry.
     *
     * @param label   the entry label
     * @param icon    the leading icon, possibly {@code null}
     * @param action  the action, possibly {@code null}
     * @param enabled whether the entry can be activated
     */
    public record Item(Component label, IconAtlas.Icon icon, Runnable action, boolean enabled)
    {
        public Item
        {
            label = label == null ? Component.empty() : label;
        }

        /** Creates an enabled entry without an icon. */
        public static Item of(String label, Runnable action)
        {
            return new Item(Component.literal(label == null ? "" : label), null, action, true);
        }

        /** Creates an enabled entry. */
        public static Item of(String label, IconAtlas.Icon icon, Runnable action)
        {
            return new Item(Component.literal(label == null ? "" : label), icon, action, true);
        }
    }

    private final List<Item> items = new ArrayList<>();
    private boolean open;
    private int anchorX;
    private int anchorY;
    private int highlight = -1;

    /** Creates an empty context menu. */
    public UiContextMenu()
    {
    }

    /** Whether the menu is open. */
    @Override
    public boolean isOpen()
    {
        return open;
    }

    /** Closes the menu. */
    @Override
    public void close()
    {
        open = false;
        highlight = -1;
    }

    /**
     * Opens the menu at a cursor position.
     *
     * @param x     the anchor x
     * @param y     the anchor y
     * @param items the entries, copied
     */
    public void open(int x, int y, List<Item> items)
    {
        this.items.clear();
        if (items != null)
        {
            for (Item item : items)
            {
                if (item != null)
                {
                    this.items.add(item);
                }
            }
        }
        if (this.items.isEmpty())
        {
            close();
            return;
        }
        anchorX = x;
        anchorY = y;
        open = true;
        highlight = -1;
    }

    /** Adds an entry to the menu. */
    public UiContextMenu add(String label, IconAtlas.Icon icon, Runnable action)
    {
        items.add(Item.of(label, icon, action));
        return this;
    }

    /** Adds an entry to the menu. */
    public UiContextMenu add(Item item)
    {
        if (item != null)
        {
            items.add(item);
        }
        return this;
    }

    /** Removes every entry. */
    public void clear()
    {
        items.clear();
        close();
    }

    /** The entries. */
    public List<Item> items()
    {
        return List.copyOf(items);
    }

    @Override
    public void drawOverlay(GuiGraphicsExtractor graphics, int mouseX, int mouseY)
    {
        Font font = font();
        if (!open || font == null || items.isEmpty())
        {
            return;
        }
        Theme theme = theme();
        Geometry geometry = geometry(font);
        UiDraw.shadow(graphics, geometry.x, geometry.y, geometry.width, geometry.height,
                UiDraw.RADIUS_SMALL, theme.shadow(), 3);
        UiDraw.roundRect(graphics, geometry.x, geometry.y, geometry.width, geometry.height,
                UiDraw.RADIUS_SMALL, theme.surfaceElevated());
        UiDraw.roundRectBorder(graphics, geometry.x, geometry.y, geometry.width,
                geometry.height, UiDraw.RADIUS_SMALL, 1, theme.borderStrong());
        int hovered = itemAt(mouseX, mouseY);
        for (int index = 0; index < items.size(); index++)
        {
            Item item = items.get(index);
            int rowY = geometry.y + PADDING + index * ROW_HEIGHT;
            if (index == hovered || index == highlight)
            {
                UiDraw.roundRect(graphics, geometry.x + 3, rowY, geometry.width - 6,
                        ROW_HEIGHT - 1, 5, UiDraw.withAlpha(
                                item.enabled() ? theme.accent() : theme.text(), 0x2E));
            }
            int color = item.enabled() ? theme.text() : theme.textFaint();
            int textLeft = geometry.x + 10;
            if (item.icon() != null)
            {
                IconAtlas.draw(graphics, item.icon(), textLeft,
                        rowY + (ROW_HEIGHT - 12) / 2, 12, color);
                textLeft += 18;
            }
            UiDraw.textClipped(graphics, font, item.label().getString(), textLeft,
                    rowY + (ROW_HEIGHT - font.lineHeight) / 2,
                    geometry.width - (textLeft - geometry.x) - 8, color, false);
        }
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean doubled)
    {
        if (!open)
        {
            return false;
        }
        if (event.button() == 0)
        {
            int index = itemAt(event.x(), event.y());
            if (index >= 0)
            {
                Item item = items.get(index);
                close();
                if (item.enabled() && item.action() != null)
                {
                    item.action().run();
                }
                return true;
            }
        }
        close();
        return true;
    }

    /** Scroll is intentionally not supported; the menu closes instead. */
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX,
                                 double scrollY)
    {
        return false;
    }

    /** Handles keyboard navigation while open. */
    public boolean keyPressed(KeyEvent event)
    {
        if (!open)
        {
            return false;
        }
        switch (event.key())
        {
            case 256 -> close();
            case 265 -> highlight = previousEnabled(highlight);
            case 264 -> highlight = nextEnabled(highlight);
            case 257, 32 -> activate(highlight);
            default -> {
                return true;
            }
        }
        return true;
    }

    /** The item index under the cursor, or {@code -1}. */
    public int itemAt(double mouseX, double mouseY)
    {
        Font font = font();
        if (!open || font == null)
        {
            return -1;
        }
        Geometry geometry = geometry(font);
        return itemAt(mouseX, mouseY, geometry.x, geometry.y, geometry.width, items.size());
    }

    /**
     * Pure item hit test.
     *
     * @return the item index, or {@code -1}
     */
    public static int itemAt(double mouseX, double mouseY, int x, int y, int width,
                             int count)
    {
        return net.weaver.minimap.ui.theme.UiMath.contextMenuItemAt(mouseX, mouseY, x, y,
                width, ROW_HEIGHT, PADDING, count);
    }

    private void activate(int index)
    {
        if (index < 0 || index >= items.size())
        {
            return;
        }
        Item item = items.get(index);
        close();
        if (item.enabled() && item.action() != null)
        {
            item.action().run();
        }
    }

    private int nextEnabled(int from)
    {
        for (int index = from + 1; index < items.size(); index++)
        {
            if (items.get(index).enabled())
            {
                return index;
            }
        }
        return from;
    }

    private int previousEnabled(int from)
    {
        for (int index = (from < 0 ? items.size() : from) - 1; index >= 0; index--)
        {
            if (items.get(index).enabled())
            {
                return index;
            }
        }
        return from;
    }

    private Geometry geometry(Font font)
    {
        int width = 0;
        for (Item item : items)
        {
            int labelWidth = font.width(item.label());
            width = Math.max(width, labelWidth + (item.icon() == null ? 20 : 38) + 12);
        }
        width = Math.max(140, Math.min(280, width));
        int height = items.size() * ROW_HEIGHT + PADDING * 2;
        int screenWidth = screenWidth();
        int screenHeight = screenHeight();
        int x = anchorX + 2;
        int y = anchorY + 2;
        if (x + width > screenWidth - 4)
        {
            x = anchorX - width - 2;
        }
        if (y + height > screenHeight - 4)
        {
            y = anchorY - height - 2;
        }
        x = UiDraw.clamp(x, 4, Math.max(4, screenWidth - width - 4));
        y = UiDraw.clamp(y, 4, Math.max(4, screenHeight - height - 4));
        return new Geometry(x, y, width, height);
    }

    private static Font font()
    {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null ? null : minecraft.font;
    }

    private static Theme theme()
    {
        return ThemeManager.get().current();
    }

    private static int screenWidth()
    {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null ? 320 : minecraft.getWindow().getGuiScaledWidth();
    }

    private static int screenHeight()
    {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null ? 240 : minecraft.getWindow().getGuiScaledHeight();
    }

    private record Geometry(int x, int y, int width, int height)
    {
    }
}
