package net.weaver.minimap.ui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.weaver.minimap.render.UiDraw;
import net.weaver.minimap.ui.theme.Animation;
import net.weaver.minimap.ui.theme.Theme;
import net.weaver.minimap.ui.theme.ThemeManager;

/**
 * Shared base for the custom minimap widgets.
 *
 * <p>The base adds theme lookup, hover timing, focus rings and narration on top
 * of the vanilla {@link AbstractWidget} contract. Subclasses only override
 * {@link #extractWidgetRenderState(GuiGraphicsExtractor, int, int, float)} and
 * the input methods they need.</p>
 */
public abstract class AbstractUiWidget extends AbstractWidget
{
    /** Hover transition duration from the visual specification. */
    protected static final long HOVER_MILLIS = 120L;

    /** Animated hover state in 0..1. */
    protected final Animation.Motion hover = new Animation.Motion(HOVER_MILLIS);

    protected AbstractUiWidget(int x, int y, int width, int height, Component message)
    {
        super(x, y, width, height, message == null ? Component.empty() : message);
    }

    /** The active theme. */
    protected Theme theme()
    {
        return ThemeManager.get().current();
    }

    /** The client font, or {@code null} when no client is available. */
    protected Font font()
    {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null ? null : minecraft.font;
    }

    /** The wall clock used by animations. */
    protected long now()
    {
        return System.currentTimeMillis();
    }

    /** Updates and returns the animated hover value. */
    protected double hoverValue()
    {
        long time = now();
        hover.set(isHovered() && active, time);
        return hover.value(time);
    }

    /** Draws the keyboard focus ring using the theme accent. */
    protected void drawFocusRing(GuiGraphicsExtractor graphics, int radius)
    {
        if (!isFocused())
        {
            return;
        }
        UiDraw.roundRectBorder(graphics, getX() - 2, getY() - 2, getWidth() + 4,
                getHeight() + 4, radius + 2, 2,
                UiDraw.withAlpha(theme().accent(), 0xCC));
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output)
    {
        output.add(NarratedElementType.TITLE, getMessage());
    }
}
