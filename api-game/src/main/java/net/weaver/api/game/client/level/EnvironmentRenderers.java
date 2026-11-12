package net.weaver.api.game.client.level;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Camera;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.oit.OitRenderPassProvider;
import net.minecraft.client.renderer.oit.OitStage;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.client.renderer.state.level.WeatherRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.weaver.api.annotation.Internal;
import org.joml.Matrix4fc;

/**
 * Replaces how a dimension draws its sky, its clouds, and its rain and snow, by
 * dimension type: the renderer registered for the dimension type of the level the
 * player is in is asked first, and when it returns {@code true} the vanilla
 * drawing is skipped. A renderer that returns {@code false} lets vanilla draw.
 *
 * <p>Register from {@code onClientSetup}. One renderer of each kind per dimension
 * type; a later registration replaces the earlier one.
 */
public final class EnvironmentRenderers
{
    private static final Map<Identifier, Sky> SKY = new ConcurrentHashMap<>();
    private static final Map<Identifier, Clouds> CLOUDS = new ConcurrentHashMap<>();
    private static final Map<Identifier, Weather> WEATHER = new ConcurrentHashMap<>();

    private EnvironmentRenderers()
    {
    }

    /** Draws a dimension's sky. */
    @FunctionalInterface
    public interface Sky
    {
        /**
         * @param level        the level render state of the frame
         * @param sky          the extracted sky state
         * @param viewRotation the camera view rotation
         * @param fog          the fog uniform slice vanilla draws the sky with
         * @return {@code true} when this drew the sky and vanilla should not
         */
        boolean renderSky(LevelRenderState level, SkyRenderState sky, Matrix4fc viewRotation, GpuBufferSlice fog);
    }

    /** Draws a dimension's clouds, with or without improved transparency. */
    public interface Clouds
    {
        /** @return {@code true} when this drew the clouds and vanilla should not */
        default boolean renderClouds(LevelRenderState level, CloudStatus status, Matrix4fc viewRotation, RenderPass pass)
        {
            return false;
        }

        /** @return {@code true} when this drew the clouds and vanilla should not */
        default boolean renderCloudsOit(LevelRenderState level, CloudStatus status, Matrix4fc viewRotation, OitStage stage,
                GpuTextureView depth, OitRenderPassProvider.Parameters parameters)
        {
            return false;
        }
    }

    /** Draws a dimension's rain and snow, and ticks its rain effects. */
    public interface Weather
    {
        /** @return {@code true} when this drew the weather and vanilla should not */
        default boolean renderSnowAndRain(LevelRenderState level, WeatherRenderState weather, Vec3 camera, RenderPass pass)
        {
            return false;
        }

        /** @return {@code true} when this drew the weather and vanilla should not */
        default boolean renderSnowAndRainOit(LevelRenderState level, WeatherRenderState weather, Vec3 camera, OitStage stage,
                RenderPass pass)
        {
            return false;
        }

        /** @return {@code true} when this ticked the rain (splashes and sounds) and vanilla should not */
        default boolean tickRain(ClientLevel level, long gameTime, Camera camera)
        {
            return false;
        }
    }

    public static void registerSky(Identifier dimensionType, Sky renderer)
    {
        SKY.put(Objects.requireNonNull(dimensionType, "dimensionType"), Objects.requireNonNull(renderer, "renderer"));
    }

    public static void registerClouds(Identifier dimensionType, Clouds renderer)
    {
        CLOUDS.put(Objects.requireNonNull(dimensionType, "dimensionType"), Objects.requireNonNull(renderer, "renderer"));
    }

    public static void registerWeather(Identifier dimensionType, Weather renderer)
    {
        WEATHER.put(Objects.requireNonNull(dimensionType, "dimensionType"), Objects.requireNonNull(renderer, "renderer"));
    }

    @Internal
    public static boolean sky(LevelRenderState level, SkyRenderState sky, Matrix4fc viewRotation, GpuBufferSlice fog)
    {
        Sky renderer = SKY.isEmpty() ? null : SKY.get(here());
        return renderer != null && renderer.renderSky(level, sky, viewRotation, fog);
    }

    @Internal
    public static boolean clouds(LevelRenderState level, CloudStatus status, Matrix4fc viewRotation, RenderPass pass)
    {
        Clouds renderer = CLOUDS.isEmpty() ? null : CLOUDS.get(here());
        return renderer != null && renderer.renderClouds(level, status, viewRotation, pass);
    }

    @Internal
    public static boolean cloudsOit(LevelRenderState level, CloudStatus status, Matrix4fc viewRotation, OitStage stage,
            GpuTextureView depth, OitRenderPassProvider.Parameters parameters)
    {
        Clouds renderer = CLOUDS.isEmpty() ? null : CLOUDS.get(here());
        return renderer != null && renderer.renderCloudsOit(level, status, viewRotation, stage, depth, parameters);
    }

    @Internal
    public static boolean weather(LevelRenderState level, WeatherRenderState weather, Vec3 camera, RenderPass pass)
    {
        Weather renderer = WEATHER.isEmpty() ? null : WEATHER.get(here());
        return renderer != null && renderer.renderSnowAndRain(level, weather, camera, pass);
    }

    @Internal
    public static boolean weatherOit(LevelRenderState level, WeatherRenderState weather, Vec3 camera, OitStage stage,
            RenderPass pass)
    {
        Weather renderer = WEATHER.isEmpty() ? null : WEATHER.get(here());
        return renderer != null && renderer.renderSnowAndRainOit(level, weather, camera, stage, pass);
    }

    @Internal
    public static boolean tickRain(ClientLevel level, long gameTime, Camera camera)
    {
        Weather renderer = WEATHER.isEmpty() ? null : WEATHER.get(of(level));
        return renderer != null && renderer.tickRain(level, gameTime, camera);
    }

    private static Identifier here()
    {
        return of(Minecraft.getInstance().level);
    }

    private static Identifier of(ClientLevel level)
    {
        if (level == null)
        {
            return MISSING;
        }
        return level.dimensionTypeRegistration().unwrapKey().map(key -> key.identifier()).orElse(MISSING);
    }

    private static final Identifier MISSING = Identifier.fromNamespaceAndPath("weaver", "missing_dimension_type");
}
