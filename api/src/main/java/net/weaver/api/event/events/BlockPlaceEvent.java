package net.weaver.api.event.events;

import net.weaver.api.event.Event;

import java.util.Objects;

/**
 * A block item is about to place a block.
 *
 * <p>Fired at method entry to {@code BlockItem#place}, before the placement is
 * resolved. Result-based and not cancelable: setting a non-null result with
 * {@link #setResultOverride(Object)} replaces the method result and skips the
 * vanilla body; leaving it {@code null} lets vanilla run. The game casts the
 * replacement to {@code InteractionResult}, so listeners must set a value of
 * that type (the typed {@code api-game} event enforces it).
 *
 * <p>The spec is installed on the logical server only, but {@code BlockItem} is
 * a shared class: in a process that hosts an integrated client, client-side
 * invocations can also reach the injected body. The spec instruments the base
 * class body only; a subclass override that does not call {@code super}
 * bypasses the hook.
 *
 * <p>The base payloads are game objects carried as {@link Object}; the typed
 * {@code api-game} event exposes game types.
 */
public class BlockPlaceEvent extends Event {

    private final Object placeContext;
    private volatile Object resultOverride;

    /**
     * Creates a block-place event.
     *
     * @param placeContext the placement context
     */
    public BlockPlaceEvent(Object placeContext) {
        super(false, true);
        this.placeContext = Objects.requireNonNull(placeContext, "placeContext");
    }

    /** The placement context. */
    public Object placeContext() {
        return placeContext;
    }

    /**
     * The replacement interaction result chosen by a listener.
     *
     * @return the replacement result, or {@code null} to let vanilla run
     */
    public Object resultOverride() {
        return resultOverride;
    }

    /**
     * Replaces the vanilla placement result.
     *
     * <p>Setting a non-null result makes the hook skip the vanilla method body
     * and return that result directly. Setting {@code null} clears any previous
     * replacement and lets the vanilla placement run.
     *
     * @param result the replacement interaction result, or {@code null} to let
     *               vanilla run
     */
    public void setResultOverride(Object result) {
        this.resultOverride = result;
    }
}
