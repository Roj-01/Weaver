package net.weaver.nf;

import net.weaver.api.game.client.level.EnvironmentRenderers;

/**
 * Draws a dimension's sky, as NeoForge's {@code CustomSkyboxRenderer}. Register it
 * with {@link Environment#registerSkyboxRenderer}; backed by Weaver's
 * {@code EnvironmentRenderers}.
 */
public interface CustomSkyboxRenderer extends EnvironmentRenderers.Sky {
    @Override
    default boolean renderSky(net.minecraft.client.renderer.state.level.LevelRenderState level,
            net.minecraft.client.renderer.state.level.SkyRenderState sky, org.joml.Matrix4fc viewRotation,
            com.mojang.renderpearl.api.buffers.GpuBufferSlice fog) {
        return false;
    }
}
