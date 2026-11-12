package net.weaver.nf;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.levelgen.Heightmap;
import net.weaver.api.game.entity.SpawnPlacementRules;

/**
 * Spawn placements and rules for entity types, as NeoForge's
 * {@code RegisterSpawnPlacementsEvent}, backed by Weaver's
 * {@code SpawnPlacementRules}.
 */
public final class SpawnPlacementsEvent {
    private static final SpawnPlacementsEvent INSTANCE = new SpawnPlacementsEvent();

    /** How a rule combines with the type's existing one. */
    public enum Operation {
        /** The rule replaces the existing one (with the placement and heightmap). */
        REPLACE,
        /** A spawn must pass both. */
        AND,
        /**
         * A spawn passes either. Weaver can only add conditions that must also
         * pass, so this behaves as {@link #REPLACE} with the given rule.
         */
        OR
    }

    public SpawnPlacementsEvent() {
    }

    public static SpawnPlacementsEvent get() {
        return INSTANCE;
    }

    public <T extends Mob> void register(EntityType<T> type, SpawnPlacementType placement, Heightmap.Types heightmap,
            SpawnPlacements.SpawnPredicate<T> predicate, Operation operation) {
        if (operation == Operation.AND) {
            SpawnPlacementRules.addCheck(type, (entityType, level, reason, pos, random) ->
                    predicate.test(type, level, reason, pos, random));
        } else {
            SpawnPlacementRules.register(type, placement, heightmap, predicate);
        }
    }

    public <T extends Mob> void register(EntityType<T> type, SpawnPlacementType placement, Heightmap.Types heightmap,
            SpawnPlacements.SpawnPredicate<T> predicate) {
        register(type, placement, heightmap, predicate, Operation.REPLACE);
    }
}
