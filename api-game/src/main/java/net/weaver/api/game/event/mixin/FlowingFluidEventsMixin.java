package net.weaver.api.game.event.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.weaver.api.game.event.block.CreateFluidSourceEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** New fluid source blocks (for example infinite water). */
@Mixin(FlowingFluid.class)
public abstract class FlowingFluidEventsMixin {
    @Unique
    private static final ThreadLocal<BlockPos> weaver$pos = new ThreadLocal<>();
    @Unique
    private static final ThreadLocal<BlockState> weaver$state = new ThreadLocal<>();

    @Inject(method = "getNewLiquid(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/level/material/FluidState;",
            at = @At("HEAD"))
    private void weaver$remember(ServerLevel level, BlockPos pos, BlockState state,
                                 CallbackInfoReturnable<FluidState> callback) {
        weaver$pos.set(pos);
        weaver$state.set(state);
    }

    @WrapOperation(method = "getNewLiquid(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/world/level/material/FluidState;",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/material/FlowingFluid;canConvertToSource(Lnet/minecraft/server/level/ServerLevel;)Z"))
    private boolean weaver$createSource(FlowingFluid self, ServerLevel level, Operation<Boolean> original) {
        boolean canConvert = original.call(self, level);
        BlockPos pos = weaver$pos.get();
        if (pos == null || !GameEvents.hasListeners(CreateFluidSourceEvent.class)) {
            return canConvert;
        }
        CreateFluidSourceEvent event = GameEvents.post(
                new CreateFluidSourceEvent(level, pos, weaver$state.get(), canConvert), level);
        return event.canConvert();
    }
}
