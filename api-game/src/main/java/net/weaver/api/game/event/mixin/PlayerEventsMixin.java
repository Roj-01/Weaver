package net.weaver.api.game.event.mixin;

import net.minecraft.world.entity.player.Player;
import net.weaver.api.game.event.support.GameEvents;
import net.weaver.api.game.event.player.PlayerWakeUpEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Player events shared by client and server players: waking up. */
@Mixin(Player.class)
public abstract class PlayerEventsMixin {
    @Inject(method = "stopSleepInBed(ZZ)V", at = @At("HEAD"))
    private void weaver$wakeUp(boolean wakeImmediately, boolean updateLevel, CallbackInfo callback) {
        Player self = (Player) (Object) this;
        if (self.isSleeping()) {
            GameEvents.post(new PlayerWakeUpEvent(self, wakeImmediately, updateLevel), self.level());
        }
    }
}
