package net.weaver.api.game.event;

import net.minecraft.world.level.Level;

/**
 * Typed variant of {@link net.weaver.api.event.events.LevelUnloadEvent}.
 *
 * <p>The level accessor is narrowed to {@link Level}. The inherited
 * {@link Object}-typed accessor stays available for binary compatibility.
 */
public final class LevelUnloadEvent extends net.weaver.api.event.events.LevelUnloadEvent {

    /**
     * Creates a typed level-unload event.
     *
     * @param level the level being unloaded
     */
    public LevelUnloadEvent(Object level) {
        super(level);
    }

    @Override
    public Level level() {
        return (Level) super.level();
    }
}
