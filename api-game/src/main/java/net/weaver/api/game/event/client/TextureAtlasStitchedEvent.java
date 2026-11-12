package net.weaver.api.game.event.client;

import net.minecraft.client.renderer.texture.TextureAtlas;
import net.weaver.api.event.Event;

/**
 * Fired on the client after a texture atlas (blocks, items, particles, mob
 * effects, and so on) was stitched and uploaded during a resource reload
 * ({@code TextureAtlas#upload}). Look up sprites with
 * {@code atlas().getSprite(id)} here to cache them.
 */
public final class TextureAtlasStitchedEvent extends Event {
    private final TextureAtlas atlas;

    public TextureAtlasStitchedEvent(TextureAtlas atlas) {
        this.atlas = atlas;
    }

    public TextureAtlas atlas() {
        return atlas;
    }
}
