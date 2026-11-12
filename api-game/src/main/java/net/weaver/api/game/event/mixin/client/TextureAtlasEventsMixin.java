package net.weaver.api.game.event.mixin.client;

import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.game.event.client.TextureAtlasStitchedEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Texture atlases after a stitch was uploaded. */
@Mixin(TextureAtlas.class)
public abstract class TextureAtlasEventsMixin {
    @Inject(method = "upload(Lnet/minecraft/client/renderer/texture/SpriteLoader$Preparations;)V", at = @At("RETURN"))
    private void weaver$stitched(SpriteLoader.Preparations preparations, CallbackInfo callback) {
        GameEvents.post(new TextureAtlasStitchedEvent((TextureAtlas) (Object) this), LogicalSide.CLIENT);
    }
}
