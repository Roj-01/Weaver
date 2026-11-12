package net.weaver.minimap.ui.widget;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.weaver.minimap.render.IconAtlas;
import net.weaver.minimap.render.UiDraw;
import net.weaver.minimap.ui.theme.Theme;

/**
 * Square icon-only button used by toolbars and list rows.
 */
public class UiIconButton extends AbstractUiWidget
{
    private final Runnable onClick;
    private IconAtlas.Icon icon;
    private int tint = -1;
    private boolean pressed;

    /**
     * Creates an icon button.
     *
     * @param x       the left edge
     * @param y       the top edge
     * @param size    the square size
     * @param icon    the icon
     * @param onClick the action, possibly {@code null}
     */
    public UiIconButton(int x, int y, int size, IconAtlas.Icon icon, Runnable onClick)
    {
        super(x, y, size, size, Component.literal(icon == null ? "" : icon.name()));
        this.icon = icon;
        this.onClick = onClick == null ? () ->
        {
        } : onClick;
    }

    /** The current icon. */
    public IconAtlas.Icon icon()
    {
        return icon;
    }

    /** Replaces the icon. */
    public UiIconButton setIcon(IconAtlas.Icon icon)
    {
        this.icon = icon;
        setMessage(Component.literal(icon == null ? "" : icon.name()));
        return this;
    }

    /** An explicit ARGB tint, or {@code -1} for the theme default. */
    public UiIconButton setTint(int tint)
    {
        this.tint = tint;
        return this;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX,
                                            int mouseY, float partialTick)
    {
        Theme theme = theme();
        int x = getX();
        int y = getY();
        int size = Math.min(getWidth(), getHeight());
        int radius = Math.min(UiDraw.RADIUS_SMALL, size / 2);
        double hoverAmount = hoverValue();
        int background = UiDraw.withAlpha(theme.surfaceElevated(),
                (int) Math.round(0xCC * hoverAmount));
        if (pressed)
        {
            background = UiDraw.withAlpha(theme.accent(), 0x44);
        }
        if (UiDraw.alpha(background) > 0)
        {
            UiDraw.roundRect(graphics, x, y, size, size, radius, background);
        }
        int color = tint;
        if (color == -1)
        {
            color = UiDraw.mix(theme.textMuted(), theme.text(), hoverAmount);
        }
        if (!active)
        {
            color = theme.textFaint();
        }
        int iconSize = Math.max(8, size - Math.max(8, size / 3));
        if (icon != null)
        {
            IconAtlas.draw(graphics, icon, x + (size - iconSize) / 2,
                    y + (size - iconSize) / 2, iconSize, color);
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
        pressed = true;
        onClick.run();
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event)
    {
        pressed = false;
        return isMouseOver(event.x(), event.y());
    }

    @Override
    public boolean keyPressed(KeyEvent event)
    {
        if (!active || !isFocused())
        {
            return false;
        }
        if (event.key() == 257 || event.key() == 32)
        {
            onClick.run();
            return true;
        }
        return false;
    }
}
