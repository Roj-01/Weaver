package net.weaver.api.game.event.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import net.weaver.api.game.event.support.GameEvents;
import net.weaver.api.game.event.player.ItemCraftedEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Taking a crafting result. */
@Mixin(ResultSlot.class)
public abstract class ResultSlotEventsMixin {
    @Shadow
    @Final
    private CraftingContainer craftSlots;

    @Inject(method = "onTake(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("HEAD"))
    private void weaver$crafted(Player player, ItemStack stack, CallbackInfo callback) {
        GameEvents.post(new ItemCraftedEvent(player, stack, craftSlots), player.level());
    }
}
