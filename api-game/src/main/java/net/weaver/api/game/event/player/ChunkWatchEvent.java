package net.weaver.api.game.event.player;

import java.util.Objects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.weaver.api.event.Event;

/**
 * Fired on the server as a chunk enters or leaves a player's view. Check
 * {@link #kind()}:
 * <ul>
 *   <li>{@code WATCH}: the chunk was queued to be sent to the player
 *       ({@code ChunkMap#markChunkPendingToSend}). {@link #chunk()} is set.</li>
 *   <li>{@code SENT}: the chunk packet was sent ({@code PlayerChunkSender#sendChunk});
 *       send mod data about the chunk now. {@link #chunk()} is set.</li>
 *   <li>{@code UNWATCH}: the chunk left the player's view
 *       ({@code ChunkMap#dropChunk}). {@link #chunk()} is {@code null}.</li>
 * </ul>
 */
public final class ChunkWatchEvent extends Event {
    /** What happened. */
    public enum Kind {
        WATCH,
        SENT,
        UNWATCH
    }

    private final Kind kind;
    private final ServerPlayer player;
    private final ServerLevel level;
    private final ChunkPos pos;
    private final LevelChunk chunk;

    public ChunkWatchEvent(Kind kind, ServerPlayer player, ServerLevel level, ChunkPos pos, LevelChunk chunk) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.player = player;
        this.level = level;
        this.pos = pos;
        this.chunk = chunk;
    }

    public Kind kind() {
        return kind;
    }

    public ServerPlayer player() {
        return player;
    }

    public ServerLevel level() {
        return level;
    }

    public ChunkPos pos() {
        return pos;
    }

    /** The chunk, or {@code null} for {@code UNWATCH}. */
    public LevelChunk chunk() {
        return chunk;
    }
}
