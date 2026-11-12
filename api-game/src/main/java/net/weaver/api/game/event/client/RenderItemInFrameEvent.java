package net.weaver.api.game.event.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemFrameRenderer;
import net.minecraft.client.renderer.entity.state.ItemFrameRenderState;
import net.weaver.api.event.Event;

/**
 * Fired on the render thread when an item frame is about to draw its item or
 * map ({@code ItemFrameRenderer#submit}), with the pose already moved and
 * rotated onto the frame. Cancelable: a canceled event draws nothing inside
 * the frame; submit a replacement through {@link #collector()}.
 */
public final class RenderItemInFrameEvent extends Event {
    private final ItemFrameRenderState state;
    private final ItemFrameRenderer<?> renderer;
    private final PoseStack poseStack;
    private final SubmitNodeCollector collector;

    public RenderItemInFrameEvent(ItemFrameRenderState state, ItemFrameRenderer<?> renderer, PoseStack poseStack,
                                  SubmitNodeCollector collector) {
        super(true);
        this.state = state;
        this.renderer = renderer;
        this.poseStack = poseStack;
        this.collector = collector;
    }

    public ItemFrameRenderState state() {
        return state;
    }

    public ItemFrameRenderer<?> renderer() {
        return renderer;
    }

    public PoseStack poseStack() {
        return poseStack;
    }

    public SubmitNodeCollector collector() {
        return collector;
    }
}
