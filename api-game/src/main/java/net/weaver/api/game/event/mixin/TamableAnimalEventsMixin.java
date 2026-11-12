package net.weaver.api.game.event.mixin;

import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.weaver.api.game.event.world.AnimalTameEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Taming wolves, cats, parrots, and other tamable animals. */
@Mixin(TamableAnimal.class)
public abstract class TamableAnimalEventsMixin {
    @Inject(method = "tame(Lnet/minecraft/world/entity/player/Player;)V", at = @At("HEAD"), cancellable = true)
    private void weaver$tame(Player player, CallbackInfo callback) {
        TamableAnimal self = (TamableAnimal) (Object) this;
        if (GameEvents.canceled(GameEvents.post(new AnimalTameEvent(self, player), self.level()))) {
            callback.cancel();
        }
    }
}
