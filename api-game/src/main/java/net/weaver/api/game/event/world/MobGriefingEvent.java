package net.weaver.api.game.event.world;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.weaver.api.event.Event;

/**
 * Fired on the server when vanilla checks the {@code mobGriefing} game rule
 * on behalf of an entity, before the entity changes the world: endermen
 * taking and placing blocks, zombies breaking doors, creepers, ghasts, and
 * withers blowing up blocks, villagers harvesting, sheep eating grass, foxes
 * eating berries, rabbits raiding carrots, silverfish entering stone, ravagers
 * trampling, mobs picking up items, snow golems leaving snow, evokers turning
 * sheep, crops and turtle eggs being trampled, and more (every place vanilla
 * reads the rule; see the hook docs for the list).
 *
 * <p>{@link #canGrief()} starts as the game rule's value. Set it to let this
 * entity grief even with the rule off, or to stop it with the rule on. For
 * projectiles (fireballs and the like) the entity is the projectile's owner
 * when it has one. Fires often (mob item pickup checks run every tick); keep
 * listeners cheap.</p>
 */
public final class MobGriefingEvent extends Event {
    private final ServerLevel level;
    private final Entity entity;
    private final boolean ruleValue;
    private boolean canGrief;

    public MobGriefingEvent(ServerLevel level, Entity entity, boolean ruleValue) {
        this.level = level;
        this.entity = entity;
        this.ruleValue = ruleValue;
        this.canGrief = ruleValue;
    }

    public ServerLevel level() {
        return level;
    }

    /** The entity about to change the world. */
    public Entity entity() {
        return entity;
    }

    /** The {@code mobGriefing} game rule's value. */
    public boolean ruleValue() {
        return ruleValue;
    }

    public boolean canGrief() {
        return canGrief;
    }

    public void setCanGrief(boolean canGrief) {
        this.canGrief = canGrief;
    }
}
