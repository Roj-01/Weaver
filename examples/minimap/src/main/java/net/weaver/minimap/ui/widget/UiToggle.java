package net.weaver.minimap.ui.widget;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.weaver.minimap.render.UiDraw;
import net.weaver.minimap.ui.theme.Animation;
import net.weaver.minimap.ui.theme.Theme;

import java.util.function.Consumer;

/**
 * Animated on/off switch.
 *
 * <p>The knob slides over 140 ms and the track fills with the accent colour
 * while on. Click, Enter and Space all toggle the value.</p>
 */
public class UiToggle extends AbstractUiWidget
{
    private final Consumer<Boolean> onChange;
    private final Animation.Motion knob = new Animation.Motion(140L);
    private boolean value;

    /**
     * Creates a toggle.
     *
     * @param x        the left edge
     * @param y        the top edge
     * @param width    the width
     * @param height   the height
     * @param value    the initial value
     * @param onChange the change callback, possibly {@code null}
     */
    public UiToggle(int x, int y, int width, int height, boolean value,
                    Consumer<Boolean> onChange)
    {
        super(x, y, width, height, Component.literal(value ? "On" : "Off"));
        this.value = value;
        this.onChange = onChange == null ? ignored ->
        {
        } : onChange;
        this.knob.set(value, System.currentTimeMillis());
    }

    /** The current value. */
    public boolean value()
    {
        return value;
    }

    /** Sets the value without firing the callback. */
    public UiToggle setValue(boolean value)
    {
        if (this.value != value)
        {
            this.value = value;
            setMessage(Component.literal(value ? "On" : "Off"));
            knob.set(value, now());
        }
        return this;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX,
                                            int mouseY, float partialTick)
    {
        Theme theme = theme();
        int x = getX();
        int y = getY();
        int width = getWidth();
        int height = getHeight();
        int radius = Math.min(UiDraw.RADIUS_PILL, height / 2);
        double hoverAmount = hoverValue();
        double progress = knob.value(now());
        int offColor = UiDraw.mix(theme.surfaceAlt(), theme.surfaceElevated(), hoverAmount);
        int onColor = UiDraw.mix(theme.accentMuted(), theme.accent(), hoverAmount);
        int knobColor = UiDraw.mix(theme.textMuted(), theme.text(), progress);
        UiDraw.switchControl(graphics, x, y, width, height, progress, offColor, onColor,
                knobColor, UiDraw.withAlpha(theme.background(), 0x99));
        drawFocusRing(graphics, radius);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled)
    {
        if (!active || event.button() != 0 || !isMouseOver(event.x(), event.y()))
        {
            return false;
        }
        toggle();
        return true;
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
            toggle();
            return true;
        }
        return false;
    }

    private void toggle()
    {
        value = !value;
        setMessage(Component.literal(value ? "On" : "Off"));
        knob.set(value, now());
        onChange.accept(value);
    }
}
