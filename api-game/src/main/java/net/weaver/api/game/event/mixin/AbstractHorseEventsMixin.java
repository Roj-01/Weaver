package net.weaver.api.game.event.mixin;

import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.weaver.api.game.event.world.AnimalTameEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Taming horses, donkeys, mules, llamas, and camels. */
@Mixin(AbstractHorse.class)
public abstract class AbstractHorseEventsMixin {
    @Inject(method = "tameWithName(Lnet/minecraft/world/entity/player/Player;)Z", at = @At("HEAD"), cancellable = true)
    private void weaver$tame(Player player, CallbackInfoReturnable<Boolean> callback) {
        AbstractHorse self = (AbstractHorse) (Object) this;
        if (GameEvents.canceled(GameEvents.post(new AnimalTameEvent(self, player), self.level()))) {
            callback.setReturnValue(false);
        }
    }
}
