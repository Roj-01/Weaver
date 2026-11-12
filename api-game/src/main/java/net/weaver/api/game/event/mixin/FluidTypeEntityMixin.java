package net.weaver.api.game.event.mixin;

import java.util.Map;
import java.util.Set;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityFluidInteraction;
import net.minecraft.world.level.material.Fluid;
import net.weaver.api.game.fluid.FluidType;
import net.weaver.api.game.fluid.FluidTypes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Modded fluid types push entities with their own current and reset fall distance. */
@Mixin(Entity.class)
public abstract class FluidTypeEntityMixin {
    @Shadow
    @Final
    private EntityFluidInteraction fluidInteraction;

    @Shadow
    public abstract boolean isPushedByFluid();

    @Shadow
    public abstract void resetFallDistance();

    @Shadow
    public abstract double getFluidHeight(TagKey<Fluid> tag);

    @ModifyArg(method = "<init>(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/EntityFluidInteraction;<init>(Ljava/util/Set;)V"))
    private Set<TagKey<Fluid>> weaver$fluidsWithCurrent(Set<TagKey<Fluid>> vanilla) {
        return FluidTypes.withCurrents(vanilla);
    }

    @Inject(method = "updateFluidInteraction()Z", at = @At("RETURN"))
    private void weaver$modFluids(CallbackInfoReturnable<Boolean> callback) {
        Map<TagKey<Fluid>, FluidType> types = FluidTypes.all();
        if (types.isEmpty()) {
            return;
        }
        for (Map.Entry<TagKey<Fluid>, FluidType> entry : types.entrySet()) {
            if (getFluidHeight(entry.getKey()) <= 0.0) {
                continue;
            }
            FluidType type = entry.getValue();
            if (type.resetsFallDistance()) {
                resetFallDistance();
            }
            if (isPushedByFluid() && type.currentStrength() > 0.0) {
                fluidInteraction.applyCurrentTo(entry.getKey(), (Entity) (Object) this, type.currentStrength());
            }
        }
    }
}
