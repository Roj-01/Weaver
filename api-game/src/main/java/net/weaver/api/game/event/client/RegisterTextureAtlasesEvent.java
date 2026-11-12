package net.weaver.api.game.event.client;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.resources.model.sprite.AtlasManager;
import net.minecraft.resources.Identifier;
import net.weaver.api.event.Event;

/**
 * Fired once on the client, early in startup, while the atlas manager is
 * created, to add texture atlases. Each atlas is stitched on every resource
 * reload from the sprite sources in its definition file
 * ({@code assets/<namespace>/atlases/<path>.json}). Register the listener during
 * mod initialization.
 */
public final class RegisterTextureAtlasesEvent extends Event {
    private final List<AtlasManager.AtlasConfig> atlases;

    public RegisterTextureAtlasesEvent(List<AtlasManager.AtlasConfig> atlases) {
        this.atlases = atlases;
    }

    /**
     * Adds an atlas.
     *
     * @param textureId  the atlas texture id, for example {@code mymod:textures/atlas/runes.png}
     * @param definition the atlas definition id, for example {@code mymod:runes}
     * @param mipmaps    whether the atlas has mipmaps (true for textures drawn in the world)
     */
    public void register(Identifier textureId, Identifier definition, boolean mipmaps) {
        atlases.add(new AtlasManager.AtlasConfig(Objects.requireNonNull(textureId, "textureId"),
                Objects.requireNonNull(definition, "definition"), mipmaps));
    }

    /** The atlases so far, vanilla included (read-only). */
    public List<AtlasManager.AtlasConfig> atlases() {
        return Collections.unmodifiableList(atlases);
    }
}
