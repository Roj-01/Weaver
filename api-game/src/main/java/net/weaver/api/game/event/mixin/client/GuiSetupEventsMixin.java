package net.weaver.api.game.event.mixin.client;

import java.util.List;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.weaver.api.game.event.support.ClientRegistrations;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Mod picture-in-picture GUI renderers. */
@Mixin(GameRenderer.class)
public abstract class GuiSetupEventsMixin {
    @ModifyArg(method = "<init>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/render/GuiRenderer;<init>(Lnet/minecraft/client/renderer/state/gui/GuiRenderState;Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;Ljava/util/List;)V"),
            index = 2)
    private List<PictureInPictureRenderer<?>> weaver$pictureInPicture(List<PictureInPictureRenderer<?>> renderers) {
        return ClientRegistrations.pictureInPicture(renderers);
    }
}
