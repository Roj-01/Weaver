package net.weaver.api.game.event.mixin.griefing;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
import net.weaver.api.game.event.support.Griefing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Mob griefing: ravagers trampling crops. */
@Mixin(targets = {"net.minecraft.world.level.block.CropBlock", "net.minecraft.world.level.block.PitcherCropBlock"})
public abstract class GriefCropTrampleMixin {
    // Wraps the whole method so the entity is popped even when it throws.
    @WrapMethod(method = "entityInside(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/InsideBlockEffectApplier;Z)V")
    private void weaver$griefContext(net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos, net.minecraft.world.entity.Entity entity, net.minecraft.world.entity.InsideBlockEffectApplier effects, boolean moved, Operation<Void> original) {
        Griefing.push(entity);
        try {
            original.call(state, level, pos, entity, effects, moved);
        } finally {
            Griefing.pop();
        }
    }

    @WrapOperation(method = "entityInside(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/InsideBlockEffectApplier;Z)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/gamerules/GameRules;get(Lnet/minecraft/world/level/gamerules/GameRule;)Ljava/lang/Object;"))
    private Object weaver$grief(GameRules rules, GameRule<?> rule, Operation<Object> original) {
        return Griefing.check(original.call(rules, rule), rule, Griefing.context());
    }
}
