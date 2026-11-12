package net.weaver.api.game.event.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.weaver.api.game.event.player.ItemSmithedEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Smithing table results. */
@Mixin(SmithingMenu.class)
public abstract class SmithingMenuEventsMixin {
    @Inject(method = "onTake(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"))
    private void weaver$smithed(Player player, ItemStack carried, CallbackInfo callback) {
        if (!carried.isEmpty()) {
            GameEvents.post(new ItemSmithedEvent(player, (SmithingMenu) (Object) this, carried), player.level());
        }
    }
}
