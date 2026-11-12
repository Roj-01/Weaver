package net.weaver.api.event.events;

import net.weaver.api.event.Event;

import java.util.Objects;

/**
 * A server level is about to unload a chunk.
 *
 * <p>Fired at method entry to {@code ServerLevel#unload}, before the chunk is
 * removed from the level. Observation-only and not cancelable. The base
 * payloads are game objects carried as {@link Object}; the typed
 * {@code api-game} event exposes game types.
 */
public class ChunkUnloadEvent extends Event {

    private final Object level;
    private final Object chunk;

    /**
     * Creates a chunk-unload event.
     *
     * @param level the server level unloading the chunk
     * @param chunk the chunk being unloaded
     */
    public ChunkUnloadEvent(Object level, Object chunk) {
        this.level = Objects.requireNonNull(level, "level");
        this.chunk = Objects.requireNonNull(chunk, "chunk");
    }

    /** The server level unloading the chunk. */
    public Object level() {
        return level;
    }

    /** The chunk being unloaded. */
    public Object chunk() {
        return chunk;
    }
}
