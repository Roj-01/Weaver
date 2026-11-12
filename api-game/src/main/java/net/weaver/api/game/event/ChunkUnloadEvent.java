package net.weaver.api.game.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Typed variant of {@link net.weaver.api.event.events.ChunkUnloadEvent}.
 *
 * <p>Fired at method entry to {@code ServerLevel#unload}, before the chunk is
 * removed from the level. Observation-only and not cancelable. The payloads
 * are narrowed to the game types. The inherited accessors stay available with
 * their {@link Object} descriptors for binary compatibility.
 */
public final class ChunkUnloadEvent extends net.weaver.api.event.events.ChunkUnloadEvent {

    /**
     * Creates a typed chunk-unload event.
     *
     * @param level the server level unloading the chunk
     * @param chunk the chunk being unloaded
     */
    public ChunkUnloadEvent(Object level, Object chunk) {
        super(level, chunk);
    }

    @Override
    public ServerLevel level() {
        return (ServerLevel) super.level();
    }

    @Override
    public LevelChunk chunk() {
        return (LevelChunk) super.chunk();
    }
}
