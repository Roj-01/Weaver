package net.weaver.api.game.event.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.weaver.api.event.Event;

/**
 * Fired when a growing plant receives a random tick and may grow
 * ({@code randomTick} of crops, stems, bamboo, cactus, sugar cane, cocoa,
 * nether wart, sweet berries, growing vines, and chorus flowers). Cancelable: a
 * canceled event skips this growth tick. Bone meal does not fire this event.
 */
public final class CropGrowPreEvent extends Event {
    private final ServerLevel level;
    private final BlockPos pos;
    private final BlockState state;

    public CropGrowPreEvent(ServerLevel level, BlockPos pos, BlockState state) {
        super(true);
        this.level = level;
        this.pos = pos;
        this.state = state;
    }

    public ServerLevel level() {
        return level;
    }

    public BlockPos pos() {
        return pos;
    }

    public BlockState state() {
        return state;
    }
}
