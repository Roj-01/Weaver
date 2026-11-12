package net.weaver.api.game.event.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.item.ItemStack;
import net.weaver.api.game.event.support.GameEvents;
import net.weaver.api.game.event.player.ItemSmeltedEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Taking a furnace, smoker, or blast furnace result. */
@Mixin(FurnaceResultSlot.class)
public abstract class FurnaceResultSlotEventsMixin {
    @Inject(method = "onTake(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("HEAD"))
    private void weaver$smelted(Player player, ItemStack stack, CallbackInfo callback) {
        GameEvents.post(new ItemSmeltedEvent(player, stack), player.level());
    }
}
