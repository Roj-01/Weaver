package net.weaver.api.game.event.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.ChorusFlowerBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.weaver.api.game.event.block.CropGrowPostEvent;
import net.weaver.api.game.event.block.CropGrowPreEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Growth ticks of every plant NeoForge treats as a crop. Each target declares
 * its own {@code randomTick}; a subclass that delegates with {@code super}
 * still fires the events once.
 */
@Mixin({CropBlock.class, StemBlock.class, BambooStalkBlock.class, CactusBlock.class, SugarCaneBlock.class,
        CocoaBlock.class, NetherWartBlock.class, SweetBerryBushBlock.class, GrowingPlantHeadBlock.class,
        ChorusFlowerBlock.class})
public abstract class CropGrowEventsMixin {
    @Inject(method = "randomTick(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V",
            at = @At("HEAD"), cancellable = true)
    private void weaver$growPre(BlockState state, ServerLevel level, BlockPos pos, RandomSource random,
                                CallbackInfo callback) {
        if (GameEvents.hasListeners(CropGrowPreEvent.class)
                && GameEvents.canceled(GameEvents.post(new CropGrowPreEvent(level, pos, state), level))) {
            callback.cancel();
        }
    }

    @Inject(method = "randomTick(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V",
            at = @At("RETURN"))
    private void weaver$growPost(BlockState state, ServerLevel level, BlockPos pos, RandomSource random,
                                 CallbackInfo callback) {
        if (!GameEvents.hasListeners(CropGrowPostEvent.class)) {
            return;
        }
        BlockState now = level.getBlockState(pos);
        if (now != state) {
            GameEvents.post(new CropGrowPostEvent(level, pos, state, now), level);
        }
    }
}
