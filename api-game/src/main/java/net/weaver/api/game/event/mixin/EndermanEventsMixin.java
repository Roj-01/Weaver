package net.weaver.api.game.event.mixin;

import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.player.Player;
import net.weaver.api.game.event.living.EndermanAngerEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Endermen getting angry at players who look at them. */
@Mixin(Enderman.class)
public abstract class EndermanEventsMixin {
    @Inject(method = "isBeingStaredBy(Lnet/minecraft/world/entity/player/Player;)Z", at = @At("RETURN"),
            cancellable = true)
    private void weaver$anger(Player player, CallbackInfoReturnable<Boolean> callback) {
        if (!callback.getReturnValueZ()) {
            return;
        }
        Enderman self = (Enderman) (Object) this;
        if (GameEvents.canceled(GameEvents.post(new EndermanAngerEvent(self, player), self.level()))) {
            callback.setReturnValue(false);
        }
    }
}
