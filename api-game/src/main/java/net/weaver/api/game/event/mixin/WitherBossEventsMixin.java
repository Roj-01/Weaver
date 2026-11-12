package net.weaver.api.game.event.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.weaver.api.game.event.living.LivingDestroyBlockEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Blocks the wither breaks around itself. {@code destroyBlock} returns a value,
 * so this is a {@code WrapOperation}: {@code WrapWithCondition} only accepts
 * calls that return {@code void}.
 */
@Mixin(WitherBoss.class)
public abstract class WitherBossEventsMixin {
    @WrapOperation(method = "customServerAiStep(Lnet/minecraft/server/level/ServerLevel;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;)Z"))
    private boolean weaver$destroyBlock(ServerLevel level, BlockPos pos, boolean drop, Entity breaker,
                                        Operation<Boolean> original) {
        WitherBoss self = (WitherBoss) (Object) this;
        if (GameEvents.canceled(GameEvents.post(
                new LivingDestroyBlockEvent(self, pos, level.getBlockState(pos)), level))) {
            return false;
        }
        return original.call(level, pos, drop, breaker);
    }
}
