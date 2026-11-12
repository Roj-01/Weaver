package net.weaver.api.game.event.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.weaver.api.game.event.player.ItemStackedOnOtherEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A cursor stack clicked onto a slot, before item click overrides run. */
@Mixin(AbstractContainerMenu.class)
public abstract class ContainerMenuEventsMixin {
    @Inject(method = "tryItemClickBehaviourOverride(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/inventory/ClickAction;Lnet/minecraft/world/inventory/Slot;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z",
            at = @At("HEAD"), cancellable = true)
    private void weaver$stackedOn(Player player, ClickAction action, Slot slot, ItemStack clicked, ItemStack carried,
                                  CallbackInfoReturnable<Boolean> callback) {
        if (!GameEvents.hasListeners(ItemStackedOnOtherEvent.class)) {
            return;
        }
        if (GameEvents.canceled(GameEvents.post(
                new ItemStackedOnOtherEvent(player, carried, clicked, slot, action), player.level()))) {
            callback.setReturnValue(true);
        }
    }
}
