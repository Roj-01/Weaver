package net.weaver.api.game.entity;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.weaver.api.game.event.mixin.SpawnPlacementsInvoker;

/**
 * Where mobs may spawn naturally: the placement of a mod type (on the ground, in
 * water, ...), its heightmap, and its spawn rule; and extra conditions on any
 * type, vanilla types included, checked after the type rule (no zombies near a
 * ward block, for example).
 *
 * <pre>{@code
 * SpawnPlacementRules.register(MY_FROG, SpawnPlacementTypes.ON_GROUND,
 *         Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mob::checkMobSpawnRules);
 * SpawnPlacementRules.addCheck(EntityType.ZOMBIE, (type, level, reason, pos, random) -> !Wards.near(level, pos));
 * }</pre>
 *
 * <p>Register during the register phase. A type registers its placement once. A
 * failing check is logged and allows the spawn.</p>
 */
public final class SpawnPlacementRules {
    /** An extra spawn condition. */
    @FunctionalInterface
    public interface Check {
        boolean test(EntityType<?> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos, RandomSource random);
    }

    private static final Map<EntityType<?>, List<Check>> CHECKS = new ConcurrentHashMap<>();

    private SpawnPlacementRules() {
    }

    /** Gives a mob type of a mod its spawn placement, heightmap, and rule. */
    public static <T extends Mob> void register(EntityType<T> type, SpawnPlacementType placement, Heightmap.Types heightmap,
            SpawnPlacements.SpawnPredicate<T> rule) {
        SpawnPlacementsInvoker.weaver$register(Objects.requireNonNull(type, "type"), Objects.requireNonNull(placement, "placement"),
                Objects.requireNonNull(heightmap, "heightmap"), Objects.requireNonNull(rule, "rule"));
    }

    /** Adds a condition every spawn of {@code type} must also pass. */
    public static void addCheck(EntityType<?> type, Check check) {
        CHECKS.computeIfAbsent(Objects.requireNonNull(type, "type"), key -> new CopyOnWriteArrayList<>())
                .add(Objects.requireNonNull(check, "check"));
    }

    /** Whether every added condition passes. Called by the loader after the type rule passed. */
    @net.weaver.api.annotation.Internal
    public static boolean check(EntityType<?> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos,
            RandomSource random) {
        List<Check> checks = CHECKS.get(type);
        if (checks == null) {
            return true;
        }
        for (Check check : checks) {
            try {
                if (!check.test(type, level, reason, pos, random)) {
                    return false;
                }
            } catch (RuntimeException failure) {
                System.out.println("[weaver] SPAWN CHECK FAILED type=" + type + " reason=" + failure + " action=allowed");
            }
        }
        return true;
    }
}
