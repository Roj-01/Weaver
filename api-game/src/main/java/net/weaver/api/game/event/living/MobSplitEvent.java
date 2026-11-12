package net.weaver.api.game.event.living;

import net.minecraft.world.entity.Mob;
import net.weaver.api.event.Event;

/**
 * Fired once when a dying slime or magma cube is about to split into smaller
 * ones ({@code AbstractCubeMob#remove}). Cancelable: a canceled event spawns no
 * children. Each child also fires {@code LivingConversionPreEvent} and
 * {@code LivingConversionPostEvent}.
 */
public final class MobSplitEvent extends Event {
    private final Mob parent;
    private final int childCount;

    public MobSplitEvent(Mob parent, int childCount) {
        super(true);
        this.parent = parent;
        this.childCount = childCount;
    }

    public Mob parent() {
        return parent;
    }

    /** How many children vanilla will try to spawn. */
    public int childCount() {
        return childCount;
    }
}
