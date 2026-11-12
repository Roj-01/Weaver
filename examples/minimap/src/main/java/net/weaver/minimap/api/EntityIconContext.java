package net.weaver.minimap.api;

import java.util.Objects;

/**
 * Immutable entity context passed to {@link EntityIconProvider}.
 *
 * @param entityTypeId the entity type id, for example {@code minecraft:zombie}
 * @param category     the map category: {@code player}, {@code hostile},
 *                     {@code passive}, {@code item}, or {@code other}
 * @param dimension    the current dimension id, or {@code null} outside a world
 * @param fullscreen   whether the fullscreen map is rendering
 */
public record EntityIconContext(String entityTypeId, String category, String dimension,
                                boolean fullscreen)
{
    /**
     * Validates the required fields.
     *
     * @throws NullPointerException     when {@code entityTypeId} or
     *                                  {@code category} is {@code null}
     * @throws IllegalArgumentException when a required field is blank
     */
    public EntityIconContext
    {
        Objects.requireNonNull(entityTypeId, "entityTypeId");
        Objects.requireNonNull(category, "category");
        if (entityTypeId.isBlank())
        {
            throw new IllegalArgumentException("entityTypeId must not be blank");
        }
        if (category.isBlank())
        {
            throw new IllegalArgumentException("category must not be blank");
        }
    }
}
