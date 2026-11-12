package net.weaver.api.game.event.mixin;

import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.world.entity.Entity;
import net.weaver.api.game.entity.MultipartEntities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** On the client, the parts take the ids that follow their parent, as the server gave them. */
@Mixin(Entity.class)
public abstract class MultipartIdsMixin {
    @Inject(method = "recreateFromPacket(Lnet/minecraft/network/protocol/game/ClientboundAddEntityPacket;)V", at = @At("RETURN"))
    private void weaver$partIds(ClientboundAddEntityPacket packet, CallbackInfo callback) {
        MultipartEntities.assignClientIds((Entity) (Object) this, packet.getId());
    }
}
