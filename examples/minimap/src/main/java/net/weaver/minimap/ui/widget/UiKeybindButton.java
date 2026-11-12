package net.weaver.minimap.ui.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.weaver.minimap.render.UiDraw;
import net.weaver.minimap.ui.theme.Theme;

import java.util.function.IntConsumer;

/**
 * Button that displays a bound key and can capture the next key press.
 *
 * <p>The owner decides when capture starts by setting {@link #setListening}
 * directly or by reacting to the click callback. During capture the next key is
 * forwarded to the optional capture consumer and rendered with an accent
 * outline.</p>
 */
public class UiKeybindButton extends AbstractUiWidget
{
    private final Runnable onClick;
    private String keyName;
    private boolean listening;
    private IntConsumer onCapture;

    /**
     * Creates a keybind button.
     *
     * @param x       the left edge
     * @param y       the top edge
     * @param width   the width
     * @param height  the height
     * @param keyName the current key label, possibly {@code null}
     * @param onClick the click callback, possibly {@code null}
     */
    public UiKeybindButton(int x, int y, int width, int height, String keyName,
                           Runnable onClick)
    {
        super(x, y, width, height, Component.literal(keyName == null ? "Unbound" : keyName));
        this.keyName = keyName;
        this.onClick = onClick == null ? () ->
        {
        } : onClick;
    }

    /** The displayed key name. */
    public String keyName()
    {
        return keyName;
    }

    /** Replaces the displayed key name without firing a callback. */
    public UiKeybindButton setKeyName(String keyName)
    {
        this.keyName = keyName;
        setMessage(Component.literal(keyName == null ? "Unbound" : keyName));
        return this;
    }

    /** Whether the button is waiting for a key. */
    public boolean isListening()
    {
        return listening;
    }

    /** Starts or stops capture. */
    public UiKeybindButton setListening(boolean listening)
    {
        this.listening = listening;
        return this;
    }

    /** Receives raw GLFW key codes during capture. */
    public UiKeybindButton setOnCapture(IntConsumer onCapture)
    {
        this.onCapture = onCapture;
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
        int background = UiDraw.mix(theme.surfaceAlt(), theme.surfaceElevated(), hoverValue());
        if (listening)
        {
            background = UiDraw.mix(theme.accentMuted(), theme.accent(), 0.35D);
        }
        UiDraw.roundRect(graphics, x, y, width, height, radius, background);
        UiDraw.roundRectBorder(graphics, x, y, width, height, radius, 1,
                listening ? theme.accent() : theme.border());
        String text = listening ? "Press a key\u2026" : keyName == null ? "Unbound" : keyName;
        int color = listening ? theme.accentContrast()
                : keyName == null ? theme.textFaint() : theme.text();
        UiDraw.textCenteredClipped(graphics, font, text, x + width / 2,
                y + (height - font.lineHeight) / 2, width - 12, color, false);
        drawFocusRing(graphics, radius);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled)
    {
        if (!active || event.button() != 0 || !isMouseOver(event.x(), event.y()))
        {
            return false;
        }
        listening = true;
        onClick.run();
        return true;
    }

    @Override
    public boolean capturesInput()
    {
        return listening;
    }

    @Override
    public boolean keyPressed(KeyEvent event)
    {
        if (!active || !listening)
        {
            return false;
        }
        listening = false;
        if (event.key() == 256)
        {
            return true;
        }
        if (onCapture != null)
        {
            onCapture.accept(event.key());
        }
        else
        {
            setKeyName(keyLabel(event.key()));
        }
        return true;
    }

    /**
     * Maps a GLFW key code to a short display label without a LWJGL dependency.
     *
     * @param key the GLFW key code
     * @return a human-readable label
     */
    public static String keyLabel(int key)
    {
        return net.weaver.minimap.ui.theme.UiMath.keyLabel(key);
    }
}
