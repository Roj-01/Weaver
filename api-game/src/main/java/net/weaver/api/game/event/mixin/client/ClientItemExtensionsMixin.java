package net.weaver.api.game.event.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.weaver.api.game.extension.ExtensionHooks;
import net.weaver.api.game.extension.ItemExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@link ItemExtension#usesBlockWhileSneaking}, {@link ItemExtension#canBeDropped}, and
 * {@link ItemExtension#shouldResetBlockBreaking} on the client.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class ClientItemExtensionsMixin {
    @WrapOperation(method = "performUseItemOn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;isSecondaryUseActive()Z"))
    private boolean weaver$sneakUse(LocalPlayer player, Operation<Boolean> original,
            @Local(argsOnly = true) BlockHitResult hit) {
        return original.call(player) && !ExtensionHooks.usesBlockWhileSneaking(player, player.level(), hit.getBlockPos());
    }

    @Inject(method = "dropItem", at = @At("HEAD"), cancellable = true)
    private void weaver$canDrop(LocalPlayer player, boolean all, CallbackInfo callback) {
        if (!ExtensionHooks.canDropSelected(player)) {
            callback.cancel();
        }
    }

    @WrapOperation(method = "sameDestroyTarget", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;isSameItemSameComponents(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean weaver$keepBreaking(net.minecraft.world.item.ItemStack held, net.minecraft.world.item.ItemStack mining,
            Operation<Boolean> original) {
        if (original.call(held, mining)) {
            return true;
        }
        return held.getItem() == mining.getItem() && held.getItem() instanceof ItemExtension extension
                && !extension.shouldResetBlockBreaking(mining, held);
    }
}
