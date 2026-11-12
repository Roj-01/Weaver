package net.weaver.api.game.event.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.portal.PortalShape;
import net.weaver.api.event.Event;

/**
 * Fired when fire placed inside an obsidian frame is about to light a nether
 * portal ({@code BaseFireBlock#onPlace} found a valid empty portal shape).
 * Cancelable: a canceled event lights no portal.
 */
public final class PortalSpawnEvent extends Event {
    private final LevelAccessor level;
    private final BlockPos pos;
    private final PortalShape shape;

    public PortalSpawnEvent(LevelAccessor level, BlockPos pos, PortalShape shape) {
        super(true);
        this.level = level;
        this.pos = pos;
        this.shape = shape;
    }

    public LevelAccessor level() {
        return level;
    }

    /** The position of the fire block. */
    public BlockPos pos() {
        return pos;
    }

    public PortalShape shape() {
        return shape;
    }
}
