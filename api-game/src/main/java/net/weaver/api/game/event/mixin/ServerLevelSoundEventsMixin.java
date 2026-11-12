package net.weaver.api.game.event.mixin;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.weaver.api.game.event.support.LevelSounds;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sounds the server level sends to nearby players. */
@Mixin(ServerLevel.class)
public abstract class ServerLevelSoundEventsMixin {
    @Inject(method = "playSeededSound(Lnet/minecraft/world/entity/Entity;DDDLnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V",
            at = @At("HEAD"), cancellable = true)
    private void weaver$soundAt(Entity except, double x, double y, double z, Holder<SoundEvent> sound, SoundSource source,
                                float volume, float pitch, long seed, CallbackInfo callback) {
        if (LevelSounds.atPosition((ServerLevel) (Object) this, except, x, y, z, sound, source, volume, pitch, seed)) {
            callback.cancel();
        }
    }

    @Inject(method = "playSeededSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V",
            at = @At("HEAD"), cancellable = true)
    private void weaver$soundOn(Entity except, Entity sourceEntity, Holder<SoundEvent> sound, SoundSource source,
                                float volume, float pitch, long seed, CallbackInfo callback) {
        if (LevelSounds.atEntity((ServerLevel) (Object) this, except, sourceEntity, sound, source, volume, pitch, seed)) {
            callback.cancel();
        }
    }
}
