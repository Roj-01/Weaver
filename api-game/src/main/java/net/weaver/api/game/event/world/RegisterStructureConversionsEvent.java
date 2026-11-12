package net.weaver.api.game.event.world;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import net.weaver.api.event.Event;

/**
 * Fired once, the first time the game upgrades a chunk saved before 1.18.2
 * ({@code StructuresBecomeConfiguredFix}), to tell the upgrade what a mod's
 * old structure ids became. Without an entry the upgrade drops unknown
 * structure starts and references from old chunks.
 *
 * <p>Ids are lower case without a namespace, as old saves stored them (for
 * example {@code "mymod:old_tower"} or {@code "old_tower"}, matching what the
 * mod saved).
 */
public final class RegisterStructureConversionsEvent extends Event {
    private final BiConsumer<String, Object> sink;
    private final Conversions factory;

    /** Creates vanilla conversions; supplied by the loader. */
    public interface Conversions {
        Object trivial(String newId);

        Object biomeMapped(Map<List<String>, String> biomes, String fallback);
    }

    public RegisterStructureConversionsEvent(BiConsumer<String, Object> sink, Conversions factory) {
        this.sink = sink;
        this.factory = factory;
    }

    /** An old structure id became {@code newId} everywhere. */
    public void register(String oldId, String newId) {
        sink.accept(Objects.requireNonNull(oldId, "oldId").toLowerCase(java.util.Locale.ROOT),
                factory.trivial(Objects.requireNonNull(newId, "newId")));
    }

    /**
     * An old structure id became one of several ids depending on the biome it
     * was in (biome ids to new id), or {@code fallback} elsewhere.
     */
    public void registerBiomeMapped(String oldId, Map<List<String>, String> biomes, String fallback) {
        sink.accept(Objects.requireNonNull(oldId, "oldId").toLowerCase(java.util.Locale.ROOT),
                factory.biomeMapped(Objects.requireNonNull(biomes, "biomes"), Objects.requireNonNull(fallback, "fallback")));
    }
}
