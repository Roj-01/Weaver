package net.weaver.api.game.event.world;

import java.util.Objects;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.weaver.api.event.Event;

/**
 * Fired when a level plays a sound, on either side: at a position
 * ({@code playSeededSound} with coordinates) or following an entity
 * ({@code playSeededSound} with a source entity). On the server the sound is
 * sent to nearby players; on the client it is played locally.
 * {@link #setSound(Holder)}, {@link #setSource(SoundSource)},
 * {@link #setVolume(float)}, and {@link #setPitch(float)} change it.
 * Cancelable: a canceled sound is not played or sent.
 */
public final class PlayLevelSoundEvent extends Event {
    private final Level level;
    private final Entity entity;
    private final Vec3 position;
    private Holder<SoundEvent> sound;
    private SoundSource source;
    private float volume;
    private float pitch;

    public PlayLevelSoundEvent(Level level, Entity entity, Vec3 position, Holder<SoundEvent> sound,
                               SoundSource source, float volume, float pitch) {
        super(true);
        this.level = level;
        this.entity = entity;
        this.position = position;
        this.sound = sound;
        this.source = source;
        this.volume = volume;
        this.pitch = pitch;
    }

    public Level level() {
        return level;
    }

    /** The entity the sound follows, or {@code null} for a positioned sound. */
    public Entity entity() {
        return entity;
    }

    /** Where the sound plays; the entity's position for an entity sound. */
    public Vec3 position() {
        return position;
    }

    public Holder<SoundEvent> sound() {
        return sound;
    }

    public void setSound(Holder<SoundEvent> sound) {
        this.sound = Objects.requireNonNull(sound, "sound");
    }

    public SoundSource source() {
        return source;
    }

    public void setSource(SoundSource source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    public float volume() {
        return volume;
    }

    public void setVolume(float volume) {
        net.weaver.api.game.event.support.Finite.check(volume, "volume");
        this.volume = volume;
    }

    public float pitch() {
        return pitch;
    }

    public void setPitch(float pitch) {
        net.weaver.api.game.event.support.Finite.check(pitch, "pitch");
        this.pitch = pitch;
    }
}
