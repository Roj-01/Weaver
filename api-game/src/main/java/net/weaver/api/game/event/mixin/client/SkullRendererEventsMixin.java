package net.weaver.api.game.event.mixin.client;

import java.util.Map;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.object.skull.SkullModelBase;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.SkullBlock;
import net.weaver.api.game.event.support.ClientRegistrations;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Mod skull models and skins. */
@Mixin(SkullBlockRenderer.class)
public abstract class SkullRendererEventsMixin {
    @Shadow
    @Final
    private static Map<SkullBlock.Type, Identifier> SKIN_BY_TYPE;

    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void weaver$skulls(CallbackInfo callback) {
        ClientRegistrations.postSkulls(SKIN_BY_TYPE);
    }

    @Inject(method = "createModel(Lnet/minecraft/client/model/geom/EntityModelSet;Lnet/minecraft/world/level/block/SkullBlock$Type;)Lnet/minecraft/client/model/object/skull/SkullModelBase;",
            at = @At("HEAD"), cancellable = true)
    private static void weaver$model(EntityModelSet models, SkullBlock.Type type, CallbackInfoReturnable<SkullModelBase> callback) {
        SkullModelBase model = ClientRegistrations.skullModel(models, type);
        if (model != null) {
            callback.setReturnValue(model);
        }
    }
}
