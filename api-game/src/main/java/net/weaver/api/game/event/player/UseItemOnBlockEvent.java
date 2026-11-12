package net.weaver.api.game.event.player;

import java.util.Objects;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.weaver.api.event.Event;

/**
 * Fired when a player uses an item on a block, once for each of vanilla's
 * two phases: {@link Phase#BLOCK} lets the block react first
 * ({@code BlockStateBase#useItemOn}); if the block did not consume the click,
 * {@link Phase#ITEM} lets the item act ({@code ItemStack#useOn}, for example
 * placing a block). Fires on both logical sides; give the same answer on
 * both. Cancelable: a canceled event skips that phase and returns
 * {@link #result()}.
 *
 * <p>Mods may call {@code ItemStack#useOn} without a player; then
 * {@link #player()} is {@code null}.</p>
 */
public final class UseItemOnBlockEvent extends Event {
    /** Which vanilla step is about to run. */
    public enum Phase {
        BLOCK,
        ITEM
    }

    private final Player player;
    private final Level level;
    private final InteractionHand hand;
    private final ItemStack stack;
    private final BlockHitResult hit;
    private final Phase phase;
    private InteractionResult result = InteractionResult.PASS;

    public UseItemOnBlockEvent(Player player, Level level, InteractionHand hand, ItemStack stack,
                               BlockHitResult hit, Phase phase) {
        super(true);
        this.player = player;
        this.level = level;
        this.hand = hand;
        this.stack = stack;
        this.hit = hit;
        this.phase = phase;
    }

    public Player player() {
        return player;
    }

    public Level level() {
        return level;
    }

    public InteractionHand hand() {
        return hand;
    }

    public ItemStack stack() {
        return stack;
    }

    public BlockHitResult hit() {
        return hit;
    }

    public Phase phase() {
        return phase;
    }

    /** What a canceled event returns; {@code PASS} by default. */
    public InteractionResult result() {
        return result;
    }

    public void setResult(InteractionResult result) {
        this.result = Objects.requireNonNull(result, "result");
    }
}
