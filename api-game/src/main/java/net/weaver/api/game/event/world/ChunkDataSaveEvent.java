package net.weaver.api.game.event.world;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.weaver.api.event.Event;

/**
 * Fired on the server thread when a chunk has been copied for saving
 * ({@code SerializableChunkData#copyOf}), before the copy is written to disk
 * on a background thread.
 */
public final class ChunkDataSaveEvent extends Event {
    private final ServerLevel level;
    private final ChunkAccess chunk;
    private final SerializableChunkData data;

    public ChunkDataSaveEvent(ServerLevel level, ChunkAccess chunk, SerializableChunkData data) {
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

    /** The copy that will be written. */
    public SerializableChunkData data() {
        return data;
    }
}
