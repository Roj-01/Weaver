package net.weaver.api.game.event.mixin.extension;

import net.minecraft.server.level.ServerPlayer;
import net.weaver.api.game.extension.ExtensionHooks;
import net.weaver.api.game.extension.ItemExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** {@link ItemExtension#canBeDropped} on the server: the drop key. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerExtensionsMixin {
    @Inject(method = "drop(Z)V", at = @At("HEAD"), cancellable = true)
    private void weaver$canDrop(boolean all, CallbackInfo callback) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (!ExtensionHooks.canDropSelected(self)) {
            callback.cancel();
            // Send the inventory back, in case the client removed the stack already.
            if (self.connection != null) {
                self.containerMenu.sendAllDataToRemote();
            }
        }
    }
}
