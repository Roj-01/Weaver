package net.weaver.api.game.event.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Objects;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.weaver.api.event.Event;

/**
 * Fired on the render thread when a living entity, players included, is
 * submitted for drawing ({@code LivingEntityRenderer#submit}). {@code PRE}
 * fires before vanilla and is cancelable (a canceled entity is not drawn);
 * {@code POST} fires after. Players have an {@code AvatarRenderState}.
 * Submit extra geometry through {@link #collector()} with {@link #poseStack()}.
 */
public final class RenderLivingEvent extends Event {
    /** Before or after vanilla submits the entity. */
    public enum Phase {
        PRE,
        POST
    }

    private final Phase phase;
    private final LivingEntityRenderer<?, ?, ?> renderer;
    private final LivingEntityRenderState state;
    private final PoseStack poseStack;
    private final SubmitNodeCollector collector;
    private final CameraRenderState camera;

    public RenderLivingEvent(Phase phase, LivingEntityRenderer<?, ?, ?> renderer, LivingEntityRenderState state,
                             PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        super(phase == Phase.PRE);
        this.phase = Objects.requireNonNull(phase, "phase");
        this.renderer = renderer;
        this.state = state;
        this.poseStack = poseStack;
        this.collector = collector;
        this.camera = camera;
    }

    public Phase phase() {
        return phase;
    }

    public LivingEntityRenderer<?, ?, ?> renderer() {
        return renderer;
    }

    /** The extracted render state of the entity. */
    public LivingEntityRenderState state() {
        return state;
    }

    public PoseStack poseStack() {
        return poseStack;
    }

    public SubmitNodeCollector collector() {
        return collector;
    }

    public CameraRenderState camera() {
        return camera;
    }
}
