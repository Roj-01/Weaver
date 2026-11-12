package net.weaver.api.game.event.client;

import com.mojang.blaze3d.audio.Channel;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.weaver.api.event.Event;

/**
 * Fired on the sound thread once an audio channel was configured for a sound
 * (pitch, volume, attenuation, looping, position; inside {@code SoundEngine#play}),
 * just before its buffer or stream is attached and playback starts. Covers
 * both static and streamed sounds. Adjust the channel here.
 */
public final class SoundSourceEvent extends Event {
    private final SoundInstance sound;
    private final Channel channel;

    public SoundSourceEvent(SoundInstance sound, Channel channel) {
        this.sound = sound;
        this.channel = channel;
    }

    public SoundInstance sound() {
        return sound;
    }

    public Channel channel() {
        return channel;
    }
}
