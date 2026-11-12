package net.weaver.minimap.api;

/**
 * Overrides the icon used for an entity marker.
 *
 * <p>Providers run in registration order; the first non-null result wins.
 * Returning {@code null} keeps the default icon. Exceptions are isolated and
 * logged once.</p>
 */
@FunctionalInterface
public interface EntityIconProvider
{
    /**
     * Returns an icon id for an entity.
     *
     * @param context the entity context, never {@code null}
     * @return the icon id, or {@code null} for the default
     */
    String icon(EntityIconContext context);
}
