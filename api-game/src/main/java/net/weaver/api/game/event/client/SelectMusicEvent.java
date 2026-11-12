package net.weaver.api.game.event.client;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.Music;
import net.weaver.api.event.Event;

/**
 * Fired every client tick when the music manager picks the music for the
 * current situation ({@code MusicManager#tick}, from
 * {@code Minecraft#getSituationalMusic}). {@link #setMusic(Music)} picks
 * another; {@code null} plays nothing new.
 */
public final class SelectMusicEvent extends Event {
    private final Music originalMusic;
    private final SoundInstance playing;
    private Music music;

    public SelectMusicEvent(Music music, SoundInstance playing) {
        this.originalMusic = music;
        this.music = music;
        this.playing = playing;
    }

    /** What vanilla picked, or {@code null}. */
    public Music originalMusic() {
        return originalMusic;
    }

    /** The music playing now, or {@code null}. */
    public SoundInstance playing() {
        return playing;
    }

    public Music music() {
        return music;
    }

    public void setMusic(Music music) {
        this.music = music;
    }
}
