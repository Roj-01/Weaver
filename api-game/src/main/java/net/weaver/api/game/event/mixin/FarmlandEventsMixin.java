package net.weaver.api.game.event.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.weaver.api.game.event.block.FarmlandTrampleEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Farmland trampling: {@code EntityExtension.canTrampleFarmland}, then {@link FarmlandTrampleEvent}. */
@Mixin(FarmlandBlock.class)
public abstract class FarmlandEventsMixin {
    @Unique
    private static final ThreadLocal<Double> weaver$fallDistance = new ThreadLocal<>();

    @Inject(method = "fallOn(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;D)V",
            at = @At("HEAD"))
    private void weaver$rememberFall(Level level, BlockState state, BlockPos pos, Entity entity,
                                     double fallDistance, CallbackInfo callback) {
        weaver$fallDistance.set(fallDistance);
    }

    @WrapWithCondition(method = "fallOn(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;D)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/FarmlandBlock;turnToBaseBlock(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V"))
    private boolean weaver$trample(FarmlandBlock self, Entity entity, BlockState state, Level level,
                                   BlockPos pos) {
        Double fallDistance = weaver$fallDistance.get();
        weaver$fallDistance.remove();
        if (entity instanceof net.weaver.api.game.extension.EntityExtension extension
                && !extension.canTrampleFarmland(state, level, pos, fallDistance == null ? 0.0 : fallDistance)) {
            return false;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return true;
        }
        FarmlandTrampleEvent event = new FarmlandTrampleEvent(serverLevel, pos, state, entity,
                fallDistance == null ? 0.0 : fallDistance);
        return !GameEvents.canceled(GameEvents.post(event, serverLevel));
    }
}
