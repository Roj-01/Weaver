package net.weaver.api.game.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Draws real geometry in the world: arbitrary triangles and quads with any render
 * type, and whole block models at any position, rotation, and scale (moving
 * machines, contraptions, previews, held structures).
 *
 * <p>Call these while a frame is being submitted and you have its pose stack and
 * collector: in {@code LevelRenderRegistry} at the {@code ENTITIES} or
 * {@code BLOCK_ENTITIES} stage, in a block entity or entity renderer's
 * {@code submit}, or from {@code PrepareRenderBuffersEvent}. World positions are
 * drawn relative to the camera: use {@link #translateToWorld} first.
 */
public final class WorldGeometry {
    /** Packed light for full brightness (sky 15, block 15). */
    public static final int FULL_BRIGHT = 0xF000F0;

    private static BlockModelResolver resolver;

    private WorldGeometry() {
    }

    /** Moves the pose so that the world position {@code (x, y, z)} is its origin. */
    public static void translateToWorld(PoseStack poses, CameraRenderState camera, double x, double y, double z) {
        Vec3 eye = camera == null || camera.pos == null ? Vec3.ZERO : camera.pos;
        poses.translate(x - eye.x, y - eye.y, z - eye.z);
    }

    /**
     * Submits geometry drawn by {@code renderer} with {@code type}: the renderer
     * gets the current pose and a vertex consumer of the type's format.
     */
    public static void geometry(SubmitNodeCollector collector, PoseStack poses, RenderType type,
            SubmitNodeCollector.CustomGeometryRenderer renderer) {
        collector.submitCustomGeometry(poses, Objects.requireNonNull(type, "type"), Objects.requireNonNull(renderer, "renderer"));
    }

    /**
     * Draws the model of {@code state} at the pose origin (a unit cube from the
     * origin to {@code (1, 1, 1)}), lit and tinted as if it stood at
     * {@code lightPos} in {@code level}, like a block moved by a piston.
     */
    public static void block(SubmitNodeCollector collector, PoseStack poses, ClientLevel level, BlockPos lightPos,
            BlockState state) {
        MovingBlockRenderState moving = new MovingBlockRenderState();
        moving.randomSeedPos = lightPos;
        moving.blockPos = lightPos;
        moving.blockState = state;
        moving.biome = level.getBiome(lightPos);
        moving.cardinalLighting = level.cardinalLighting();
        moving.lightEngine = level.getLightEngine();
        collector.submitMovingBlock(poses, moving, 0);
    }

    /**
     * Draws the model of {@code state} at the pose origin with a fixed light,
     * without a level (GUI scenes, previews before a world exists).
     */
    public static void blockWithLight(SubmitNodeCollector collector, PoseStack poses, BlockState state, int light) {
        BlockModelRenderState model = new BlockModelRenderState();
        resolver().update(model, state, BlockDisplayContext.create());
        if (!model.isEmpty()) {
            model.submit(poses, collector, light, OverlayTexture.NO_OVERLAY, 0);
        }
    }

    private static synchronized BlockModelResolver resolver() {
        if (resolver == null) {
            resolver = new BlockModelResolver(Minecraft.getInstance().getModelManager());
        }
        return resolver;
    }
}
