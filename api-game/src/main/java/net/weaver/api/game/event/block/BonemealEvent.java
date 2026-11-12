package net.weaver.api.game.event.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.weaver.api.event.Event;

/**
 * Fired when bone meal is about to be applied to a block
 * ({@code BoneMealItem#growCrop}, from a player or a dispenser). Cancelable: a
 * canceled event grows nothing and consumes no bone meal.
 */
public final class BonemealEvent extends Event {
    private final Level level;
    private final BlockPos pos;
    private final BlockState state;
    private final ItemStack stack;
    private final Player player;

    public BonemealEvent(Level level, BlockPos pos, BlockState state, ItemStack stack, Player player) {
        super(true);
        this.level = level;
        this.pos = pos;
        this.state = state;
        this.stack = stack;
        this.player = player;
    }

    public Level level() {
        return level;
    }

    public BlockPos pos() {
        return pos;
    }

    public BlockState state() {
        return state;
    }

    /** The bone meal stack. */
    public ItemStack stack() {
        return stack;
    }

    /** The player using the bone meal, or {@code null} for a dispenser. */
    public Player player() {
        return player;
    }
}
