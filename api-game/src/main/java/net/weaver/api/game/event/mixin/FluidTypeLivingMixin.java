package net.weaver.api.game.event.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.weaver.api.game.fluid.FluidType;
import net.weaver.api.game.fluid.FluidTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Swimming, jumping, and drowning in modded fluid types. Water and lava keep vanilla behaviour. */
@Mixin(LivingEntity.class)
public abstract class FluidTypeLivingMixin extends Entity {
    protected FluidTypeLivingMixin(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Shadow
    public abstract boolean isAffectedByFluids();

    @Shadow
    public abstract Vec3 getFluidFallingAdjustedMovement(double gravity, boolean falling, Vec3 movement);

    @Inject(method = "shouldTravelInFluid(Lnet/minecraft/world/level/material/FluidState;)Z", at = @At("RETURN"), cancellable = true)
    private void weaver$modFluidTravel(FluidState state, CallbackInfoReturnable<Boolean> callback) {
        if (!callback.getReturnValueZ() && !FluidTypes.all().isEmpty() && isAffectedByFluids()
                && FluidTypes.swimmable(this) != null) {
            callback.setReturnValue(true);
        }
    }

    @Inject(method = "travelInFluid(Lnet/minecraft/world/phys/Vec3;)V", at = @At("HEAD"), cancellable = true)
    private void weaver$modFluidMovement(Vec3 input, CallbackInfo callback) {
        if (FluidTypes.all().isEmpty() || isInWater() || isInLava()) {
            return;
        }
        FluidType type = FluidTypes.swimmable(this);
        if (type == null) {
            return;
        }
        callback.cancel();
        double gravity = getEffectiveGravity();
        boolean falling = getDeltaMovement().y <= 0.0;
        moveRelative((float) type.speed(), input);
        move(MoverType.SELF, getDeltaMovement());
        Vec3 slowed = getDeltaMovement().multiply(type.drag(), type.verticalDrag(), type.drag());
        setDeltaMovement(getFluidFallingAdjustedMovement(gravity, falling, slowed));
    }

    // Jumping up through a swimmable modded fluid uses the water jump path.
    @WrapOperation(method = "aiStep()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;getFluidHeight(Lnet/minecraft/tags/TagKey;)D"))
    private double weaver$swimHeight(LivingEntity self, TagKey<Fluid> tag, Operation<Double> original) {
        double height = original.call(self, tag);
        return tag == FluidTags.WATER && !FluidTypes.all().isEmpty() ? Math.max(height, FluidTypes.swimHeight(self)) : height;
    }

    @WrapOperation(method = "aiStep()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;isInWater()Z"))
    private boolean weaver$swimming(LivingEntity self, Operation<Boolean> original) {
        return original.call(self) || (!FluidTypes.all().isEmpty() && FluidTypes.swimmable(self) != null);
    }

    @WrapOperation(method = "baseTick()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;isEyeInFluid(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean weaver$drowning(LivingEntity self, TagKey<Fluid> tag, Operation<Boolean> original) {
        boolean inside = original.call(self, tag);
        return inside || (tag == FluidTags.WATER && !FluidTypes.all().isEmpty() && FluidTypes.drowns(self));
    }
}
