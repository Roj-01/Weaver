package net.weaver.api.game.client.util;

/**
 * Common contract for the ordered, identified listeners used by the built-in
 * client render registries.
 *
 * <p>Every listener has a stable id for diagnostics and duplicate detection and
 * an order used for dispatch. Lower order values run first. Equal orders keep
 * registration order.
 *
 * <p>Listener callbacks run on the client/render thread from the built-in
 * mixins. The base interface carries no game types, so the game-free API module
 * remains free of Minecraft classes.
 */
public interface OrderedClientListener
{
    /**
     * The unique listener id.
     *
     * @return the non-null, non-blank listener id
     */
    String id();

    /**
     * The dispatch order; lower values run first.
     *
     * @return the dispatch order
     */
    default int order()
    {
        return 0;
    }
}
