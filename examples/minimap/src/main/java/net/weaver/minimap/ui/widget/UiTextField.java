package net.weaver.minimap.ui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.network.chat.Component;
import net.weaver.minimap.render.UiDraw;
import net.weaver.minimap.ui.theme.Theme;

import java.util.function.Consumer;

/**
 * Themed single-line text field wrapping a vanilla {@link EditBox}.
 *
 * <p>The outer widget owns the rounded panel, border and focus ring; the
 * embedded edit box is borderless and offset by 8 px. The edit box is created
 * lazily on the first paint so construction never touches the client.</p>
 */
public class UiTextField extends AbstractUiWidget
{
    private final Consumer<String> onChange;
    private EditBox editBox;
    private String value;
    private String hint = "";
    private int maxLength = 256;
    private boolean suppressChange;

    /**
     * Creates a text field.
     *
     * @param x        the left edge
     * @param y        the top edge
     * @param width    the width
     * @param height   the height
     * @param initial  the initial text
     * @param onChange the change callback, possibly {@code null}
     */
    public UiTextField(int x, int y, int width, int height, String initial,
                       Consumer<String> onChange)
    {
        super(x, y, width, height, Component.literal(initial == null ? "" : initial));
        this.value = initial == null ? "" : initial;
        this.onChange = onChange == null ? ignored ->
        {
        } : onChange;
    }

    /** The current text. */
    public String value()
    {
        return editBox == null ? value : editBox.getValue();
    }

    /** Replaces the text without firing the callback. */
    public UiTextField setValue(String text)
    {
        value = text == null ? "" : text;
        if (editBox != null)
        {
            suppressChange = true;
            try
            {
                editBox.setValue(value);
            }
            finally
            {
                suppressChange = false;
            }
        }
        return this;
    }

    /** The placeholder hint. */
    public String hint()
    {
        return hint;
    }

    /** Sets the placeholder hint. */
    public UiTextField setHint(String hint)
    {
        this.hint = hint == null ? "" : hint;
        if (editBox != null)
        {
            editBox.setHint(Component.literal(this.hint));
        }
        return this;
    }

    /** The maximum text length. */
    public int maxLength()
    {
        return maxLength;
    }

    /** Sets the maximum text length. */
    public UiTextField setMaxLength(int maxLength)
    {
        this.maxLength = Math.max(1, maxLength);
        if (editBox != null)
        {
            editBox.setMaxLength(this.maxLength);
        }
        return this;
    }

    /** The embedded edit box, or {@code null} before the first paint. */
    public EditBox editBox()
    {
        return editBox;
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
        int radius = Math.min(UiDraw.RADIUS_SMALL, getHeight() / 2);
        UiDraw.roundRect(graphics, getX(), getY(), getWidth(), getHeight(), radius,
                theme.surfaceAlt());
        UiDraw.roundRectBorder(graphics, getX(), getY(), getWidth(), getHeight(), radius, 1,
                isFocused() ? theme.accent() : theme.border());
        EditBox box = ensureEditBox();
        if (box == null)
        {
            return;
        }
        layout(box);
        box.setTextColor(theme.text());
        box.setTextColorUneditable(theme.textFaint());
        box.setFocused(isFocused());
        box.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled)
    {
        if (!active || !isMouseOver(event.x(), event.y()))
        {
            return false;
        }
        setFocused(true);
        EditBox box = ensureEditBox();
        if (box != null)
        {
            layout(box);
            box.mouseClicked(event, doubled);
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event)
    {
        EditBox box = ensureEditBox();
        return box != null && isFocused() && box.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event)
    {
        EditBox box = ensureEditBox();
        return box != null && isFocused() && box.charTyped(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY)
    {
        EditBox box = ensureEditBox();
        return box != null && box.isFocused() && box.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event)
    {
        EditBox box = ensureEditBox();
        return box != null && box.isFocused() && box.mouseReleased(event);
    }

    @Override
    public boolean preeditUpdated(PreeditEvent event)
    {
        EditBox box = ensureEditBox();
        return box != null && isFocused() && box.preeditUpdated(event);
    }

    @Override
    public void setFocused(boolean focused)
    {
        super.setFocused(focused);
        if (editBox != null)
        {
            editBox.setFocused(focused);
        }
    }

    @Override
    public boolean capturesInput()
    {
        return isFocused();
    }

    private void layout(EditBox box)
    {
        box.setX(getX() + 8);
        box.setY(getY() + (getHeight() - 8) / 2);
        box.setWidth(Math.max(1, getWidth() - 16));
        box.setHeight(8);
    }

    private EditBox ensureEditBox()
    {
        if (editBox != null)
        {
            return editBox;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null)
        {
            return null;
        }
        EditBox box = new EditBox(minecraft.font, Math.max(1, getWidth() - 16), 8,
                Component.empty());
        box.setBordered(false);
        box.setCanLoseFocus(true);
        box.setMaxLength(maxLength);
        box.setValue(value);
        if (!hint.isEmpty())
        {
            box.setHint(Component.literal(hint));
        }
        box.setResponder(next ->
        {
            value = next;
            if (!suppressChange)
            {
                onChange.accept(next);
            }
        });
        editBox = box;
        return box;
    }
}
