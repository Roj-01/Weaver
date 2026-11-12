package net.weaver.api.game.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Typed variant of {@link net.weaver.api.event.events.ChunkLoadEvent}.
 *
 * <p>Fired at method entry to {@code ServerLevel#startTickingChunk}. The hook
 * runs when a chunk transitions into the ticking state, not on every tick.
 * Observation-only and not cancelable. The payloads are narrowed to the game
 * types. The inherited accessors stay available with their {@link Object}
 * descriptors for binary compatibility.
 */
public final class ChunkLoadEvent extends net.weaver.api.event.events.ChunkLoadEvent {

    /**
     * Creates a typed chunk-load event.
     *
     * @param level the server level that started ticking the chunk
     * @param chunk the chunk that started ticking
     */
    public ChunkLoadEvent(Object level, Object chunk) {
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
