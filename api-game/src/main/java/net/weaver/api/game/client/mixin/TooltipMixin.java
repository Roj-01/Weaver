package net.weaver.api.game.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.weaver.api.client.ClientTooltipHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Appends Weaver tooltip provider lines to {@code Screen.getTooltipFromItem}.
 *
 * <p>The injection runs at {@code RETURN}. A {@code null} return list is
 * ignored, and an immutable list is tolerated by the core hook, which reports
 * the first append failure once. The mixin holds no game objects between calls.
 *
 * <p>The target was verified against Minecraft 26.3:
 * {@code Screen#getTooltipFromItem(Minecraft, ItemStack)Ljava/util/List;}.
 */
@Mixin(Screen.class)
public abstract class TooltipMixin
{
    private static final AtomicBoolean REPORTED_FAILURE = new AtomicBoolean();
    private static final AtomicBoolean REPORTED_PROOF = new AtomicBoolean();

    @Inject(method = "getTooltipFromItem", at = @At("RETURN"))
    private static void weaverAppendTooltip(Minecraft minecraft, ItemStack itemStack,
                                            CallbackInfoReturnable<List<Component>> callback)
    {
        List<Component> lines = callback.getReturnValue();
        if (lines == null)
        {
            return;
        }
        int before = lines.size();
        try
        {
            @SuppressWarnings({"unchecked", "rawtypes"})
            List<Object> target = (List<Object>) (List) lines;
            ClientTooltipHook.appendTooltip(minecraft, itemStack, target);
        }
        catch (Throwable failure)
        {
            if (REPORTED_FAILURE.compareAndSet(false, true))
            {
                System.err.println("[weaver] CLIENT TOOLTIP HOOK FAILED reason=" + failure);
            }
            return;
        }
        if (Boolean.getBoolean("weaver.proof.tooltip") && lines.size() > before
                && REPORTED_PROOF.compareAndSet(false, true))
        {
            System.out.println("[weaver] TOOLTIP ok");
        }
    }
}
