package net.weaver.api.game.event.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Camera;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.game.event.client.CameraDistanceEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The third-person camera distance. */
@Mixin(Camera.class)
public abstract class CameraControlEventsMixin {
    @WrapOperation(method = "alignWithEntity(F)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F"))
    private float weaver$distance(Camera camera, float distance, Operation<Float> original) {
        if (!GameEvents.hasListeners(CameraDistanceEvent.class)) {
            return original.call(camera, distance);
        }
        float wanted = GameEvents.post(new CameraDistanceEvent(camera, distance), LogicalSide.CLIENT).distance();
        return original.call(camera, wanted);
    }
}
