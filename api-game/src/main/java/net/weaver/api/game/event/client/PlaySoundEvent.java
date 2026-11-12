package net.weaver.api.game.event.client;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.weaver.api.event.Event;

/**
 * Fired on the client when the sound engine is about to play a sound
 * ({@code SoundEngine#play}): world sounds, UI clicks, music, and records.
 * {@link #setSound(SoundInstance)} plays another sound; {@code null} plays
 * nothing.
 */
public final class PlaySoundEvent extends Event {
    private final SoundInstance originalSound;
    private SoundInstance sound;

    public PlaySoundEvent(SoundInstance sound) {
        this.originalSound = sound;
        this.sound = sound;
    }

    public SoundInstance originalSound() {
        return originalSound;
    }

    public SoundInstance sound() {
        return sound;
    }

    public void setSound(SoundInstance sound) {
        this.sound = sound;
    }
}
