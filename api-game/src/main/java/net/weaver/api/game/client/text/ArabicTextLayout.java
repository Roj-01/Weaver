package net.weaver.api.game.client.text;

import com.ibm.icu.text.ArabicShaping;
import com.ibm.icu.text.ArabicShapingException;
import com.ibm.icu.text.Bidi;
import net.minecraft.client.gui.Font;

/**
 * Shapes Arabic text and maps logical cursor positions to visual coordinates.
 *
 * <p>The game ships ICU4J, so no extra dependency is required. The layout keeps
 * the original logical string untouched for networking; only the rendered form
 * and the cursor coordinates are transformed.
 */
public final class ArabicTextLayout {

    private static final int SHAPING_OPTIONS = 8;
    private static final int BIDI_FLAGS = 127;
    private static final int REORDER_MODE = 0;
    private static final int REORDER_OPTIONS = 2;

    private static final ThreadLocal<ArabicShaping> SHAPER =
            ThreadLocal.withInitial(() -> new ArabicShaping(SHAPING_OPTIONS));

    private ArabicTextLayout() {
    }

    public static String shape(String text) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        try {
            return SHAPER.get().shape(text);
        } catch (ArabicShapingException failure) {
            return text;
        }
    }

    /**
     * A shaping + bidi layout for one logical line.
     */
    public static final class Layout {

        private final String raw;
        private final String shaped;
        private final String visual;
        private final Bidi bidi;
        private final Font font;
        private final int[] visualIndex;

        public Layout(String raw, Font font) {
            this.raw = raw == null ? "" : raw;
            this.font = font;
            this.shaped = shape(this.raw);
            Bidi parsed = null;
            String reordered = this.shaped;
            try {
                parsed = new Bidi(this.shaped, BIDI_FLAGS);
                parsed.setReorderingMode(REORDER_MODE);
                reordered = parsed.writeReordered(REORDER_OPTIONS);
            } catch (Throwable ignored) {
                // Fall back to logical order; the text is still shaped.
            }
            this.bidi = parsed;
            this.visual = reordered == null ? this.shaped : reordered;
            this.visualIndex = new int[this.shaped.length() + 1];
            for (int index = 0; index < this.shaped.length(); index++) {
                this.visualIndex[index] = this.bidi == null
                        ? index : this.bidi.getVisualIndex(index);
            }
            this.visualIndex[this.shaped.length()] = this.visual.length();
        }

        /** The shaped, visually ordered string to draw. */
        public String visual() {
            return visual;
        }

        /** The visual width of the whole line. */
        public int width() {
            return font.width(visual);
        }

        /** The visual x offset of a cursor at {@code logicalIndex} in the raw string. */
        public int x(int logicalIndex) {
            int shapedIndex = shapedIndex(logicalIndex);
            int visualPosition = visualIndex[shapedIndex];
            if (visualPosition <= 0) {
                return 0;
            }
            return font.width(visual.substring(0, Math.min(visualPosition, visual.length())));
        }

        private int shapedIndex(int logicalIndex) {
            if (logicalIndex <= 0) {
                return 0;
            }
            if (logicalIndex >= raw.length()) {
                return shaped.length();
            }
            try {
                int shapedPrefix = shape(raw.substring(0, logicalIndex)).length();
                return Math.min(shapedPrefix, shaped.length());
            } catch (Throwable failure) {
                return Math.min(logicalIndex, shaped.length());
            }
        }
    }
}
