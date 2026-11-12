package net.weaver.api.game.event.support;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.world.entity.Entity;

/** Mobs whose spawn a FinalizeSpawnEvent listener cancelled; the level refuses to add them. */
public final class SpawnCancels {
    private static final Set<Entity> CANCELLED = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    private SpawnCancels() {
    }

    public static void cancel(Entity entity) {
        CANCELLED.add(entity);
    }

    /** Whether {@code entity} must not be added; forgets it (an entity is added at most once). */
    public static boolean consume(Entity entity) {
        return !CANCELLED.isEmpty() && CANCELLED.remove(entity);
    }
}
