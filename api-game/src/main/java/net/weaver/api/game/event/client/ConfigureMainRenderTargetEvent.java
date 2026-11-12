package net.weaver.api.game.event.client;

import net.weaver.api.event.Event;

/**
 * Fired once on the client while the main render target (the window frame
 * buffer) is created, before its attachments are allocated. Call
 * {@link #enableStencil()} to give it a depth-stencil attachment
 * ({@code D32_FLOAT_S8_UINT}) instead of depth only. Register the listener
 * during mod initialization: the target is created with the game renderer.
 */
public final class ConfigureMainRenderTargetEvent extends Event {
    private boolean stencil;

    public ConfigureMainRenderTargetEvent() {
    }

    /** Requests a stencil attachment on the main render target. */
    public void enableStencil() {
        this.stencil = true;
    }

    public boolean stencilEnabled() {
        return stencil;
    }
}
