package net.weaver.api.game.event.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.weaver.api.event.Event;

/**
 * Fired when a brewing stand finished its timer and is about to brew
 * ({@code BrewingStandBlockEntity#doBrew}). Cancelable: a canceled event brews
 * nothing and keeps the ingredient. The stand is a {@code Container}: slots 0
 * to 2 hold the bottles and slot 3 the ingredient.
 */
public final class PotionBrewPreEvent extends Event {
    private final ServerLevel level;
    private final BlockPos pos;
    private final BrewingStandBlockEntity stand;

    public PotionBrewPreEvent(ServerLevel level, BlockPos pos, BrewingStandBlockEntity stand) {
        super(true);
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
