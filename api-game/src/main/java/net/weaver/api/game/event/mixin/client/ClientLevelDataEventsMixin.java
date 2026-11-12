package net.weaver.api.game.event.mixin.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.Difficulty;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.game.event.client.ClientDifficultyChangeEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The client's copy of the world difficulty. */
@Mixin(ClientLevel.ClientLevelData.class)
public abstract class ClientLevelDataEventsMixin {
    @Shadow
    private Difficulty difficulty;

    @Unique
    private Difficulty weaver$oldDifficulty;

    @Inject(method = "setDifficulty(Lnet/minecraft/world/Difficulty;)V", at = @At("HEAD"))
    private void weaver$difficultyBefore(Difficulty newDifficulty, CallbackInfo callback) {
        weaver$oldDifficulty = difficulty;
    }

    @Inject(method = "setDifficulty(Lnet/minecraft/world/Difficulty;)V", at = @At("RETURN"))
    private void weaver$difficultyAfter(Difficulty newDifficulty, CallbackInfo callback) {
        Difficulty old = weaver$oldDifficulty;
        weaver$oldDifficulty = null;
        if (old != newDifficulty) {
            GameEvents.post(new ClientDifficultyChangeEvent(old, newDifficulty), LogicalSide.CLIENT);
        }
    }
}
