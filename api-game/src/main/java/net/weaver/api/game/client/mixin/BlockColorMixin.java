package net.weaver.api.game.client.mixin;

import net.minecraft.client.color.block.BlockColors;
import net.weaver.api.game.client.color.BlockColorRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Applies queued Weaver block tint sources to the default color table.
 *
 * <p>Target verified against Minecraft 26.3:
 * {@code BlockColors#createDefault()LBlockColors;} at {@code RETURN}. The
 * injection runs after vanilla has registered every built-in source, then
 * applies the Weaver entries to the returned instance. Registrations made
 * before the client exists are therefore queued safely and applied when the
 * game builds its color table.
 *
 * <p>Item colors are data-driven in 26.3 and are intentionally not handled by
 * this mixin.
 */
@Mixin(BlockColors.class)
public abstract class BlockColorMixin
{
    private static final AtomicBoolean REPORTED_FAILURE = new AtomicBoolean();

    @Inject(method = "createDefault", at = @At("RETURN"), require = 1)
    private static void weaverApplyBlockColors(CallbackInfoReturnable<BlockColors> callback)
    {
        BlockColors colors = callback.getReturnValue();
        if (colors == null)
        {
            return;
        }
        try
        {
            BlockColorRegistry.apply(colors);
        }
        catch (Throwable failure)
        {
            if (REPORTED_FAILURE.compareAndSet(false, true))
            {
                System.err.println("[weaver] BLOCK COLOR HOOK FAILED reason=" + failure);
            }
        }
    }
}
