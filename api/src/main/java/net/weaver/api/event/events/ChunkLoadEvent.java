package net.weaver.api.event.events;

import net.weaver.api.event.Event;

import java.util.Objects;

/**
 * A server level started ticking a chunk.
 *
 * <p>Fired at method entry to {@code ServerLevel#startTickingChunk}. The hook
 * runs when a chunk transitions into the ticking state, which happens on first
 * load and again if a loaded chunk re-enters the ticking set. It is not a
 * per-tick event. Observation-only and not cancelable. The base payloads are
 * game objects carried as {@link Object}; the typed {@code api-game} event
 * exposes game types.
 */
public class ChunkLoadEvent extends Event {

    private final Object level;
    private final Object chunk;

    /**
     * Creates a chunk-load event.
     *
     * @param level the server level that started ticking the chunk
     * @param chunk the chunk that started ticking
     */
    public ChunkLoadEvent(Object level, Object chunk) {
        this.level = Objects.requireNonNull(level, "level");
        this.chunk = Objects.requireNonNull(chunk, "chunk");
    }

    /** The server level that started ticking the chunk. */
    public Object level() {
        return level;
    }

    /** The chunk that started ticking. */
    public Object chunk() {
        return chunk;
    }
}
