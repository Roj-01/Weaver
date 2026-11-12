package net.weaver.api.config;

import java.util.Map;
import java.util.Objects;

/**
 * An immutable snapshot of one configuration file.
 *
 * <p>This is the value exchanged with a {@link ConfigStore}: it carries the
 * schema version written to disk and the JSON-compatible values to persist.
 * The compact constructor copies the map and every nested list and map into
 * immutable structures while preserving insertion order, so a store cannot
 * mutate the document and the document cannot change after construction.
 *
 * @param schemaVersion the schema version written to disk
 * @param values        the JSON-compatible values in insertion order, not
 *                      {@code null}
 */
public record ConfigDocument(int schemaVersion, Map<String, Object> values)
{
    /**
     * Validates and defensively freezes the components.
     *
     * @throws NullPointerException when {@code values} or a top-level key is
     *                              {@code null}
     */
    public ConfigDocument
    {
        values = ConfigData.freezeJsonMap(Objects.requireNonNull(values, "values"));
    }

    /**
     * An empty document at schema version {@code 0}.
     *
     * @return an empty immutable document, never {@code null}
     */
    public static ConfigDocument empty()
    {
        return new ConfigDocument(0, Map.of());
    }
}
