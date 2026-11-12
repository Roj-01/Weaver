package net.weaver.api.game.event;

import net.weaver.api.event.Side;

/**
 * Typed variant of {@link net.weaver.api.event.events.ResourceReloadEvent}.
 *
 * <p>The source payload is intentionally heterogeneous: the same hook reports a
 * {@code Minecraft}, {@code MinecraftServer}, or {@code PlayerList} depending on
 * the reload path, so this class does not narrow {@link #source()} and keeps the
 * inherited {@link Object}-typed accessor.
 */
public final class ResourceReloadEvent extends net.weaver.api.event.events.ResourceReloadEvent {

    /**
     * Creates a typed resource-reload event.
     *
     * @param source the reload source, one of the game objects for the path
     * @param side   the logical side performing the reload
     * @param phase  the reload phase
     */
    public ResourceReloadEvent(Object source, Side side, Phase phase) {
        super(source, side, phase);
    }
}
