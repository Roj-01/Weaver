package net.weaver.api.game.event.client;

import java.util.Objects;
import java.util.function.Function;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.weaver.api.event.Event;

/**
 * Fired on the client when a screen builds its widgets
 * ({@code Screen#init(int, int)}): when it opens and when the window is
 * resized. {@code PRE} fires before the screen's own {@code init()} and is
 * cancelable (a canceled event skips it, leaving the screen empty);
 * {@code POST} fires after, and {@link #addWidget} adds a button or other
 * widget to the screen.
 */
public final class ScreenInitEvent extends Event {
    /** Before or after the screen's own widgets. */
    public enum Phase {
        PRE,
        POST
    }

    private final Phase phase;
    private final Screen screen;
    private final Function<Object, Object> adder;

    public ScreenInitEvent(Phase phase, Screen screen, Function<Object, Object> adder) {
        super(phase == Phase.PRE);
        this.phase = Objects.requireNonNull(phase, "phase");
        this.screen = screen;
        this.adder = adder;
    }

    public Phase phase() {
        return phase;
    }

    public Screen screen() {
        return screen;
    }

    /**
     * Adds a widget that is drawn and receives input, as the screen's own
     * {@code addRenderableWidget} does.
     *
     * @throws IllegalStateException in the {@code PRE} phase
     */
    public <T extends GuiEventListener & Renderable & NarratableEntry> T addWidget(T widget) {
        if (phase != Phase.POST) {
            throw new IllegalStateException("widgets can only be added in the POST phase");
        }
        adder.apply(Objects.requireNonNull(widget, "widget"));
        return widget;
    }
}
