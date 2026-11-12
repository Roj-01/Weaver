package net.weaver.api.game.event.client;

import net.minecraft.client.gui.components.toasts.Toast;
import net.weaver.api.event.Event;

/**
 * Fired on the client when a toast (advancement, recipe, tutorial, system
 * message) is queued ({@code ToastManager#addToast}). Cancelable: a canceled
 * toast is not shown.
 */
public final class ToastAddEvent extends Event {
    private final Toast toast;

    public ToastAddEvent(Toast toast) {
        super(true);
        this.toast = toast;
    }

    public Toast toast() {
        return toast;
    }
}
