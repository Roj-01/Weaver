package net.weaver.api.game.event.mixin;

import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A mod object (block, item, entity type) whose constructor threw after it
 * created its intrusive holder leaves that holder unregistered, and vanilla
 * then refuses to freeze the registry: the game stops at startup with a message
 * that names no mod. The failed registration was already reported by the
 * registering code; here the orphan holders are logged with their classes (which
 * name the mod) and dropped, so the game starts without that one object.
 */
@Mixin(MappedRegistry.class)
public abstract class MappedRegistryFreezeMixin<T> {
    @Shadow
    private Map<T, Holder.Reference<T>> unregisteredIntrusiveHolders;

    @Shadow
    @Final
    private ResourceKey<? extends net.minecraft.core.Registry<T>> key;

    @Inject(method = "freeze()Lnet/minecraft/core/Registry;", at = @At("HEAD"))
    private void weaver$dropOrphanHolders(CallbackInfoReturnable<net.minecraft.core.Registry<T>> callback) {
        Map<T, Holder.Reference<T>> orphans = unregisteredIntrusiveHolders;
        if (orphans == null || orphans.isEmpty()) {
            return;
        }
        for (T value : orphans.keySet()) {
            System.out.println("[weaver] REGISTRY ORPHAN registry=" + key.identifier()
                    + " class=" + (value == null ? "null" : value.getClass().getName())
                    + " action=dropped (an object was created but never registered;"
                    + " look for an earlier registration failure of the mod that owns this class)");
        }
        orphans.clear();
    }

    /**
     * Vanilla gives its own block and fluid states a global id and builds each block
     * state's cache (its fluid, shapes, light) once, during bootstrap. States of blocks
     * and fluids that mods register later would have neither: a modded liquid block
     * would hold no fluid, and its states could not be sent to clients (a player
     * placing such a block was disconnected). Every state still missing is added,
     * and its cache built, when the registry freezes; block items registered
     * outside {@code Items} are linked to their blocks the same way.
     */
    @Inject(method = "freeze()Lnet/minecraft/core/Registry;", at = @At("RETURN"))
    private void weaver$registerModStates(CallbackInfoReturnable<net.minecraft.core.Registry<T>> callback) {
        try {
            if (key.equals(net.minecraft.core.registries.Registries.FLUID)) {
                int added = 0;
                for (Object value : (MappedRegistry<?>) (Object) this) {
                    for (net.minecraft.world.level.material.FluidState state
                            : ((net.minecraft.world.level.material.Fluid) value).getStateDefinition().getPossibleStates()) {
                        if (net.minecraft.world.level.material.Fluid.FLUID_STATE_REGISTRY.getId(state) < 0) {
                            net.minecraft.world.level.material.Fluid.FLUID_STATE_REGISTRY.add(state);
                            added++;
                        }
                    }
                }
                if (added > 0) {
                    System.out.println("[weaver] MOD FLUID STATES registered=" + added);
                }
            } else if (key.equals(net.minecraft.core.registries.Registries.BLOCK)) {
                int added = 0;
                for (Object value : (MappedRegistry<?>) (Object) this) {
                    for (net.minecraft.world.level.block.state.BlockState state
                            : ((net.minecraft.world.level.block.Block) value).getStateDefinition().getPossibleStates()) {
                        if (net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY.getId(state) < 0) {
                            net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY.add(state);
                            state.initCache();
                            added++;
                        }
                    }
                }
                if (added > 0) {
                    System.out.println("[weaver] MOD BLOCK STATES registered=" + added + " (ids and caches)");
                }
            } else if (key.equals(net.minecraft.core.registries.Registries.ITEM)) {
                // Vanilla links a block item to its block (Block#asItem, pick block, drops by block)
                // only for items made through Items; a mod that registers a BlockItem directly
                // would leave its block without an item.
                int linked = 0;
                for (Object value : (MappedRegistry<?>) (Object) this) {
                    if (value instanceof net.minecraft.world.item.BlockItem blockItem
                            && !net.minecraft.world.item.Item.BY_BLOCK.containsKey(blockItem.getBlock())) {
                        blockItem.registerBlocks(net.minecraft.world.item.Item.BY_BLOCK, blockItem);
                        linked++;
                    }
                }
                if (linked > 0) {
                    System.out.println("[weaver] MOD BLOCK ITEMS linked=" + linked);
                }
            }
        } catch (RuntimeException failure) {
            System.out.println("[weaver] MOD STATE REGISTRATION FAILED registry=" + key.identifier() + " reason=" + failure);
        }
    }
}
