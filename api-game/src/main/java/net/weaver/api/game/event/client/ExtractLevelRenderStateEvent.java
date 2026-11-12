package net.weaver.api.game.event.client;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.weaver.api.event.Event;

/**
 * Fired on the client thread each frame after the level's render state was
 * extracted from the live world ({@code LevelExtractor#extract}). This is the
 * point to copy any mod state that rendering later needs, since rendering
 * reads only the extracted state.
 */
public final class ExtractLevelRenderStateEvent extends Event {
    private final LevelExtractor extractor;
    private final Camera camera;
    private final DeltaTracker deltaTracker;

    public ExtractLevelRenderStateEvent(LevelExtractor extractor, Camera camera, DeltaTracker deltaTracker) {
        this.extractor = extractor;
        this.camera = camera;
        this.deltaTracker = deltaTracker;
    }

    public LevelExtractor extractor() {
        return extractor;
    }

    public Camera camera() {
        return camera;
    }

    public DeltaTracker deltaTracker() {
        return deltaTracker;
    }
}
