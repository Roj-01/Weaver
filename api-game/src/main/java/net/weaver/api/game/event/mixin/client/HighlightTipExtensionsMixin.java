package net.weaver.api.game.event.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.weaver.api.game.extension.ItemExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** {@link ItemExtension#getHighlightTip}: the name above the hotbar. */
@Mixin(Hud.class)
public abstract class HighlightTipExtensionsMixin {
    @WrapOperation(method = "extractSelectedItemName", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;getHoverName()Lnet/minecraft/network/chat/Component;"))
    private Component weaver$highlightTip(ItemStack stack, Operation<Component> original) {
        Component name = original.call(stack);
        return stack.getItem() instanceof ItemExtension extension ? extension.getHighlightTip(stack, name) : name;
    }
}
