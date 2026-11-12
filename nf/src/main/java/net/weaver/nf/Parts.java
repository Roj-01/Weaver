package net.weaver.nf;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.weaver.api.game.entity.MultipartEntities;
import net.weaver.api.game.entity.PartEntity;

/**
 * The parts in a level, as NeoForge's {@code Level#getPartEntities}: backed by
 * Weaver's {@code MultipartEntities}, which tracks every multipart entity added to
 * a level (and gives client parts their ids).
 */
public final class Parts {
    private Parts() {
    }

    /** Adds the parts in {@code box} (except {@code except} and its parts) to {@code out}. */
    public static void addParts(Level level, Entity except, AABB box, Predicate<? super Entity> filter, List<Entity> out) {
        MultipartEntities.addParts(level, except, box, filter, out);
    }

    public static <T extends Entity> void addParts(Level level, EntityTypeTest<Entity, T> test, AABB box,
            Predicate<? super T> filter, List<? super T> out, int limit) {
        MultipartEntities.addParts(level, test, box, filter, out, limit);
    }

    /** The entity or part with network id {@code id}, or null. */
    public static Entity byId(Level level, int id) {
        PartEntity<?> part = MultipartEntities.part(level, id);
        return part != null ? part : level.getEntity(id);
    }
}
