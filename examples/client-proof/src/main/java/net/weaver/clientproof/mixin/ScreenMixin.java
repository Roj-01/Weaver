package net.weaver.clientproof.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.weaver.core.client.CoreClientHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(Screen.class)
public abstract class ScreenMixin
{
    @Inject(method = "getTooltipFromItem", at = @At("RETURN"))
    private static void weaverclientproof$appendTooltip(Minecraft minecraft, ItemStack stack,
            CallbackInfoReturnable<List<Component>> callbackInfo)
    {
        @SuppressWarnings("unchecked")
        List<Object> lines = (List<Object>) (List<?>) callbackInfo.getReturnValue();
        CoreClientHooks.appendTooltip(minecraft, stack, lines);
    }
}
