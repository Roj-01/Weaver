package net.weaver.api.game.event.world;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.weaver.api.event.Event;

/**
 * Fired when a saved chunk has been read from disk
 * ({@code SerializableChunkData#read}), before it joins the world. Not fired
 * for newly generated chunks. May fire off the server thread; do not touch
 * other chunks or the world from a listener.
 */
public final class ChunkDataLoadEvent extends Event {
    private final ServerLevel level;
    private final ChunkAccess chunk;
    private final SerializableChunkData data;

    public ChunkDataLoadEvent(ServerLevel level, ChunkAccess chunk, SerializableChunkData data) {
        this.level = level;
        this.chunk = chunk;
        this.data = data;
    }

    public ServerLevel level() {
        return level;
    }

    public ChunkAccess chunk() {
        return chunk;
    }

    /** The data the chunk was read from. */
    public SerializableChunkData data() {
        return data;
    }
}
