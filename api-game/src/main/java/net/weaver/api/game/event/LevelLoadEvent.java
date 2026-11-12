package net.weaver.api.game.event;

import net.minecraft.world.level.Level;

/**
 * Typed variant of {@link net.weaver.api.event.events.LevelLoadEvent}.
 *
 * <p>The level accessor is narrowed to {@link Level}. The inherited
 * {@link Object}-typed accessor stays available for binary compatibility.
 */
public final class LevelLoadEvent extends net.weaver.api.event.events.LevelLoadEvent {

    /**
     * Creates a typed level-load event.
     *
     * @param level the level that was created or loaded
     */
    public LevelLoadEvent(Object level) {
        super(level);
    }

    @Override
    public Level level() {
        return (Level) super.level();
    }
}
