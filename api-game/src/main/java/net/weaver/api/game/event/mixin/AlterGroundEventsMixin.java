package net.weaver.api.game.event.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.treedecorators.AlterGroundDecorator;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.game.event.block.AlterGroundEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The ground block a tree decorator places (podzol under spruce). */
@Mixin(AlterGroundDecorator.class)
public abstract class AlterGroundEventsMixin {
    @WrapOperation(method = "placeBlockAt(Lnet/minecraft/world/level/levelgen/feature/treedecorators/TreeDecorator$Context;Lnet/minecraft/core/BlockPos;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/feature/stateproviders/BlockStateProvider;getOptionalState(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState weaver$ground(BlockStateProvider provider, LevelAccessor level, RandomSource random, BlockPos pos,
            Operation<BlockState> original) {
        BlockState state = original.call(provider, level, random, pos);
        if (!GameEvents.hasListeners(AlterGroundEvent.class)) {
            return state;
        }
        return GameEvents.post(new AlterGroundEvent(level, pos.immutable(), state), LogicalSide.SERVER).newState();
    }
}
