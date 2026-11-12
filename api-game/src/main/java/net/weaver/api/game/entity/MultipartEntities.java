package net.weaver.api.game.entity;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

/**
 * The parts of the multipart entities each level tracks. Filled and read by the
 * loader at the points vanilla uses for the ender dragon; mods only implement
 * {@link MultipartEntity}.
 */
public final class MultipartEntities {
    private static final Map<Level, Int2ObjectMap<PartEntity<?>>> PARTS = new WeakHashMap<>();

    private MultipartEntities() {
    }

    private static Int2ObjectMap<PartEntity<?>> parts(Level level, boolean create) {
        synchronized (PARTS) {
            Int2ObjectMap<PartEntity<?>> map = PARTS.get(level);
            if (map == null && create) {
                map = new Int2ObjectOpenHashMap<>();
                PARTS.put(level, map);
            }
            return map;
        }
    }

    /** The parent started being tracked: its parts become hittable. */
    public static void track(Level level, Entity entity) {
        if (entity instanceof MultipartEntity multipart) {
            Int2ObjectMap<PartEntity<?>> map = parts(level, true);
            synchronized (PARTS) {
                for (PartEntity<?> part : multipart.getParts()) {
                    map.put(part.getId(), part);
                }
            }
        }
    }

    /** The parent stopped being tracked. */
    public static void untrack(Level level, Entity entity) {
        if (entity instanceof MultipartEntity multipart) {
            Int2ObjectMap<PartEntity<?>> map = parts(level, false);
            if (map != null) {
                synchronized (PARTS) {
                    for (PartEntity<?> part : multipart.getParts()) {
                        map.remove(part.getId());
                    }
                }
            }
        }
    }

    /** The part with {@code id} in {@code level}, or null. */
    public static PartEntity<?> part(Level level, int id) {
        Int2ObjectMap<PartEntity<?>> map = parts(level, false);
        if (map == null) {
            return null;
        }
        synchronized (PARTS) {
            return map.get(id);
        }
    }

    /** The parts of {@code level} (a copy). */
    public static Collection<PartEntity<?>> all(Level level) {
        Int2ObjectMap<PartEntity<?>> map = parts(level, false);
        if (map == null) {
            return List.of();
        }
        synchronized (PARTS) {
            return new ArrayList<>(map.values());
        }
    }

    /** Adds the parts whose box meets {@code box} to an untyped entity query. */
    public static void addParts(Level level, Entity except, AABB box, Predicate<? super Entity> filter, List<Entity> out) {
        for (PartEntity<?> part : all(level)) {
            if (part != except && part.parent() != except && part.getBoundingBox().intersects(box)
                    && (filter == null || filter.test(part)) && !out.contains(part)) {
                out.add(part);
            }
        }
    }

    /** Adds the parts that pass {@code test} and {@code filter} to a typed entity query. */
    @SuppressWarnings("unchecked")
    public static <T extends Entity> void addParts(Level level, EntityTypeTest<Entity, T> test, AABB box,
            Predicate<? super T> filter, List<? super T> out, int limit) {
        for (PartEntity<?> part : all(level)) {
            if (out.size() >= limit) {
                return;
            }
            if (!part.getBoundingBox().intersects(box)) {
                continue;
            }
            T cast = test.tryCast(part);
            if (cast != null && (filter == null || filter.test(cast)) && !out.contains(cast)) {
                out.add(cast);
            }
        }
    }

    /** Gives the parts their ids from the parent spawn packet (client): parent id + 1, + 2, ... */
    public static void assignClientIds(Entity entity, int parentId) {
        if (entity instanceof MultipartEntity multipart) {
            PartEntity<?>[] parts = multipart.getParts();
            for (int i = 0; i < parts.length; i++) {
                parts[i].setId(parentId + i + 1);
            }
        }
    }
}
