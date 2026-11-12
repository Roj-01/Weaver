package net.weaver.api.game.event.support;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects the item entities a dying mob spawns through
 * {@code Entity#spawnAtLocation} while {@code LivingEntity#dropAllDeathLoot}
 * runs, so {@code LivingDropsEvent} can see and change them before they enter
 * the world. Captures nest per thread; an entity only captures its own drops.
 */
public final class DropCapture {
    private static final ThreadLocal<DropCapture> CURRENT = new ThreadLocal<>();

    private final Entity owner;
    private final DropCapture previous;
    private final List<ItemEntity> drops = new ArrayList<>();

    private DropCapture(Entity owner, DropCapture previous) {
        this.owner = owner;
        this.previous = previous;
    }

    /** Starts capturing drops spawned by {@code owner} on this thread. */
    public static void begin(Entity owner) {
        CURRENT.set(new DropCapture(owner, CURRENT.get()));
    }

    /**
     * Captures {@code item} when {@code spawner} is the entity currently capturing.
     *
     * @return {@code true} when the item was captured and must not be spawned now
     */
    public static boolean offer(Entity spawner, ItemEntity item) {
        DropCapture capture = CURRENT.get();
        if (capture == null || capture.owner != spawner) {
            return false;
        }
        capture.drops.add(item);
        return true;
    }

    /**
     * Ends the capture for {@code owner} and returns what it collected, or
     * {@code null} when {@code owner} was not capturing.
     */
    public static List<ItemEntity> end(Entity owner) {
        DropCapture capture = CURRENT.get();
        if (capture == null || capture.owner != owner) {
            return null;
        }
        if (capture.previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(capture.previous);
        }
        return capture.drops;
    }
}
