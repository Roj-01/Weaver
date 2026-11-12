package net.weaver.api.game.event.mixin.client;

import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.weaver.api.game.extension.ItemExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** {@link ItemExtension#causesReequipAnimation}: the item may keep the first-person hand still. */
@Mixin(FirstPersonHandsAndItems.class)
public abstract class ReequipExtensionsMixin {
    @Inject(method = "shouldInstantlyReplaceVisibleItem", at = @At("RETURN"), cancellable = true)
    private void weaver$reequip(ItemStack shown, ItemStack current, LocalPlayer player,
            CallbackInfoReturnable<Boolean> callback) {
        if (!callback.getReturnValueZ() && shown.getItem() == current.getItem()
                && current.getItem() instanceof ItemExtension extension
                && !extension.causesReequipAnimation(shown, current)) {
            callback.setReturnValue(true);
        }
    }
}
