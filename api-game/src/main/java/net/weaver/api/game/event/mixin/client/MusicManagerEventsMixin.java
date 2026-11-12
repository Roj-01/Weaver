package net.weaver.api.game.event.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.MusicManager;
import net.minecraft.sounds.Music;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.game.event.client.SelectMusicEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** The music picked for the current situation. */
@Mixin(MusicManager.class)
public abstract class MusicManagerEventsMixin {
    @Shadow
    private SoundInstance currentMusic;

    @ModifyExpressionValue(method = "tick()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;getSituationalMusic()Lnet/minecraft/sounds/Music;"))
    private Music weaver$selectMusic(Music music) {
        if (!GameEvents.hasListeners(SelectMusicEvent.class)) {
            return music;
        }
        Music chosen = GameEvents.post(new SelectMusicEvent(music, currentMusic), LogicalSide.CLIENT).music();
        return chosen == null ? music : chosen;
    }
}
