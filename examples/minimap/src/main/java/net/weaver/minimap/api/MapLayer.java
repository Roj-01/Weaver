package net.weaver.minimap.api;

import java.util.Objects;

/**
 * A renderer layer contributed by a {@link MapLayerProvider}.
 *
 * @param id              the layer id, non-blank
 * @param translationKey  the optional translation key for the layer name
 * @param zOrder          the draw order; lower values are drawn first
 * @param defaultVisible  whether the layer starts visible
 * @param icon            the optional icon id used by the layers panel
 */
public record MapLayer(String id, String translationKey, int zOrder, boolean defaultVisible,
                       String icon)
{
    /**
     * Validates the identifier.
     *
     * @throws NullPointerException     when {@code id} is {@code null}
     * @throws IllegalArgumentException when {@code id} is blank
     */
    public MapLayer
    {
        Objects.requireNonNull(id, "id");
        if (id.isBlank())
        {
            throw new IllegalArgumentException("id must not be blank");
        }
        translationKey = translationKey == null ? "" : translationKey;
        icon = icon == null ? "" : icon;
    }
}
