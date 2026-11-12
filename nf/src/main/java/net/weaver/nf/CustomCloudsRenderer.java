package net.weaver.nf;

import net.weaver.api.game.client.level.EnvironmentRenderers;

/**
 * Draws a dimension's clouds, as NeoForge's {@code CustomCloudsRenderer}. Register
 * it with {@link Environment#registerCloudRenderer}; backed by Weaver's
 * {@code EnvironmentRenderers}.
 */
public interface CustomCloudsRenderer extends EnvironmentRenderers.Clouds {
}
