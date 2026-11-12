package net.weaver.api.game.event.mixin.client;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.level.GameType;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.game.event.client.ClientGameModeChangeEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Game mode changes of players in the tab list. */
@Mixin(PlayerInfo.class)
public abstract class PlayerInfoEventsMixin {
    @Shadow
    private GameType gameMode;

    @Unique
    private GameType weaver$oldGameMode;

    @Inject(method = "setGameMode(Lnet/minecraft/world/level/GameType;)V", at = @At("HEAD"))
    private void weaver$gameModeBefore(GameType newMode, CallbackInfo callback) {
        weaver$oldGameMode = gameMode;
    }

    @Inject(method = "setGameMode(Lnet/minecraft/world/level/GameType;)V", at = @At("RETURN"))
    private void weaver$gameModeAfter(GameType newMode, CallbackInfo callback) {
        GameType old = weaver$oldGameMode;
        weaver$oldGameMode = null;
        if (old != newMode) {
            GameEvents.post(new ClientGameModeChangeEvent((PlayerInfo) (Object) this, old, newMode), LogicalSide.CLIENT);
        }
    }
}
