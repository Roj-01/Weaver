package net.weaver.api.game.event.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.weaver.api.game.extension.EntityExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** {@link EntityExtension#shouldRiderSit}: humanoid riders of the vehicle are drawn standing. */
@Mixin(HumanoidMobRenderer.class)
public abstract class RiderPoseExtensionsMixin {
    @WrapOperation(method = "extractHumanoidRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;isPassenger()Z"))
    private static boolean weaver$riderSits(LivingEntity entity, Operation<Boolean> original) {
        if (entity.getVehicle() instanceof EntityExtension vehicle && !vehicle.shouldRiderSit()) {
            return false;
        }
        return original.call(entity);
    }
}
