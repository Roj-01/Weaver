package net.weaver.api.game.event.client;

import java.util.List;
import java.util.Objects;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.resources.Identifier;
import net.weaver.api.event.Event;

/**
 * Fired on the client when a tooltip is drawn ({@code GuiGraphicsExtractor#tooltip}).
 * {@code PRE} fires first and is cancelable (a canceled tooltip is not drawn);
 * {@link #setX(int)}, {@link #setY(int)}, and {@link #setFont(Font)} move it
 * or change its font. {@code BACKGROUND} fires when the background is drawn;
 * {@link #setBackground(Identifier)} picks another sprite ({@code null} is
 * vanilla's). To add or change lines, use {@code TooltipRegistry}.
 */
public final class RenderTooltipEvent extends Event {
    /** Which step of drawing. */
    public enum Phase {
        PRE,
        BACKGROUND
    }

    private final Phase phase;
    private final GuiGraphicsExtractor graphics;
    private final List<ClientTooltipComponent> lines;
    private Font font;
    private int x;
    private int y;
    private Identifier background;

    public RenderTooltipEvent(Phase phase, GuiGraphicsExtractor graphics, List<ClientTooltipComponent> lines, Font font,
                              int x, int y, Identifier background) {
        super(phase == Phase.PRE);
        this.phase = Objects.requireNonNull(phase, "phase");
        this.graphics = graphics;
        this.lines = lines;
        this.font = font;
        this.x = x;
        this.y = y;
        this.background = background;
    }

    public Phase phase() {
        return phase;
    }

    public GuiGraphicsExtractor graphics() {
        return graphics;
    }

    /** The lines, read-only. */
    public List<ClientTooltipComponent> lines() {
        return List.copyOf(lines);
    }

    public Font font() {
        return font;
    }

    public void setFont(Font font) {
        this.font = Objects.requireNonNull(font, "font");
    }

    /** Mouse x for {@code PRE}; the positioned left edge for {@code BACKGROUND}. */
    public int x() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    /** Mouse y for {@code PRE}; the positioned top edge for {@code BACKGROUND}. */
    public int y() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    /** The background sprite, or {@code null} for vanilla's. */
    public Identifier background() {
        return background;
    }

    public void setBackground(Identifier background) {
        this.background = background;
    }
}
