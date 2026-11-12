package net.weaver.api.game.event.world;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.VillageSiege;
import net.minecraft.world.phys.Vec3;
import net.weaver.api.event.Event;

/**
 * Fired when a zombie siege found a place to start near a village
 * ({@code VillageSiege#tryToSetupSiege}), at midnight. Cancelable: a
 * canceled event discards that spot, as if vanilla had found none there;
 * vanilla tries up to ten spots per player.
 */
public final class VillageSiegeEvent extends Event {
    private final VillageSiege siege;
    private final ServerLevel level;
    private final Vec3 position;

    public VillageSiegeEvent(VillageSiege siege, ServerLevel level, Vec3 position) {
        super(true);
        this.siege = siege;
        this.level = level;
        this.position = position;
    }

    public VillageSiege siege() {
        return siege;
    }

    public ServerLevel level() {
        return level;
    }

    /** Where the zombies would spawn. */
    public Vec3 position() {
        return position;
    }
}
