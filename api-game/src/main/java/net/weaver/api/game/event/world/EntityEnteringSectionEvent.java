package net.weaver.api.game.event.world;

import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.Entity;
import net.weaver.api.event.Event;

/**
 * Fired when an entity moves into another chunk section (a 16x16x16 cube), from the entity
 * section managers of server and client levels. Not fired when the entity is first added.
 */
public final class EntityEnteringSectionEvent extends Event {
    private final Entity entity;
    private final long oldSection;
    private final long newSection;

    public EntityEnteringSectionEvent(Entity entity, long oldSection, long newSection) {
        super(false);
        this.entity = entity;
        this.oldSection = oldSection;
        this.newSection = newSection;
    }

    /** The entity. */
    public Entity entity() {
        return entity;
    }

    /** The packed section the entity left ({@link SectionPos#asLong}). */
    public long oldSection() {
        return oldSection;
    }

    /** The packed section the entity entered. */
    public long newSection() {
        return newSection;
    }

    /** The section the entity left. */
    public SectionPos oldPos() {
        return SectionPos.of(oldSection);
    }

    /** The section the entity entered. */
    public SectionPos newPos() {
        return SectionPos.of(newSection);
    }

    /** Whether the entity entered another chunk column, not just another section height. */
    public boolean didChunkChange() {
        return SectionPos.x(oldSection) != SectionPos.x(newSection) || SectionPos.z(oldSection) != SectionPos.z(newSection);
    }
}
