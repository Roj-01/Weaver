package net.weaver.api.game.event.client;

import net.weaver.api.event.Event;

/**
 * Fired on the render thread at the end of each frame, after the level
 * renderer finished it ({@code LevelRenderer#endFrame} in
 * {@code Minecraft#renderFrame}) and before the frame-rate limiter waits.
 * Release per-frame resources here. Fires every frame; keep listeners cheap.
 */
public final class FlipFrameEvent extends Event {
}
