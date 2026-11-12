package net.weaver.api.game.client.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.TextCursorUtils;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.weaver.api.game.client.text.ArabicTextLayout;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Renders {@link EditBox} text through Arabic shaping + bidi and maps the
 * logical cursor/selection to visual coordinates.
 *
 * <p>The value stored by {@code EditBox} is never changed: the server still
 * receives the original logical text. Only the text, cursor, and selection
 * rendering are replaced.
 */
@Mixin(EditBox.class)
@SuppressWarnings("deprecation")
public abstract class ArabicEditBoxMixin {

    @Shadow private String value;
    @Shadow private int displayPos;
    @Shadow private int cursorPos;
    @Shadow private int highlightPos;
    @Shadow private int textX;
    @Shadow private int textY;
    @Shadow private int textColor;
    @Shadow private int textColorUneditable;
    @Shadow private int maxLength;
    @Shadow private boolean textShadow;
    @Shadow private boolean invertHighlightedTextColor;
    @Shadow private long focusedTime;
    @Shadow private Font font;
    @Shadow private boolean isEditable;

    @Shadow public abstract int getInnerWidth();

    @Shadow public abstract boolean isVisible();

    private AbstractWidget widget() {
        return (AbstractWidget) (Object) this;
    }

    @Redirect(method = "extractWidgetRenderState",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V"))
    private void weaverArabicSkipText(GuiGraphicsExtractor graphics, Font font,
                                      FormattedCharSequence text, int x, int y,
                                      int color, boolean shadow) {
        // Drawn by the TAIL overlay in shaped visual order.
    }

    @Redirect(method = "extractWidgetRenderState",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;textHighlight(IIIIZ)V"))
    private void weaverArabicSkipHighlight(GuiGraphicsExtractor graphics, int x1, int y1,
                                           int x2, int y2, boolean invert) {
        // Drawn by the TAIL overlay with logical-to-visual mapping.
    }

    @Redirect(method = "extractWidgetRenderState",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/TextCursorUtils;extractInsertCursor(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIII)V"))
    private void weaverArabicSkipInsertCursor(GuiGraphicsExtractor graphics, int x, int y,
                                              int color, int height) {
        // Drawn by the TAIL overlay.
    }

    @Redirect(method = "extractWidgetRenderState",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/TextCursorUtils;extractAppendCursor(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIIZ)V"))
    private void weaverArabicSkipAppendCursor(GuiGraphicsExtractor graphics, Font font,
                                              int x, int y, int color, boolean shadow) {
        // Drawn by the TAIL overlay.
    }

    @Inject(method = "extractWidgetRenderState", at = @At("TAIL"))
    private void weaverArabicRender(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                    float partialTick, CallbackInfo callback) {
        if (!isVisible()) {
            return;
        }
        int start = Math.max(0, Math.min(displayPos, value.length()));
        String raw = value.substring(start);
        if (raw.isEmpty()) {
            return;
        }

        ArabicTextLayout.Layout layout = new ArabicTextLayout.Layout(raw, font);
        String visual = layout.visual();
        String visible = font.plainSubstrByWidth(visual, getInnerWidth());
        int color = isEditable ? textColor : textColorUneditable;
        graphics.text(font, FormattedCharSequence.forward(visible, Style.EMPTY),
                textX, textY, color, textShadow);

        int maxX = textX + Math.max(0, getInnerWidth());
        int cursorRel = Mth.clamp(cursorPos - start, 0, raw.length());
        int cursorX = Mth.clamp(textX + layout.x(cursorRel), textX, maxX);
        int highlightRel = Mth.clamp(highlightPos - start, 0, raw.length());
        int highlightX = Mth.clamp(textX + layout.x(highlightRel), textX, maxX);

        if (highlightX != cursorX) {
            int left = Math.min(cursorX, highlightX);
            int right = Math.max(cursorX, highlightX);
            graphics.textHighlight(left, textY - 1, right, textY + 9,
                    invertHighlightedTextColor);
        }

        if (widget().isFocused()
                && TextCursorUtils.isCursorVisible(Util.getMillis() - focusedTime)) {
            boolean atEnd = cursorPos >= value.length() || value.length() >= maxLength;
            if (atEnd) {
                TextCursorUtils.extractInsertCursor(graphics, cursorX, textY, color, 10);
            } else {
                TextCursorUtils.extractAppendCursor(graphics, font, cursorX, textY, color,
                        textShadow);
            }
        }
    }

    /**
     * Finds the logical cursor position nearest to the mouse click by comparing
     * visual cursor coordinates instead of raw logical widths.
     */
    @Overwrite
    private int findClickedPositionInText(MouseButtonEvent event) {
        int start = Math.max(0, Math.min(displayPos, value.length()));
        String raw = value.substring(start);
        if (raw.isEmpty()) {
            return start;
        }
        ArabicTextLayout.Layout layout = new ArabicTextLayout.Layout(raw, font);
        int relativeX = (int) event.x() - textX;
        int bestIndex = 0;
        int bestDistance = Integer.MAX_VALUE;
        for (int index = 0; index <= raw.length(); index++) {
            int distance = Math.abs(layout.x(index) - relativeX);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = index;
            }
        }
        return Math.min(value.length(), start + bestIndex);
    }
}
