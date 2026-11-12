package net.weaver.nf;

import net.minecraft.resources.Identifier;
import net.weaver.api.game.client.level.EnvironmentRenderers;

/**
 * Per-dimension sky, cloud, and weather renderers, as NeoForge's
 * {@code DimensionSpecialEffects} custom renderers: keyed by the dimension type id,
 * asked before vanilla draws, and vanilla is skipped when they return
 * {@code true}. Weaver's own mixins call them, so a mod needs none of its own.
 */
public final class Environment {
    private Environment() {
    }

    public static void registerSkyboxRenderer(Identifier dimensionType, CustomSkyboxRenderer renderer) {
        EnvironmentRenderers.registerSky(dimensionType, renderer);
    }

    public static void registerCloudRenderer(Identifier dimensionType, CustomCloudsRenderer renderer) {
        EnvironmentRenderers.registerClouds(dimensionType, renderer);
    }

    public static void registerWeatherEffectRenderer(Identifier dimensionType, CustomWeatherEffectRenderer renderer) {
        EnvironmentRenderers.registerWeather(dimensionType, renderer);
    }
}
