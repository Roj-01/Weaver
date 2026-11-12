package net.weaver.api.game.event;

import net.minecraft.server.level.ServerLevel;

/**
 * Typed variant of {@link net.weaver.api.event.events.LevelSaveEvent}.
 *
 * <p>The level accessor is narrowed to {@link ServerLevel}. The inherited
 * {@link Object}-typed accessor stays available for binary compatibility.
 */
public final class LevelSaveEvent extends net.weaver.api.event.events.LevelSaveEvent {

    /**
     * Creates a typed level-save event.
     *
     * @param level the server level being saved
     * @param phase the save phase
     */
    public LevelSaveEvent(Object level, Phase phase) {
        super(level, phase);
    }

    @Override
    public ServerLevel level() {
        return (ServerLevel) super.level();
    }
}
