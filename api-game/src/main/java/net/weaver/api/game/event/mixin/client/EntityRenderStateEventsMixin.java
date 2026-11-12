package net.weaver.api.game.event.mixin.client;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.weaver.api.game.event.support.RenderStateModifiers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Render-state modifiers after vanilla extraction. */
@Mixin(EntityRenderer.class)
public abstract class EntityRenderStateEventsMixin {
    @Inject(method = "createRenderState(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;",
            at = @At("RETURN"))
    private void weaver$modifiers(Entity entity, float partialTick, CallbackInfoReturnable<EntityRenderState> callback) {
        RenderStateModifiers.apply(entity, callback.getReturnValue(), partialTick);
    }
}
