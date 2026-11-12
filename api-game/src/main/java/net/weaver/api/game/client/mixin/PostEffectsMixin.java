package net.weaver.api.game.client.mixin;

import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import net.weaver.api.game.client.render.CustomRendering;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Enabled mod post effects join vanilla's requested list each frame (invalid ids are skipped by vanilla). */
@Mixin(GameRenderer.class)
public abstract class PostEffectsMixin
{
    @Inject(method = "update(Lnet/minecraft/client/DeltaTracker;)V", at = @At("TAIL"))
    private void weaver$postEffects(DeltaTracker delta, CallbackInfo callback)
    {
        if (CustomRendering.postEffects().isEmpty())
        {
            return;
        }
        List<Identifier> requested = ((GameRenderer) (Object) this).getRequestedPostEffects();
        for (Identifier effect : CustomRendering.postEffects())
        {
            if (!requested.contains(effect))
            {
                requested.add(effect);
            }
        }
    }
}
