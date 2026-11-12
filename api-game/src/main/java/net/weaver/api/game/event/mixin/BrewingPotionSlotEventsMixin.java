package net.weaver.api.game.event.mixin;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.weaver.api.game.event.player.PlayerBrewedPotionEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Taking a potion out of a brewing stand. Same condition as vanilla's advancement trigger. */
@Mixin(targets = "net.minecraft.world.inventory.BrewingStandMenu$PotionSlot")
public abstract class BrewingPotionSlotEventsMixin {
    @Inject(method = "onTake(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"))
    private void weaver$brewed(Player player, ItemStack carried, CallbackInfo callback) {
        if (player instanceof ServerPlayer && carried.get(DataComponents.POTION_CONTENTS) != null) {
            GameEvents.post(new PlayerBrewedPotionEvent(player, carried), player.level());
        }
    }
}
