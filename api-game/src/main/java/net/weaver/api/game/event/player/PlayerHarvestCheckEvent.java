package net.weaver.api.game.event.player;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.weaver.api.event.Event;

/**
 * Fired when the game checks whether a player's held tool can harvest a block
 * ({@code Player#hasCorrectToolForDrops}). The answer decides whether the
 * block drops anything and how fast it breaks. Fires on both logical sides.
 */
public final class PlayerHarvestCheckEvent extends Event {
    private final Player player;
    private final BlockState state;
    private boolean canHarvest;

    public PlayerHarvestCheckEvent(Player player, BlockState state, boolean canHarvest) {
        this.player = player;
        this.state = state;
        this.canHarvest = canHarvest;
    }

    public Player player() {
        return player;
    }

    public BlockState state() {
        return state;
    }

    public boolean canHarvest() {
        return canHarvest;
    }

    public void setCanHarvest(boolean canHarvest) {
        this.canHarvest = canHarvest;
    }
}
