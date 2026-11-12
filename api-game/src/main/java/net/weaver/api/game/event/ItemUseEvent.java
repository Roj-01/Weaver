package net.weaver.api.game.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Typed variant of {@link net.weaver.api.event.events.ItemUseEvent}.
 *
 * <p>Fired on the server thread at method entry, before
 * {@code ServerPlayerGameMode#useItem}. Observation-only and not cancelable.
 * The payloads are narrowed to the game types. The inherited accessors stay
 * available with their {@link Object} descriptors for binary compatibility.
 */
public final class ItemUseEvent extends net.weaver.api.event.events.ItemUseEvent {

    /**
     * Creates a typed item-use event.
     *
     * @param player the player using the item
     * @param level  the level the item is used in
     * @param stack  the item stack being used
     * @param hand   the hand holding the stack
     */
    public ItemUseEvent(Object player, Object level, Object stack, Object hand) {
        super(player, level, stack, hand);
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
}
