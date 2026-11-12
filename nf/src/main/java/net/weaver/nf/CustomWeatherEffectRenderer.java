package net.weaver.nf;

import net.weaver.api.game.client.level.EnvironmentRenderers;

/**
 * Draws a dimension's rain and snow and ticks its rain, as NeoForge's
 * {@code CustomWeatherEffectRenderer}. Register it with
 * {@link Environment#registerWeatherEffectRenderer}; backed by Weaver's
 * {@code EnvironmentRenderers}.
 */
public interface CustomWeatherEffectRenderer extends EnvironmentRenderers.Weather {
}
