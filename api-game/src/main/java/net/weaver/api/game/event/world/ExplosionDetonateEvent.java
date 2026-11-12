package net.weaver.api.game.event.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ServerExplosion;
import net.weaver.api.event.Event;

import java.util.List;

/**
 * Fired when an explosion has computed what it affects, before it damages
 * entities and breaks blocks ({@code ServerExplosion#explode}). Both lists are
 * mutable: remove positions to protect blocks, remove entities to spare them.
 */
public final class ExplosionDetonateEvent extends Event {
    private final ServerLevel level;
    private final ServerExplosion explosion;
    private final List<BlockPos> blocks;
    private final List<Entity> entities;

    public ExplosionDetonateEvent(ServerLevel level, ServerExplosion explosion, List<BlockPos> blocks,
                                  List<Entity> entities) {
        this.level = level;
        this.explosion = explosion;
        this.blocks = blocks;
        this.entities = entities;
    }

    public ServerLevel level() {
        return level;
    }

    public ServerExplosion explosion() {
        return explosion;
    }

    /** Block positions the explosion will break; mutable. */
    public List<BlockPos> blocks() {
        return blocks;
    }

    /** Entities the explosion will damage and push; mutable. */
    public List<Entity> entities() {
        return entities;
    }
}
