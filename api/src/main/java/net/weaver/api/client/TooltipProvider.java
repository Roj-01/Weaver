package net.weaver.api.client;

import java.util.List;

/**
 * Contributes plain-text lines to an item tooltip.
 *
 * <p>Providers are registered through
 * {@link ClientRegistry#registerTooltipProvider(TooltipProvider)}. The backend
 * calls {@link #append(TooltipContext, List)} on the render thread with the
 * current context and a mutable line list; the facade's
 * {@link ClientRegistry#tooltipLines(TooltipContext)} entry point returns the
 * collected lines.
 */
public interface TooltipProvider
{
    /**
     * The unique provider id.
     *
     * @return the non-null, non-blank provider id
     */
    String id();

    /**
     * Appends zero or more plain-text lines.
     *
     * <p>Called on the render thread only. Each line is plain text without
     * formatting codes. The provider may append nothing, must not retain the
     * {@code lines} list, and must not retain any game object reachable from
     * the context after the call returns. Providers should not mutate existing
     * entries; append new lines only.
     *
     * @param context the immutable tooltip snapshot, never {@code null}
     * @param lines   the mutable list owned by the caller, never {@code null}
     */
    void append(TooltipContext context, List<String> lines);
}
