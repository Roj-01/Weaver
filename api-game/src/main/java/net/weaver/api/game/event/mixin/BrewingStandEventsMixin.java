package net.weaver.api.game.event.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.weaver.api.game.event.block.PotionBrewPostEvent;
import net.weaver.api.game.event.block.PotionBrewPreEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Brewing stand results. */
@Mixin(BrewingStandBlockEntity.class)
public abstract class BrewingStandEventsMixin {
    @Inject(method = "doBrew(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BrewingStandBlockEntity;)V",
            at = @At("HEAD"), cancellable = true)
    private static void weaver$brewPre(ServerLevel level, BlockPos pos, BrewingStandBlockEntity stand,
                                       CallbackInfo callback) {
        if (GameEvents.canceled(GameEvents.post(new PotionBrewPreEvent(level, pos, stand), level))) {
            callback.cancel();
        }
    }

    @Inject(method = "doBrew(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BrewingStandBlockEntity;)V",
            at = @At("TAIL"))
    private static void weaver$brewPost(ServerLevel level, BlockPos pos, BrewingStandBlockEntity stand,
                                        CallbackInfo callback) {
        GameEvents.post(new PotionBrewPostEvent(level, pos, stand), level);
    }
}
