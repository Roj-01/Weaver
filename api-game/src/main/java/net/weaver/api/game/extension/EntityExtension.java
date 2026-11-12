package net.weaver.api.game.extension;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Behavior a mod's entity can override where vanilla decides by entity type or
 * a tag. An {@code Entity} subclass implements this interface and overrides the
 * methods it needs; every default is vanilla's own behavior.
 */
public interface EntityExtension {
    /**
     * Whether this entity, landing on farmland after falling
     * {@code fallDistance} blocks, tramples it back to dirt. Asked only when
     * vanilla would trample it. Default: true.
     */
    default boolean canTrampleFarmland(BlockState state, Level level, BlockPos pos, double fallDistance) {
        return true;
    }

    /**
     * Whether this entity walks on powder snow instead of sinking, like the
     * types in {@code minecraft:powder_snow_walkable_mobs}. Default: false
     * (vanilla's tag and leather boots still apply).
     */
    default boolean walksOnPowderSnow() {
        return false;
    }

    /**
     * Called when the level starts tracking this entity: it was added, or its
     * chunk loaded. Both sides. Default: nothing.
     */
    default void onAddedToLevel() {
    }

    /**
     * Called when the level stops tracking this entity: it was removed, or its
     * chunk unloaded. Both sides. Default: nothing.
     */
    default void onRemovedFromLevel() {
    }

    /**
     * For a vehicle: whether humanoid riders are drawn sitting. False draws them
     * standing, as on a platform. Client only. Default: true.
     */
    default boolean shouldRiderSit() {
        return true;
    }
}
