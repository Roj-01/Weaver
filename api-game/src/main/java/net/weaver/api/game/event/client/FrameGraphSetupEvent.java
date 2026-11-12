package net.weaver.api.game.event.client;

import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.weaver.api.event.Event;

/**
 * Fired on the render thread each frame after the level frame graph has its
 * render targets and before the first pass (the sky) is added
 * ({@code LevelRenderer#render}). Mods can add their own passes or targets with
 * {@link #builder()}; passes run in the order they are added.
 */
public final class FrameGraphSetupEvent extends Event {
    private final FrameGraphBuilder builder;
    private final LevelTargetBundle targets;
    private final CameraRenderState camera;
    private final LevelRenderState levelState;

    public FrameGraphSetupEvent(FrameGraphBuilder builder, LevelTargetBundle targets, CameraRenderState camera,
            LevelRenderState levelState) {
        this.builder = builder;
        this.targets = targets;
        this.camera = camera;
        this.levelState = levelState;
    }

    public FrameGraphBuilder builder() {
        return builder;
    }

    /** The level targets (main, translucent, particles, and so on) for this frame. */
    public LevelTargetBundle targets() {
        return targets;
    }

    public CameraRenderState camera() {
        return camera;
    }

    public LevelRenderState levelState() {
        return levelState;
    }
}
