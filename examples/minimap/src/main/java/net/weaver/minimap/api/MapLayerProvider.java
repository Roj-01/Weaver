package net.weaver.minimap.api;

/**
 * Contributes one optional renderer layer.
 *
 * <p>Providers run on the client thread. Returning {@code null} contributes
 * nothing. Any thrown exception is isolated by {@link MinimapApi} and logged
 * once; the remaining providers still run.</p>
 */
@FunctionalInterface
public interface MapLayerProvider
{
    /**
     * Returns the layer descriptor for a render pass.
     *
     * @param context the render context, never {@code null}
     * @return the layer, or {@code null} for none
     */
    MapLayer layer(MapLayerContext context);
}
