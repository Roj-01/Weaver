package net.weaver.api.game.event.mixin.client;

import com.mojang.blaze3d.audio.Channel;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.world.phys.Vec3;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.game.event.client.SoundSourceEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Audio channels configured for a sound. {@code lambda$play$0} is the task
 * {@code SoundEngine#play} runs on the sound thread to set up the channel,
 * for static and streamed sounds alike.
 */
@Mixin(SoundEngine.class)
public abstract class SoundChannelEventsMixin {
    @Inject(method = "lambda$play$0(FFLnet/minecraft/client/resources/sounds/SoundInstance;FZZLnet/minecraft/world/phys/Vec3;Lcom/mojang/blaze3d/audio/Channel;)V",
            at = @At("RETURN"))
    private static void weaver$channel(float pitch, float volume, SoundInstance sound, float range, boolean looping,
                                       boolean relative, Vec3 position, Channel channel, CallbackInfo callback) {
        if (GameEvents.hasListeners(SoundSourceEvent.class)) {
            GameEvents.post(new SoundSourceEvent(sound, channel), LogicalSide.CLIENT);
        }
    }
}
