package net.weaver.api.game.event.mixin;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.material.Fluid;
import net.weaver.api.game.fluid.FluidTypes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** The bucket empty sound of a modded fluid type (vanilla picks only between water and lava). */
@Mixin(BucketItem.class)
public abstract class FluidTypeBucketMixin {
    @Shadow
    @Final
    protected Fluid content;

    @ModifyVariable(method = "playEmptySound(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;)V",
            at = @At("STORE"), ordinal = 0)
    private SoundEvent weaver$emptySound(SoundEvent vanilla) {
        SoundEvent custom = FluidTypes.emptySound(content);
        return custom != null ? custom : vanilla;
    }
}
