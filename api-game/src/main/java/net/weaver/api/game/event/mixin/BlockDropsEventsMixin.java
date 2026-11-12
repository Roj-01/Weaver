package net.weaver.api.game.event.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.weaver.api.game.event.block.BlockDropsEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.List;

/** Block item drops, for every {@code Block#dropResources} overload. */
@Mixin(Block.class)
public abstract class BlockDropsEventsMixin {
    @WrapOperation(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/Block;getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;)Ljava/util/List;"))
    private static List<ItemStack> weaver$drops(BlockState state, ServerLevel level, BlockPos pos,
                                                BlockEntity blockEntity,
                                                Operation<List<ItemStack>> original) {
        return weaver$post(original.call(state, level, pos, blockEntity), state, level, pos, blockEntity,
                null, ItemStack.EMPTY);
    }

    @WrapOperation(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/Block;getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;)Ljava/util/List;"))
    private static List<ItemStack> weaver$dropsWithBlockEntity(BlockState state, ServerLevel level, BlockPos pos,
                                                               BlockEntity blockEntity,
                                                               Operation<List<ItemStack>> original) {
        return weaver$post(original.call(state, level, pos, blockEntity), state, level, pos, blockEntity,
                null, ItemStack.EMPTY);
    }

    @WrapOperation(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/Block;getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemInstance;)Ljava/util/List;"))
    private static List<ItemStack> weaver$dropsWithBreaker(BlockState state, ServerLevel level, BlockPos pos,
                                                           BlockEntity blockEntity, Entity breaker,
                                                           ItemInstance tool,
                                                           Operation<List<ItemStack>> original) {
        List<ItemStack> drops = original.call(state, level, pos, blockEntity, breaker, tool);
        ItemStack toolStack = tool instanceof ItemStack stack ? stack : ItemStack.EMPTY;
        return weaver$post(drops, state, level, pos, blockEntity, breaker, toolStack);
    }

    @Unique
    private static List<ItemStack> weaver$post(List<ItemStack> drops, BlockState state, ServerLevel level,
                                               BlockPos pos, BlockEntity blockEntity, Entity breaker,
                                               ItemStack tool) {
        if (!GameEvents.hasListeners(BlockDropsEvent.class)) {
            return drops;
        }
        List<ItemStack> mutable = new ArrayList<>(drops);
        BlockDropsEvent event = GameEvents.post(
                new BlockDropsEvent(level, pos, state, blockEntity, breaker, tool, mutable), level);
        if (GameEvents.canceled(event)) {
            return new ArrayList<>();
        }
        mutable.removeIf(stack -> stack == null || stack.isEmpty());
        return mutable;
    }
}
