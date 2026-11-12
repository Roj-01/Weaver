package net.weaver.minimap.ui.widget;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.weaver.minimap.render.UiDraw;
import net.weaver.minimap.ui.theme.Theme;

/**
 * Scroll viewport helper.
 *
 * <p>The widget draws the scrollbar and owns the scroll offset. Content is
 * drawn by the caller between {@link #contentY()} and
 * {@link #contentHeight()}, ideally inside the guard returned by
 * {@link #scissor(GuiGraphicsExtractor)} so rows never bleed outside the
 * viewport.</p>
 */
public class UiScrollView extends AbstractUiWidget
{
    private static final int THUMB_TRACK_INSET = 4;
    private static final int MIN_THUMB = 24;

    private int contentHeight;
    private int scroll;
    private boolean draggingThumb;
    private int dragOffset;

    /**
     * Creates a scroll view.
     *
     * @param x             the left edge
     * @param y             the top edge
     * @param width         the width
     * @param height        the height
     * @param contentHeight the content height in pixels
     */
    public UiScrollView(int x, int y, int width, int height, int contentHeight)
    {
        super(x, y, width, height, Component.literal("Scrollable content"));
        this.contentHeight = Math.max(0, contentHeight);
    }

    /** The content height in pixels. */
    public int contentHeight()
    {
        return contentHeight;
    }

    /** Sets the content height and clamps the scroll offset. */
    public void setContentHeight(int contentHeight)
    {
        this.contentHeight = Math.max(0, contentHeight);
        setScrollOffset(scroll);
    }

    /** The current scroll offset in pixels. */
    public int scrollOffset()
    {
        return scroll;
    }

    /** Sets the scroll offset, clamped to the valid range. */
    public void setScrollOffset(int offset)
    {
        scroll = UiDraw.clamp(offset, 0, maxScroll());
    }

    /** The maximum scroll offset. */
    public int maxScroll()
    {
        return Math.max(0, contentHeight - getHeight());
    }

    /** The y coordinate where the first content pixel is drawn. */
    public int contentY()
    {
        return getY() - scroll;
    }

    /** Whether the content overflows the viewport. */
    public boolean canScroll()
    {
        return contentHeight > getHeight();
    }

    /** The visible content height. */
    public int visibleHeight()
    {
        return getHeight();
    }

    /** Enables a scissor over the viewport. */
    public net.weaver.minimap.ui.theme.ScissorGuard scissor(
            GuiGraphicsExtractor graphics)
    {
        return UiDraw.scissor(graphics, getX(), getY(), getWidth(), getHeight());
    }

    /** Draws the scrollbar when the content overflows. */
    public void drawScrollbar(GuiGraphicsExtractor graphics)
    {
        if (!canScroll())
        {
            return;
        }
        Theme theme = theme();
        int trackX = getX() + getWidth() - 6;
        int trackY = getY() + THUMB_TRACK_INSET;
        int trackHeight = Math.max(1, getHeight() - THUMB_TRACK_INSET * 2);
        int thumbHeight = thumbHeight(trackHeight);
        int thumbY = thumbY(trackY, trackHeight, thumbHeight);
        UiDraw.scrollbar(graphics, trackX, trackY, 3, trackHeight, thumbY, thumbHeight,
                UiDraw.withAlpha(theme.text(), 0x12),
                UiDraw.withAlpha(theme.text(), hoverValue() > 0.0D ? 0x88 : 0x55));
    }

    /** The current thumb y, useful for hit tests and tests. */
    public int thumbY()
    {
        int trackY = getY() + THUMB_TRACK_INSET;
        int trackHeight = Math.max(1, getHeight() - THUMB_TRACK_INSET * 2);
        return thumbY(trackY, trackHeight, thumbHeight(trackHeight));
    }

    /** The current thumb height. */
    public int thumbHeight()
    {
        int trackHeight = Math.max(1, getHeight() - THUMB_TRACK_INSET * 2);
        return thumbHeight(trackHeight);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX,
                                            int mouseY, float partialTick)
    {
        drawScrollbar(graphics);
        drawFocusRing(graphics, Math.min(UiDraw.RADIUS_SMALL, getHeight() / 2));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY)
    {
        if (!canScroll() || !UiDraw.inside(mouseX, mouseY, getX(), getY(), getWidth(),
                getHeight()) || scrollY == 0.0D)
        {
            return false;
        }
        setScrollOffset(scroll - (int) Math.round(scrollY * 24.0D));
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled)
    {
        if (!canScroll() || event.button() != 0)
        {
            return false;
        }
        int trackX = getX() + getWidth() - 8;
        int thumbTop = thumbY();
        int thumbSize = thumbHeight();
        if (UiDraw.hitScrollbar(event.x(), event.y(), trackX, getY(), 8, getHeight(),
                thumbTop, thumbSize))
        {
            draggingThumb = true;
            dragOffset = (int) Math.round(event.y()) - thumbTop;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY)
    {
        if (!draggingThumb)
        {
            return false;
        }
        int trackY = getY() + THUMB_TRACK_INSET;
        int trackHeight = Math.max(1, getHeight() - THUMB_TRACK_INSET * 2);
        int thumbHeight = thumbHeight(trackHeight);
        int travel = Math.max(1, trackHeight - thumbHeight);
        double ratio = (event.y() - dragOffset - trackY) / travel;
        setScrollOffset((int) Math.round(UiDraw.clamp01(ratio) * maxScroll()));
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event)
    {
        boolean wasDragging = draggingThumb;
        draggingThumb = false;
        return wasDragging;
    }

    @Override
    public boolean keyPressed(KeyEvent event)
    {
        if (!active || !isFocused() || !canScroll())
        {
            return false;
        }
        int next;
        switch (event.key())
        {
            case 266 -> next = scroll - getHeight();
            case 267 -> next = scroll + getHeight();
            case 268 -> next = 0;
            case 269 -> next = maxScroll();
            case 265 -> next = scroll - 24;
            case 264 -> next = scroll + 24;
            default -> {
                return false;
            }
        }
        setScrollOffset(next);
        return true;
    }

    private int thumbHeight(int trackHeight)
    {
        if (contentHeight <= 0)
        {
            return trackHeight;
        }
        return Math.min(trackHeight,
                Math.max(MIN_THUMB, trackHeight * getHeight() / contentHeight));
    }

    private int thumbY(int trackY, int trackHeight, int thumbHeight)
    {
        int travel = Math.max(0, trackHeight - thumbHeight);
        int range = maxScroll();
        return trackY + (range <= 0 ? 0 : travel * scroll / range);
    }
}
