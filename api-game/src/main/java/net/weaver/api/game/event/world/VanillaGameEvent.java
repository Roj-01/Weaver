package net.weaver.api.game.event.world;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.weaver.api.event.Event;

/**
 * Fired when a vibration-style game event happens in a server level
 * ({@code ServerLevel#gameEvent}): steps, block placement, eating, chest
 * opening, and so on, the events that sculk sensors and wardens hear. Fires
 * very often; keep listeners cheap. Cancelable: a canceled event reaches no
 * listener (no sculk sensor, warden, or allay hears it).
 */
public final class VanillaGameEvent extends Event {
    private final ServerLevel level;
    private final Holder<GameEvent> gameEvent;
    private final Vec3 position;
    private final GameEvent.Context context;

    public VanillaGameEvent(ServerLevel level, Holder<GameEvent> gameEvent, Vec3 position, GameEvent.Context context) {
        super(true);
        this.level = level;
        this.gameEvent = gameEvent;
        this.position = position;
        this.context = context;
    }

    public ServerLevel level() {
        return level;
    }

    public Holder<GameEvent> gameEvent() {
        return gameEvent;
    }

    public Vec3 position() {
        return position;
    }

    /** The source entity and block state, either of which may be {@code null}. */
    public GameEvent.Context context() {
        return context;
    }
}
