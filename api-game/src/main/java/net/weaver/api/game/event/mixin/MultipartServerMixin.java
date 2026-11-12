package net.weaver.api.game.event.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.weaver.api.game.entity.MultipartEntities;
import net.weaver.api.game.entity.PartEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A part id in a packet (a player hitting a part) resolves to the part. */
@Mixin(ServerLevel.class)
public abstract class MultipartServerMixin {
    @Inject(method = "getEntityOrPart(I)Lnet/minecraft/world/entity/Entity;", at = @At("RETURN"), cancellable = true)
    private void weaver$part(int id, CallbackInfoReturnable<Entity> callback) {
        if (callback.getReturnValue() == null) {
            PartEntity<?> part = MultipartEntities.part((ServerLevel) (Object) this, id);
            if (part != null) {
                callback.setReturnValue(part);
            }
        }
    }
}
