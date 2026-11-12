package net.weaver.api.command;

import java.util.List;

/**
 * Supplies command suggestions for an argument node.
 *
 * <p>The provider is called on the server thread while the game builds a
 * suggestion list. It must be fast and must not block. A {@code null} result is
 * treated as an empty suggestion list by callers; a non-null list should not
 * contain {@code null} entries.
 */
@FunctionalInterface
public interface CommandSuggestionProvider
{
    /**
     * Returns suggestions for the current input.
     *
     * @param context the suggestion context, not {@code null}
     * @return the suggestions, possibly {@code null} or empty
     */
    List<String> suggest(CommandSuggestionContext context);
}
