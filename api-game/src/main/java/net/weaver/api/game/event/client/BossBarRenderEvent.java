package net.weaver.api.game.event.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.weaver.api.event.Event;

/**
 * Fired on the client for each boss bar the HUD is about to draw
 * ({@code BossHealthOverlay#extractRenderState}). Cancelable: a canceled bar
 * draws neither the bar nor its name; the space stays reserved, so draw a
 * replacement at {@link #x()}, {@link #y()} through {@link #graphics()}.
 */
public final class BossBarRenderEvent extends Event {
    private final GuiGraphicsExtractor graphics;
    private final LerpingBossEvent bossEvent;
    private final int x;
    private final int y;

    public BossBarRenderEvent(GuiGraphicsExtractor graphics, LerpingBossEvent bossEvent, int x, int y) {
        super(true);
        this.graphics = graphics;
        this.bossEvent = bossEvent;
        this.x = x;
        this.y = y;
    }

    public GuiGraphicsExtractor graphics() {
        return graphics;
    }

    /** The boss bar: name, progress, color, and style. */
    public LerpingBossEvent bossEvent() {
        return bossEvent;
    }

    /** Left edge of the bar. */
    public int x() {
        return x;
    }

    /** Top edge of the bar; the name is drawn 9 pixels above it. */
    public int y() {
        return y;
    }
}
