package net.weaver.nf;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.weaver.api.game.entity.PartEntity;

/**
 * Finds the whole creature behind a hitbox: Weaver parts (and so these
 * {@link net.weaver.nf.PartEntity} parts), the ender dragon parts, and parts of
 * other libraries that offer a public {@code getParent()}.
 */
public final class PartLink {
    private static final Map<Class<?>, Optional<MethodHandle>> PARENT = new ConcurrentHashMap<>();

    private PartLink() {
    }

    public static boolean isPart(Entity entity) {
        return entity instanceof PartEntity<?> || entity instanceof EnderDragonPart
                || entity != null && handle(entity.getClass()).isPresent();
    }

    /** The parent of a part, or null when {@code entity} is not a part. */
    public static Entity parent(Entity entity) {
        if (entity instanceof PartEntity<?> part) {
            return part.parent();
        }
        if (entity instanceof EnderDragonPart part) {
            return part.parentMob;
        }
        if (entity == null) {
            return null;
        }
        Optional<MethodHandle> handle = handle(entity.getClass());
        if (handle.isEmpty()) {
            return null;
        }
        try {
            return (Entity) handle.get().invoke(entity);
        } catch (Throwable failure) {
            return null;
        }
    }

    /** The parent when {@code entity} is a part, else the entity itself. */
    public static Entity whole(Entity entity) {
        Entity parent = parent(entity);
        return parent != null ? parent : entity;
    }

    private static Optional<MethodHandle> handle(Class<?> type) {
        return PARENT.computeIfAbsent(type, each -> {
            try {
                MethodHandle found = MethodHandles.publicLookup().findVirtual(each, "getParent", MethodType.methodType(Entity.class));
                return Optional.of(found.asType(MethodType.methodType(Entity.class, Entity.class)));
            } catch (ReflectiveOperationException | IllegalArgumentException failure) {
                return Optional.empty();
            }
        });
    }
}
