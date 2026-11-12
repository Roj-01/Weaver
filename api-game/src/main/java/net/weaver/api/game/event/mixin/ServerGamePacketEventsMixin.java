package net.weaver.api.game.event.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.weaver.api.game.event.living.LivingSwapHandsEvent;
import net.weaver.api.game.event.player.ClientInformationUpdatedEvent;
import net.minecraft.server.level.ClientInformation;
import net.weaver.api.game.event.player.PlayerSwitchHotbarSlotPostEvent;
import net.weaver.api.game.event.player.PlayerSwitchHotbarSlotPreEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hotbar slot changes and hand swaps requested by the client. Both hooks run
 * after {@code PacketUtils.ensureRunningOnSameThread}, on the server thread.
 *
 * <p>The swap is two {@code setItemInHand} calls: the off hand first (taking
 * the main-hand stack), then the main hand (taking the old off-hand stack).
 * The event is posted at the first call, while both hands still hold their
 * original stacks, and kept for the second.</p>
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketEventsMixin {
    @Unique
    private static final ThreadLocal<LivingSwapHandsEvent> weaver$swap = new ThreadLocal<>();

    @Unique
    private ServerPlayer weaver$player() {
        return ((ServerGamePacketListenerImpl) (Object) this).player;
    }

    @Inject(method = "handleSetCarriedItem(Lnet/minecraft/network/protocol/game/ServerboundSetCarriedItemPacket;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V",
                    shift = At.Shift.AFTER),
            cancellable = true)
    private void weaver$hotbarPre(ServerboundSetCarriedItemPacket packet, CallbackInfo callback) {
        ServerPlayer player = weaver$player();
        int current = player.getInventory().getSelectedSlot();
        int wanted = packet.getSlot();
        if (wanted == current || wanted < 0 || wanted >= Inventory.getSelectionSize()) {
            return;
        }
        if (GameEvents.canceled(GameEvents.post(new PlayerSwitchHotbarSlotPreEvent(player, current, wanted), player.level()))) {
            player.connection.send(new ClientboundSetHeldSlotPacket(current));
            callback.cancel();
        }
    }

    @WrapOperation(method = "handleSetCarriedItem(Lnet/minecraft/network/protocol/game/ServerboundSetCarriedItemPacket;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;setSelectedSlot(I)V"))
    private void weaver$hotbarPost(Inventory inventory, int slot, Operation<Void> original) {
        int previous = inventory.getSelectedSlot();
        original.call(inventory, slot);
        if (previous != slot) {
            ServerPlayer player = weaver$player();
            GameEvents.post(new PlayerSwitchHotbarSlotPostEvent(player, previous, slot), player.level());
        }
    }

    @WrapOperation(method = "handlePlayerAction(Lnet/minecraft/network/protocol/game/ServerboundPlayerActionPacket;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;setItemInHand(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/ItemStack;)V",
                    ordinal = 0))
    private void weaver$swapOffHand(ServerPlayer player, InteractionHand hand, ItemStack stack, Operation<Void> original) {
        weaver$swap.remove();
        LivingSwapHandsEvent event = GameEvents.post(
                new LivingSwapHandsEvent(player, player.getItemInHand(InteractionHand.OFF_HAND), stack), player.level());
        weaver$swap.set(event);
        if (!GameEvents.canceled(event)) {
            original.call(player, hand, event.toOffHand());
        }
    }

    @WrapOperation(method = "handlePlayerAction(Lnet/minecraft/network/protocol/game/ServerboundPlayerActionPacket;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;setItemInHand(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/ItemStack;)V",
                    ordinal = 1))
    private void weaver$swapMainHand(ServerPlayer player, InteractionHand hand, ItemStack stack, Operation<Void> original) {
        LivingSwapHandsEvent event = weaver$swap.get();
        weaver$swap.remove();
        if (event == null) {
            original.call(player, hand, stack);
        } else if (!GameEvents.canceled(event)) {
            original.call(player, hand, event.toMainHand());
        }
    }

    @WrapOperation(method = "handleClientInformation(Lnet/minecraft/network/protocol/common/ServerboundClientInformationPacket;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;updateOptions(Lnet/minecraft/server/level/ClientInformation;)V"))
    private void weaver$clientInformation(ServerPlayer player, ClientInformation information, Operation<Void> original) {
        ClientInformation previous = player.clientInformation();
        original.call(player, information);
        GameEvents.post(new ClientInformationUpdatedEvent(player, previous, information), player.level());
    }
}
