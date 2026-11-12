package net.weaver.api.game.extension;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Behavior a mod's block can override, by position and context, where vanilla
 * only reads a fixed value, a tag, or a hard-coded block. A {@code Block}
 * subclass implements this interface and overrides the methods it needs; every
 * default is vanilla's own behavior.
 *
 * <pre>{@code
 * public class RopeBlock extends Block implements BlockExtension {
 *     public boolean isClimbable(BlockState state, LevelReader level, BlockPos pos, LivingEntity entity) {
 *         return true;
 *     }
 * }
 * }</pre>
 *
 * <p>Weaver asks the block whose state is involved: the block an entity stands
 * in or on, the soil under a plant, the block an explosion reaches, and so on.
 * Methods run on the thread that runs the vanilla logic (server or client).</p>
 */
public interface BlockExtension {
    /**
     * Whether a living entity inside this block climbs it like a ladder.
     * Default: the block is in {@code minecraft:climbable}.
     */
    default boolean isClimbable(BlockState state, LevelReader level, BlockPos pos, LivingEntity entity) {
        return state.is(BlockTags.CLIMBABLE);
    }

    /**
     * The friction of this block for an entity moving on it (living entities,
     * dropped items, and experience orbs): 0.6 is normal, 0.98 is ice. Default:
     * the block's fixed friction.
     */
    default float getFriction(BlockState state, LevelReader level, BlockPos pos, Entity entity) {
        return state.getBlock().getFriction();
    }

    /**
     * Whether {@code plant} can grow on this block (the soil at {@code pos}), for
     * plants built on {@code VegetationBlock}: flowers, grass, saplings, crops,
     * mushrooms. Default: vanilla decides (the plant's own soil rule).
     */
    default TriState canSustainPlant(BlockState state, BlockGetter level, BlockPos pos, BlockState plant) {
        return TriState.DEFAULT;
    }

    /** How much this block resists {@code explosion}. Default: the block's fixed resistance. */
    default float getExplosionResistance(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
        return state.getBlock().getExplosionResistance();
    }

    /**
     * Whether fire on the {@code side} face of this block burns forever, like
     * netherrack. Default: the block is in the dimension's infiniburn tag.
     */
    default boolean isFireSource(BlockState state, LevelReader level, BlockPos pos, Direction side) {
        return state.is(level.dimensionType().infiniburn());
    }

    /**
     * Whether this block pulls the blocks next to it along when a piston moves
     * it, like slime and honey. Default: false (slime and honey are vanilla's).
     */
    default boolean isStickyBlock(BlockState state) {
        return false;
    }

    /**
     * When this block is sticky, or touches a sticky block, whether the two stick
     * together. Default: true (vanilla only keeps slime and honey apart).
     */
    default boolean canStickTo(BlockState state, BlockState other) {
        return true;
    }

    /**
     * Whether redstone dust next to this block connects to it from
     * {@code direction} (null when asked without a side). Default: vanilla decides
     * (signal sources, repeaters, observers).
     */
    default TriState connectsToRedstone(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return TriState.DEFAULT;
    }

    /**
     * Whether an open trapdoor right above this block can be climbed like a
     * ladder, as vanilla does for a ladder facing the same way. Default: false
     * (vanilla ladders keep working).
     */
    default boolean makesOpenTrapdoorAboveClimbable(BlockState state, LevelReader level, BlockPos pos,
            BlockState trapdoor) {
        return false;
    }

    /**
     * How likely fire is to burn this block away, like vanilla's per-block
     * values (planks 20, wool 60). 0 keeps vanilla's value. Waterlogged blocks
     * never burn.
     */
    default int getFlammability(BlockState state) {
        return 0;
    }

    /** How likely fire next to this block is to spread onto it (planks 5, wool 30). 0 keeps vanilla's value. */
    default int getFireSpreadSpeed(BlockState state) {
        return 0;
    }

