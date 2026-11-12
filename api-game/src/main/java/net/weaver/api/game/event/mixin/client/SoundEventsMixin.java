package net.weaver.api.game.event.mixin.client;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.game.event.client.PlaySoundEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Sounds about to be played by the sound engine. */
@Mixin(SoundEngine.class)
public abstract class SoundEventsMixin {
    @Unique
    private static final ThreadLocal<Object> weaver$reinvoking = new ThreadLocal<>();

    @Inject(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;",
            at = @At("HEAD"), cancellable = true)
    private void weaver$play(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> callback) {
        if (weaver$reinvoking.get() == this || !GameEvents.hasListeners(PlaySoundEvent.class)) {
            return;
        }
        PlaySoundEvent event = GameEvents.post(new PlaySoundEvent(sound), LogicalSide.CLIENT);
        if (event.sound() == null) {
            callback.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
        } else if (event.sound() != sound) {
            Object previous = weaver$reinvoking.get();
            weaver$reinvoking.set(this);
            try {
                callback.setReturnValue(((SoundEngine) (Object) this).play(event.sound()));
            } finally {
                weaver$reinvoking.set(previous);
            }
        }
    }
}
