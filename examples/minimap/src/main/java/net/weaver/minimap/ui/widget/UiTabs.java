package net.weaver.minimap.ui.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.weaver.minimap.render.UiDraw;
import net.weaver.minimap.ui.theme.Theme;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * Horizontal tab strip.
 *
 * <p>The selected tab is raised with an accent underline. Arrow keys move the
 * selection and immediately notifies the callback; the label is clipped per
 * tab so narrow strips stay readable.</p>
 */
public class UiTabs extends AbstractUiWidget
{
    private final List<String> tabs;
    private final IntConsumer onChange;
    private int selected;

    /**
     * Creates a tab strip.
     *
     * @param x        the left edge
     * @param y        the top edge
     * @param width    the width
     * @param height   the height
     * @param tabs     the tab labels, never {@code null}
     * @param selected the selected index
     * @param onChange the selection callback, possibly {@code null}
     */
    public UiTabs(int x, int y, int width, int height, List<String> tabs, int selected,
                  IntConsumer onChange)
    {
        super(x, y, width, height, Component.literal(selectedLabel(tabs, selected)));
        this.tabs = tabs == null ? List.of() : List.copyOf(tabs);
        this.selected = this.tabs.isEmpty() ? 0
                : UiDraw.clamp(selected, 0, this.tabs.size() - 1);
        this.onChange = onChange == null ? ignored ->
        {
        } : onChange;
    }

    /** The selected index. */
    public int selected()
    {
        return selected;
    }

    /** Selects an index without firing the callback. */
    public UiTabs setSelected(int index)
    {
        selected = tabs.isEmpty() ? 0 : UiDraw.clamp(index, 0, tabs.size() - 1);
        setMessage(Component.literal(selectedLabel(tabs, selected)));
        return this;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX,
                                            int mouseY, float partialTick)
    {
        Font font = font();
        if (font == null || tabs.isEmpty())
        {
            return;
        }
        Theme theme = theme();
        int x = getX();
        int y = getY();
        int width = getWidth();
        int height = getHeight();
        int radius = Math.min(UiDraw.RADIUS_SMALL, height / 2);
        UiDraw.roundRect(graphics, x, y, width, height, radius, theme.surfaceAlt());
        UiDraw.roundRectBorder(graphics, x, y, width, height, radius, 1, theme.border());
        int hovered = tabIndexAt(mouseX, mouseY, x, y, width, height, tabs.size());
        for (int index = 0; index < tabs.size(); index++)
        {
            int left = tabLeft(index);
            int right = tabRight(index);
            boolean isSelected = index == selected;
            if (isSelected)
            {
                UiDraw.roundRect(graphics, left + 2, y + 2, right - left - 4, height - 4,
                        Math.max(2, radius - 2), theme.surfaceElevated());
                UiDraw.roundRect(graphics, left + 6, y + height - 3, right - left - 12, 2,
                        1, theme.accent());
            }
            else if (index == hovered)
            {
                UiDraw.roundRect(graphics, left + 2, y + 2, right - left - 4, height - 4,
                        Math.max(2, radius - 2), UiDraw.withAlpha(theme.text(), 0x12));
            }
            int color = isSelected ? theme.text()
                    : index == hovered ? theme.textMuted() : theme.textFaint();
            UiDraw.textCenteredClipped(graphics, font, tabs.get(index),
                    (left + right) / 2, y + (height - font.lineHeight) / 2,
                    Math.max(1, right - left - 10), color, false);
        }
        drawFocusRing(graphics, radius);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled)
    {
        if (!active || event.button() != 0)
        {
            return false;
        }
        int index = tabIndexAt(event.x(), event.y(), getX(), getY(), getWidth(),
                getHeight(), tabs.size());
        if (index < 0)
        {
            return false;
        }
        select(index);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event)
    {
        if (!active || !isFocused() || tabs.isEmpty())
        {
            return false;
        }
        int next;
        switch (event.key())
        {
            case 263 -> next = selected - 1;
            case 262 -> next = selected + 1;
            case 268 -> next = 0;
            case 269 -> next = tabs.size() - 1;
            default -> {
                return false;
            }
        }
        select(UiDraw.clamp(next, 0, tabs.size() - 1));
        return true;
    }

    /**
     * Resolves a tab index under the cursor.
     *
     * @param mouseX the cursor x
     * @param mouseY the cursor y
     * @param x      the strip x
     * @param y      the strip y
     * @param width  the strip width
     * @param height the strip height
     * @param count  the tab count
     * @return the tab index, or {@code -1}
     */
    public static int tabIndexAt(double mouseX, double mouseY, int x, int y, int width,
                                 int height, int count)
    {
        return net.weaver.minimap.ui.theme.UiMath.tabIndexAt(mouseX, mouseY, x, y, width,
                height, count);
    }

    private void select(int index)
    {
        if (index == selected)
        {
            return;
        }
        selected = index;
        setMessage(Component.literal(selectedLabel(tabs, selected)));
        onChange.accept(index);
    }

    private int tabLeft(int index)
    {
        return getX() + (int) (((long) getWidth() * index) / Math.max(1, tabs.size()));
    }

    private int tabRight(int index)
    {
        return getX() + (int) (((long) getWidth() * (index + 1)) / Math.max(1, tabs.size()));
    }

    private static String selectedLabel(List<String> tabs, int selected)
    {
        if (tabs == null || tabs.isEmpty())
        {
            return "";
        }
        return tabs.get(UiDraw.clamp(selected, 0, tabs.size() - 1));
    }
}
