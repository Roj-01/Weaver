package net.weaver.api.game.event.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.weaver.api.event.Event;

import java.util.List;

/**
 * Fired when a block is about to drop its items ({@code Block#dropResources},
 * after the loot table produced them). Cancelable: a canceled event drops no
 * items. {@link #drops()} is mutable; add, remove, or replace stacks to change
 * what drops. Experience from the block is not part of this event.
 */
public final class BlockDropsEvent extends Event {
    private final ServerLevel level;
    private final BlockPos pos;
    private final BlockState state;
    private final BlockEntity blockEntity;
    private final Entity breaker;
    private final ItemStack tool;
    private final List<ItemStack> drops;

    public BlockDropsEvent(ServerLevel level, BlockPos pos, BlockState state, BlockEntity blockEntity,
                           Entity breaker, ItemStack tool, List<ItemStack> drops) {
        super(true);
        this.level = level;
        this.pos = pos;
        this.state = state;
        this.blockEntity = blockEntity;
        this.breaker = breaker;
        this.tool = tool;
        this.drops = drops;
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

    /** The block entity that was at the position, or {@code null}. */
    public BlockEntity blockEntity() {
        return blockEntity;
    }

    /** The entity that broke the block, or {@code null} (explosions, pistons, and so on). */
    public Entity breaker() {
        return breaker;
    }

    /** The tool used, or {@link ItemStack#EMPTY}. */
    public ItemStack tool() {
        return tool;
    }

    /** The stacks that will drop; mutable. */
    public List<ItemStack> drops() {
        return drops;
    }
}