    /**
     * Called after fire burned this block away (vanilla primes TNT here). The
     * block is already gone or replaced by fire. Server only. Default: nothing.
     */
    default void onBurnedByFire(BlockState state, Level level, BlockPos pos) {
    }

    /**
     * How mobs see this block when finding a path (for example {@code DAMAGING},
     * which they avoid, or {@code BLOCKED}). Null keeps vanilla's answer.
     */
    default net.minecraft.world.level.pathfinder.PathType getPathType(BlockState state, BlockGetter level, BlockPos pos) {
        return null;
    }

    /**
     * The enchanting power this block gives a nearby enchanting table, like a
     * bookshelf (1). Above 0 it counts even outside
     * {@code minecraft:enchantment_power_provider}, with the same placement rules;
     * 0 keeps vanilla's rule. The table counts up to 15 in total.
     */
    default float getEnchantPowerBonus(BlockState state, LevelReader level, BlockPos pos) {
        return 0.0F;
    }

    /**
     * Called on the client when this block is broken, before vanilla spawns its
     * break particles. Return true to replace them (spawn your own, or none).
     * Default: false.
     */
    default boolean addDestroyEffects(BlockState state, Level level, BlockPos pos) {
        return false;
    }

    /**
     * Called on the client while a player mines this block, before vanilla spawns
     * the crack particles on {@code side}. Return true to replace them. Default: false.
     */
    default boolean addHitEffects(BlockState state, Level level, BlockPos pos, Direction side) {
        return false;
    }

    /**
     * Called when {@code entity} sprints over this block, before vanilla spawns the
     * running particles. Return true to replace them. Default: false.
     */
    default boolean addRunningEffects(BlockState state, Level level, BlockPos pos, Entity entity) {
        return false;
    }

    /**
     * Called on the server when {@code entity} lands on this block after a fall,
     * before vanilla sends {@code count} landing particles. Return true to replace
     * them. Default: false.
     */
    default boolean addLandingEffects(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos,
            LivingEntity entity, int count) {
        return false;
    }

    /**
     * For a block that also implements {@code BeaconBeamBlock}: the ARGB color it
     * gives a beacon beam passing through it, instead of its dye color. Null keeps
     * the dye color.
     */
    default Integer getBeaconColor(BlockState state, LevelReader level, BlockPos pos, BlockPos beaconPos) {
        return null;
    }

    /**
     * Whether {@code player} harvests this block at {@code pos}: mines it at full
     * speed and gets its drops. Vanilla decides by the block state and the tool
     * only. Default: vanilla decides.
     */
    default TriState canHarvestBlock(BlockState state, BlockGetter level, BlockPos pos,
            net.minecraft.world.entity.player.Player player) {
        return TriState.DEFAULT;
    }

    /**
     * Whether a fluid next to this block draws its overlay texture on the face
     * toward it, as water does next to glass and leaves. Use it for see-through
     * blocks that are neither. {@code pos} is this block's position. Client only.
     * Default: false (vanilla's glass and leaves keep the overlay).
     */
    default boolean shouldDisplayFluidOverlay(BlockState state, BlockGetter level, BlockPos pos,
            net.minecraft.world.level.material.FluidState fluid) {
        return false;
    }

    /**
     * The sounds of this block at {@code pos}: stepping on it, landing on it,
     * placing, breaking, and mining it. {@code entity} is the one stepping, landing,
     * or placing, or null. Entities with step sounds of their own (most animals)
     * do not ask. Default: the state's sound type.
     */
    default net.minecraft.world.level.block.SoundType getSoundType(BlockState state, LevelReader level, BlockPos pos,
            Entity entity) {
        return state.getSoundType();
    }

    /**
     * Whether this block hides the face of {@code neighbor} that touches it, from
     * {@code side} (the side of this block the face is on). Use it for blocks that
     * draw over their neighbors (facades, connected glass). Client only. Default:
     * false (vanilla's occlusion still applies).
     */
    default boolean hidesNeighborFace(BlockState state, BlockState neighbor, Direction side) {
        return false;
    }
}
