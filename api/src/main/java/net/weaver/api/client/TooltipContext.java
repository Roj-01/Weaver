package net.weaver.api.client;

import java.util.List;
import java.util.Objects;

/**
 * An immutable snapshot of one item tooltip request.
 *
 * <p>The snapshot holds defensive copies of the line list, but the referenced
 * game objects are not thread-safe. Use the context only on the render thread
 * and do not retain the game objects past the
 * {@link TooltipProvider#append(TooltipContext, List)} call.
 *
 * <p>The client, screen, and item stack values may be {@code null} when the
 * hook has no corresponding object. The existing line list is never
 * {@code null} and may be empty.
 *
 * @param minecraft    the client instance, or {@code null}
 * @param screen       the current screen, or {@code null}
 * @param itemStack    the stack being described, or {@code null}
 * @param existingLines an unmodifiable copy of the lines already collected,
 *                      never {@code null} and possibly empty
 */
public record TooltipContext(Object minecraft, Object screen, Object itemStack,
                             List<Object> existingLines)
{
    /**
     * Validates and defensively copies the existing line list.
     *
     * @throws NullPointerException when {@code existingLines} is {@code null}
     *                              or contains a {@code null} element
     */
    public TooltipContext
    {
        Objects.requireNonNull(existingLines, "existingLines");
        existingLines = List.copyOf(existingLines);
    }
}
