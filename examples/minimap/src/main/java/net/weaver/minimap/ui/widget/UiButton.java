package net.weaver.minimap.ui.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.weaver.minimap.render.IconAtlas;
import net.weaver.minimap.render.UiDraw;
import net.weaver.minimap.ui.theme.Theme;

/**
 * Rounded, themed button with optional leading icon.
 *
 * <p>Supports hover and press animation, keyboard activation with Enter or
 * Space, a focus ring, and ellipsised labels when the button is narrow.</p>
 */
public class UiButton extends AbstractUiWidget
{
    /** The visual weight of a button. */
    public enum Variant
    {
        PRIMARY,
        SECONDARY,
        GHOST,
        DANGER
    }

    private final Runnable onClick;
    private Variant variant = Variant.SECONDARY;
    private IconAtlas.Icon icon;
    private boolean pressed;
    private Font cachedFont;
    private int cachedWidth = Integer.MIN_VALUE;
    private String cachedLabel;
    private String cachedResult;

    /**
     * Creates a button.
     *
     * @param x       the left edge
     * @param y       the top edge
     * @param width   the width
     * @param height  the height
     * @param label   the label
     * @param onClick the action, possibly {@code null}
     */
    public UiButton(int x, int y, int width, int height, Component label, Runnable onClick)
    {
        super(x, y, width, height, label == null ? Component.empty() : label);
        this.onClick = onClick == null ? () ->
        {
        } : onClick;
    }

    /** The button variant. */
    public Variant variant()
    {
        return variant;
    }

    /** Sets the button variant. */
    public UiButton setVariant(Variant variant)
    {
        this.variant = variant == null ? Variant.SECONDARY : variant;
        return this;
    }

    /** The leading icon, or {@code null}. */
    public IconAtlas.Icon icon()
    {
        return icon;
    }

    /** Sets the leading icon, or {@code null} to remove it. */
    public UiButton setIcon(IconAtlas.Icon icon)
    {
        this.icon = icon;
        return this;
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
        double hoverAmount = hoverValue();
        boolean enabled = active && visible;
        int background;
        int foreground;
        int border = theme.border();
        switch (variant)
        {
            case PRIMARY ->
            {
                background = UiDraw.mix(theme.accent(),
                        UiDraw.mix(theme.accent(), 0xFFFFFFFF, 0.10D), hoverAmount);
                foreground = theme.accentContrast();
                border = 0;
            }
            case DANGER ->
            {
                background = UiDraw.mix(theme.danger(),
                        UiDraw.mix(theme.danger(), 0xFFFFFFFF, 0.10D), hoverAmount);
                foreground = UiDraw.contrastText(theme.danger());
                border = 0;
            }
            case GHOST ->
            {
                background = UiDraw.withAlpha(theme.text(),
                        (int) Math.round(0x18 * hoverAmount));
                foreground = UiDraw.mix(theme.textMuted(), theme.text(), hoverAmount);
                border = 0;
            }
            case SECONDARY ->
            {
                background = UiDraw.mix(theme.surfaceAlt(),
                        theme.surfaceElevated(), hoverAmount);
                foreground = theme.text();
                border = UiDraw.mix(theme.border(), theme.borderStrong(), hoverAmount);
            }
            default ->
            {
                background = theme.surfaceAlt();
                foreground = theme.text();
            }
        }
        if (!enabled)
        {
            background = UiDraw.withAlpha(background, 0x66);
            foreground = theme.textFaint();
        }
        if (pressed)
        {
            background = UiDraw.mix(background, 0xFF000000, 0.12D);
        }
        UiDraw.roundRect(graphics, x, y, width, height, radius, background);
        if (border != 0)
        {
            UiDraw.roundRectBorder(graphics, x, y, width, height, radius, 1, border);
        }
        drawFocusRing(graphics, radius);

        int padding = Math.min(10, Math.max(4, width / 8));
        int iconSize = Math.min(height - 8, 16);
        int textLeft = x + padding;
        int textRight = x + width - padding;
        if (icon != null && iconSize >= 8 && width >= padding * 2 + iconSize + 24)
        {
            IconAtlas.draw(graphics, icon, textLeft, y + (height - iconSize) / 2,
                    iconSize, foreground);
            textLeft += iconSize + 6;
        }
        int available = Math.max(1, textRight - textLeft);
        String label = ellipsized(font, getMessage().getString(), available);
        int textY = y + (height - font.lineHeight) / 2 + (pressed ? 1 : 0);
        graphics.text(font, label, textLeft + (textRight - textLeft - font.width(label)) / 2,
                textY, foreground, false);
    }

    private String ellipsized(Font font, String value, int width)
    {
        if (font == cachedFont && width == cachedWidth && value.equals(cachedLabel)
                && cachedResult != null)
        {
            return cachedResult;
        }
        cachedFont = font;
        cachedWidth = width;
        cachedLabel = value;
        cachedResult = UiDraw.ellipsize(font, value, width);
        return cachedResult;
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
            pressed = true;
            onClick.run();
            pressed = false;
            return true;
        }
        return false;
    }
}
