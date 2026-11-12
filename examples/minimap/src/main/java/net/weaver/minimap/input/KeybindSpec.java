package net.weaver.minimap.input;

import net.weaver.api.client.KeyMappingSpec;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A pure description of one minimap key mapping.
 *
 * <p>The spec does not register anything with the loader. The integrator
 * converts it with {@link #toMappingSpec(String, int)} or
 * {@link #toMappingSpec(String, int, String)} and registers the result through
 * {@code ClientRegistry.registerKeyMapping}.</p>
 *
 * @param id             the action id, matching {@code [a-z0-9_.-]+}
 * @param translationKey the translation key for the action name
 * @param defaultKey     the GLFW-compatible default key code
 * @param mouse          whether the code is a mouse button
 * @param category       the category id, for example {@code minimap}
 */
public record KeybindSpec(String id, String translationKey, int defaultKey, boolean mouse,
                          String category)
{
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+");

    /**
     * Validates every component.
     *
     * @throws NullPointerException     when a string component is {@code null}
     * @throws IllegalArgumentException when an id is invalid or a string is
     *                                  blank, or when the key code is negative
     */
    public KeybindSpec
    {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(translationKey, "translationKey");
        Objects.requireNonNull(category, "category");
        if (!ID.matcher(id).matches())
        {
            throw new IllegalArgumentException("invalid keybind id: " + id);
        }
        if (translationKey.isBlank())
        {
            throw new IllegalArgumentException("translationKey must not be blank");
        }
        if (category.isBlank())
        {
            throw new IllegalArgumentException("category must not be blank");
        }
        if (defaultKey < 0)
        {
            throw new IllegalArgumentException("defaultKey must not be negative");
        }
    }

    /**
     * Converts this spec into a loader mapping without a screen factory.
     *
     * @param modId the owning mod id
     * @param order the display order; lower values are shown first
     * @return the loader mapping, never {@code null}
     */
    public KeyMappingSpec toMappingSpec(String modId, int order)
    {
        return toMappingSpec(modId, order, null);
    }

    /**
     * Converts this spec into a loader mapping.
     *
     * @param modId           the owning mod id
     * @param order           the display order; lower values are shown first
     * @param screenFactoryId the optional screen factory opened on press
     * @return the loader mapping, never {@code null}
     */
    public KeyMappingSpec toMappingSpec(String modId, int order, String screenFactoryId)
    {
        KeyMappingSpec.Builder builder = KeyMappingSpec.builder(modId, id, defaultKey)
                .translationKey(translationKey)
                .category(category)
                .mouse(mouse)
                .order(order);
        if (screenFactoryId != null)
        {
            builder.screenFactory(screenFactoryId);
        }
        return builder.build();
    }
}
