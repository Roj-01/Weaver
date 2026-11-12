package net.weaver.api.game.event.mixin.extension;

import net.minecraft.world.entity.Entity;
import net.weaver.api.game.extension.EntityExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** {@link EntityExtension#onAddedToLevel} and {@link EntityExtension#onRemovedFromLevel} on the server. */
@Mixin(targets = "net.minecraft.server.level.ServerLevel$EntityCallbacks")
public abstract class EntityTrackingExtensionsMixin {
    @Inject(method = "onTrackingStart(Lnet/minecraft/world/entity/Entity;)V", at = @At("TAIL"))
    private void weaver$added(Entity entity, CallbackInfo callback) {
        if (entity instanceof EntityExtension extension) {
            extension.onAddedToLevel();
        }
    }

    @Inject(method = "onTrackingEnd(Lnet/minecraft/world/entity/Entity;)V", at = @At("TAIL"))
    private void weaver$removed(Entity entity, CallbackInfo callback) {
        if (entity instanceof EntityExtension extension) {
            extension.onRemovedFromLevel();
        }
    }
}
