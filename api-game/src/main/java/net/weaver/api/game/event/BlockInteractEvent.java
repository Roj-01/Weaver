package net.weaver.api.game.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Typed variant of {@link net.weaver.api.event.events.BlockInteractEvent}.
 *
 * <p>Fired on the server thread at method entry, before
 * {@code ServerPlayerGameMode#useItemOn}. Observation-only and not cancelable.
 * The payloads are narrowed to the game types. The inherited accessors stay
 * available with their {@link Object} descriptors for binary compatibility.
 */
public final class BlockInteractEvent extends net.weaver.api.event.events.BlockInteractEvent {

    /**
     * Creates a typed block-interaction event.
     *
     * @param player    the interacting player
     * @param level     the level the interaction happens in
     * @param stack     the item stack used for the interaction
     * @param hand      the hand holding the stack
     * @param hitResult the block hit result
     */
    public BlockInteractEvent(Object player, Object level, Object stack, Object hand,
                              Object hitResult) {
        super(player, level, stack, hand, hitResult);
    }

    @Override
    public ServerPlayer player() {
        return (ServerPlayer) super.player();
    }

    @Override
    public Level level() {
        return (Level) super.level();
    }

    @Override
    public ItemStack stack() {
        return (ItemStack) super.stack();
    }

    @Override
    public InteractionHand hand() {
        return (InteractionHand) super.hand();
    }

    @Override
    public BlockHitResult hitResult() {
        return (BlockHitResult) super.hitResult();
    }
}
