package net.weaver.minimap.ui.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.weaver.minimap.render.UiDraw;
import net.weaver.minimap.ui.theme.Theme;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Horizontal slider with an optional value label.
 *
 * <p>Click or drag the track to change the value, or use the arrow keys, Home
 * and End while focused. The label function receives the live value and may
 * return {@code null} to hide the label.</p>
 */
public class UiSlider extends AbstractUiWidget
{
    private final double min;
    private final double max;
    private final Consumer<Double> onChange;
    private final Function<Double, String> label;
    private double value;
    private double step;
    private boolean dragging;
    private int trackStart;
    private int trackWidth;

    /**
     * Creates a slider.
     *
     * @param x        the left edge
     * @param y        the top edge
     * @param width    the width
     * @param height   the height
     * @param min      the minimum value
     * @param max      the maximum value
     * @param value    the initial value, clamped
     * @param onChange the change callback, possibly {@code null}
     * @param label    the label function, possibly {@code null}
     */
    public UiSlider(int x, int y, int width, int height, double min, double max, double value,
                    Consumer<Double> onChange, Function<Double, String> label)
    {
        super(x, y, width, height, Component.literal(Double.toString(value)));
        this.min = min;
        this.max = Math.max(min, max);
        this.value = UiDraw.clamp(value, this.min, this.max);
        this.onChange = onChange == null ? ignored ->
        {
        } : onChange;
        this.label = label;
        this.step = (this.max - this.min) / 100.0D;
    }

    /** The current value. */
    public double value()
    {
        return value;
    }

    /** Sets the value without firing the callback. */
    public UiSlider setValue(double value)
    {
        this.value = UiDraw.clamp(value, min, max);
        return this;
    }

    /** The keyboard increment. */
    public double step()
    {
        return step;
    }

    /** Sets the keyboard increment. */
    public UiSlider setStep(double step)
    {
        this.step = step <= 0.0D ? (max - min) / 100.0D : step;
        return this;
    }

    /** Maps a mouse position to a value, using the widget's track geometry. */
    public double valueAtX(double mouseX)
    {
        updateTrack(font());
        return UiDraw.sliderValue(mouseX, trackStart, trackWidth, min, max);
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
        updateTrack(font);
        int handleSize = handleSize();
        int trackY = getY() + getHeight() / 2;
        double progress = max <= min ? 0.0D : (value - min) / (max - min);
        double hoverAmount = hoverValue();
        int trackColor = UiDraw.mix(theme.surfaceAlt(), theme.surfaceElevated(), hoverAmount);
        int handleColor = UiDraw.mix(theme.text(), theme.accent(), hoverAmount);
        UiDraw.sliderControl(graphics, trackStart, trackY - 2, trackWidth, 4, progress,
                trackColor, theme.accent(), handleColor,
                UiDraw.withAlpha(theme.background(), 0xAA), handleSize);
        String text = label == null ? null : label.apply(value);
        if (text != null && !text.isEmpty())
        {
            UiDraw.textRight(graphics, font, text, getX() + getWidth(),
                    getY() + (getHeight() - font.lineHeight) / 2, theme.textMuted(), false);
        }
        drawFocusRing(graphics, Math.min(UiDraw.RADIUS_SMALL, getHeight() / 2));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled)
    {
        if (!active || event.button() != 0 || !isMouseOver(event.x(), event.y()))
        {
            return false;
        }
        dragging = true;
        apply(valueAtX(event.x()));
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY)
    {
        if (!dragging)
        {
            return false;
        }
        apply(valueAtX(event.x()));
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event)
    {
        boolean wasDragging = dragging;
        dragging = false;
        return wasDragging;
    }

    @Override
    public boolean keyPressed(KeyEvent event)
    {
        if (!active || !isFocused())
        {
            return false;
        }
        double next;
        switch (event.key())
        {
            case 263 -> next = value - step;
            case 262 -> next = value + step;
            case 268 -> next = min;
            case 269 -> next = max;
            default -> {
                return false;
            }
        }
        apply(next);
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output)
    {
        String text = label == null ? Double.toString(value) : label.apply(value);
        output.add(NarratedElementType.TITLE,
                Component.literal(text == null ? "" : text));
    }

    private void apply(double next)
    {
        double clamped = UiDraw.clamp(next, min, max);
        if (clamped == value)
        {
            return;
        }
        value = clamped;
        onChange.accept(value);
    }

    private void updateTrack(Font font)
    {
        int handle = handleSize();
        int labelWidth = 0;
        if (font != null && label != null)
        {
            String text = label.apply(value);
            if (text != null && !text.isEmpty())
            {
                labelWidth = font.width(text) + 8;
            }
        }
        trackStart = getX() + handle / 2;
        trackWidth = Math.max(1, getWidth() - handle - labelWidth);
    }

    private int handleSize()
    {
        return Math.max(8, Math.min(14, getHeight()));
    }
}
