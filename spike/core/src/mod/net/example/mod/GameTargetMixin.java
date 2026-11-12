package net.example.mod;

import net.example.game.GameTarget;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Injects into the game class and calls mod-owned code directly. */
@Mixin(GameTarget.class)
public class GameTargetMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void weaver$proof(CallbackInfo info) {
        ProofHooks.hit();
    }
}
