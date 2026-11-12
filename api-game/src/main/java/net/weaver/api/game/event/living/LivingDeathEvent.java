package net.weaver.api.game.event.living;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.weaver.api.event.Event;

/**
 * Fired on the server when a living entity is about to die: at the start of
 * {@code LivingEntity#die}, and of {@code ServerPlayer#die} for players (which
 * does not call the former). Cancelable: a canceled event skips the death
 * entirely (no death message, drops, experience, kill credit or death
 * animation).
 *
 * <p>A listener that cancels must also give the entity its life back, for
 * example {@code entity.setHealth(1.0F)}; an entity left at zero health stays
 * dying and is removed by the game a second later anyway.</p>
 *
 * <p>The observation events {@code LivingEntityDeathEvent} and
 * {@code PlayerDeathEvent} are posted by the loader's own hooks at the very
 * start of those methods, before this event, so they report the attempt even
 * when this event is then canceled. Like NeoForge's {@code LivingDeathEvent},
 * this event is posted whenever {@code die} is called, also for an entity that
 * has already died.</p>
 */
public final class LivingDeathEvent extends Event {
    private final LivingEntity entity;
    private final DamageSource source;

    public LivingDeathEvent(LivingEntity entity, DamageSource source) {
        super(true);
        this.entity = entity;
        this.source = source;
    }

    /** The entity that is dying (a {@code ServerPlayer} for a player). */
    public LivingEntity entity() {
        return entity;
    }

    /** What killed it. */
    public DamageSource source() {
        return source;
    }
}
