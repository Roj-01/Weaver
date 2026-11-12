package net.weaver.api.game.event.world;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.weaver.api.event.Event;

/**
 * Fired on the server thread when a chunk's ticket level changes
 * ({@code ChunkMap#updateChunkScheduling}): a player, a forced-chunk ticket,
 * or a portal moved it closer to or further from being loaded. Lower levels
 * mean more loaded (33 and below is loaded, 31 and below ticks entities).
 */
public final class ChunkTicketLevelUpdatedEvent extends Event {
    private final ServerLevel level;
    private final long chunkPos;
    private final int oldLevel;
    private final int newLevel;
    private final ChunkHolder holder;

    public ChunkTicketLevelUpdatedEvent(ServerLevel level, long chunkPos, int oldLevel, int newLevel, ChunkHolder holder) {
        this.level = level;
        this.chunkPos = chunkPos;
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
        this.holder = holder;
    }

    public ServerLevel level() {
        return level;
    }

    /** The chunk position, packed as in {@link ChunkPos#pack()}. */
    public long packedChunkPos() {
        return chunkPos;
    }

    public ChunkPos chunkPos() {
        return ChunkPos.unpack(chunkPos);
    }

    public int oldLevel() {
        return oldLevel;
    }

    public int newLevel() {
        return newLevel;
    }

    /** The chunk holder, or {@code null} when the chunk is not tracked. */
    public ChunkHolder holder() {
        return holder;
    }
}
