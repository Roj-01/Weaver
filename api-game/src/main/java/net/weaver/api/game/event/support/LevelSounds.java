package net.weaver.api.game.event.support;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.weaver.api.game.event.world.PlayLevelSoundEvent;

/**
 * Shared logic for the {@code playSeededSound} hooks of both level classes.
 * When a listener changes the sound, the call is replayed with the new values
 * and the original is canceled; a per-thread marker keeps the replay from
 * posting again.
 */
public final class LevelSounds {
    private static final ThreadLocal<Boolean> REPLAYING = new ThreadLocal<>();

    private LevelSounds() {
    }

    /** @return {@code true} when the original call must be canceled */
    public static boolean atPosition(Level level, Entity except, double x, double y, double z, Holder<SoundEvent> sound,
                                     SoundSource source, float volume, float pitch, long seed) {
        if (REPLAYING.get() != null || !GameEvents.hasListeners(PlayLevelSoundEvent.class)) {
            return false;
        }
        PlayLevelSoundEvent event = GameEvents.post(
                new PlayLevelSoundEvent(level, null, new Vec3(x, y, z), sound, source, volume, pitch), level);
        if (GameEvents.canceled(event)) {
            return true;
        }
        if (!changed(event, sound, source, volume, pitch)) {
            return false;
        }
        REPLAYING.set(Boolean.TRUE);
        try {
            level.playSeededSound(except, x, y, z, event.sound(), event.source(), event.volume(), event.pitch(), seed);
        } finally {
            REPLAYING.remove();
        }
        return true;
    }

    /** @return {@code true} when the original call must be canceled */
    public static boolean atEntity(Level level, Entity except, Entity sourceEntity, Holder<SoundEvent> sound,
                                   SoundSource source, float volume, float pitch, long seed) {
        if (REPLAYING.get() != null || !GameEvents.hasListeners(PlayLevelSoundEvent.class)) {
            return false;
        }
        PlayLevelSoundEvent event = GameEvents.post(new PlayLevelSoundEvent(level, sourceEntity, sourceEntity.position(),
                sound, source, volume, pitch), level);
        if (GameEvents.canceled(event)) {
            return true;
        }
        if (!changed(event, sound, source, volume, pitch)) {
            return false;
        }
        REPLAYING.set(Boolean.TRUE);
        try {
            level.playSeededSound(except, sourceEntity, event.sound(), event.source(), event.volume(), event.pitch(), seed);
        } finally {
            REPLAYING.remove();
        }
        return true;
    }

    private static boolean changed(PlayLevelSoundEvent event, Holder<SoundEvent> sound, SoundSource source,
                                   float volume, float pitch) {
        return event.sound() != sound || event.source() != source || event.volume() != volume || event.pitch() != pitch;
    }
}
