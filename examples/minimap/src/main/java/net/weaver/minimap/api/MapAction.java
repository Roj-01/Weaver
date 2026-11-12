package net.weaver.minimap.api;

import java.util.Objects;

/**
 * A command contributed to the fullscreen map UI.
 *
 * <p>{@link MinimapApi} wraps {@link #invoke()} with failure isolation before
 * returning actions to the renderer, so a throwing action cannot break the
 * map screen.</p>
 *
 * @param id                  the action id, non-blank
 * @param label               the display label, non-blank
 * @param icon                the optional icon id
 * @param requiresConfirmation whether the UI should confirm before invoking
 * @param invoke              the action body, never {@code null}
 */
public record MapAction(String id, String label, String icon,
                        boolean requiresConfirmation, Runnable invoke)
{
    /**
     * Validates the fields.
     *
     * @throws NullPointerException     when a required field is {@code null}
     * @throws IllegalArgumentException when {@code id} or {@code label} is
     *                                  blank
     */
    public MapAction
    {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(invoke, "invoke");
        if (id.isBlank())
        {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (label.isBlank())
        {
            throw new IllegalArgumentException("label must not be blank");
        }
        icon = icon == null ? "" : icon;
    }
}
