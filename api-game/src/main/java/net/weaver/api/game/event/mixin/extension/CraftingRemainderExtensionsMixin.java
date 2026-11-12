package net.weaver.api.game.event.mixin.extension;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.weaver.api.game.extension.ItemExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** {@link ItemExtension#getCraftingRemainder}: the remainder of each stack in the grid. */
@Mixin(CraftingRecipe.class)
public interface CraftingRemainderExtensionsMixin {
    @Inject(method = "defaultCraftingReminder", at = @At("RETURN"))
    private static void weaver$remainder(CraftingInput input, CallbackInfoReturnable<NonNullList<ItemStack>> callback) {
        NonNullList<ItemStack> remaining = callback.getReturnValue();
        for (int i = 0; i < remaining.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.getItem() instanceof ItemExtension extension) {
                remaining.set(i, extension.getCraftingRemainder(stack));
            }
        }
    }
}
