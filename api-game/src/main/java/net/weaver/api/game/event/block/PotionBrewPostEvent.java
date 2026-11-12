package net.weaver.api.game.event.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.weaver.api.event.Event;

/**
 * Fired after a brewing stand brewed ({@code BrewingStandBlockEntity#doBrew}
 * returned). Listeners may change the result bottles in slots 0 to 2.
 */
public final class PotionBrewPostEvent extends Event {
    private final ServerLevel level;
    private final BlockPos pos;
    private final BrewingStandBlockEntity stand;

    public PotionBrewPostEvent(ServerLevel level, BlockPos pos, BrewingStandBlockEntity stand) {
        this.level = level;
        this.pos = pos;
        this.stand = stand;
    }

    public ServerLevel level() {
        return level;
    }

    public BlockPos pos() {
        return pos;
    }

    public BrewingStandBlockEntity stand() {
        return stand;
    }
}
