package net.weaver.api.game.client.level;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;

import java.util.Objects;

/**
 * A snapshot of one level render stage.
 *
 * <p>The game objects are live render-thread objects and must not be retained
 * beyond the listener call. The context is immutable, but the referenced
 * {@link LevelRenderState} and drawing objects are not.
 *
 * <p>{@link #poseStack()} and {@link #collector()} are only available for
 * {@link LevelRenderStage#ENTITIES} and
 * {@link LevelRenderStage#BLOCK_ENTITIES}. They are {@code null} for
 * {@link LevelRenderStage#START}, {@link LevelRenderStage#SKY}, and
 * {@link LevelRenderStage#END}. {@link #cameraState()} may be {@code null} when
 * the caller has not populated the nested camera state.
 *
 * @param level        the client level, normally non-null in a live game
 * @param cameraState  the camera render state, or {@code null}
 * @param levelState   the level render state, never {@code null}
 * @param poseStack    the current pose stack, or {@code null} outside the
 *                     entity stages
 * @param collector    the submit collector, or {@code null} outside the entity
 *                     stages
 * @param partialTick  the frame's partial tick
 * @param stage        the current stage, never {@code null}
 * @param minecraft    the client instance, normally non-null in a live game
 */
public record LevelRenderContext(ClientLevel level, CameraRenderState cameraState,
                                 LevelRenderState levelState, PoseStack poseStack,
                                 SubmitNodeCollector collector, float partialTick,
                                 LevelRenderStage stage, Minecraft minecraft)
{
    /**
     * Validates the required values and the partial tick.
     *
     * @throws NullPointerException     when {@code levelState} or {@code stage}
     *                                  is {@code null}
     */
    public LevelRenderContext
    {
        Objects.requireNonNull(levelState, "levelState");
        Objects.requireNonNull(stage, "stage");
        // Vanilla can pass a frame time a hair below zero (rounding in its timer):
        // it means the start of the tick, not an error that would cancel the hook.
        if (!(partialTick >= 0.0F))
        {
            partialTick = 0.0F;
        }
    }

    /**
     * Whether this stage carries a pose stack and submit collector.
     *
     * @return {@code true} for the entity stages
     */
    public boolean hasEntityPass()
    {
        return stage == LevelRenderStage.ENTITIES
                || stage == LevelRenderStage.BLOCK_ENTITIES;
    }
}
