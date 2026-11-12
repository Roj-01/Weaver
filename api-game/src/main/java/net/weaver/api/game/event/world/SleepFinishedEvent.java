package net.weaver.api.game.event.world;

import java.util.Objects;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.clock.ClockTimeMarker;
import net.weaver.api.event.Event;

/**
 * Fired when enough players slept through the night and the level is about
 * to move its clock to the wake-up time ({@code ServerLevel#tick}, through
 * {@code ServerClockManager#moveToTimeMarker}). {@link #setMarker(ResourceKey)}
 * moves the clock to another time marker. Cancelable: a canceled event leaves
 * the time unchanged; players still wake up.
 */
public final class SleepFinishedEvent extends Event {
    private final ServerLevel level;
    private ResourceKey<ClockTimeMarker> marker;

    public SleepFinishedEvent(ServerLevel level, ResourceKey<ClockTimeMarker> marker) {
        super(true);
        this.level = level;
        this.marker = marker;
    }

    public ServerLevel level() {
        return level;
    }

    /** The time marker the clock moves to; vanilla's is {@code wake_up_from_sleep}. */
    public ResourceKey<ClockTimeMarker> marker() {
        return marker;
    }

    public void setMarker(ResourceKey<ClockTimeMarker> marker) {
        this.marker = Objects.requireNonNull(marker, "marker");
    }
}
