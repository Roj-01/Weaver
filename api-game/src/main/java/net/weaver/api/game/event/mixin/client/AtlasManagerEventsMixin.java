package net.weaver.api.game.event.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import java.util.List;
import net.minecraft.client.resources.model.sprite.AtlasManager;
import net.weaver.api.game.event.support.ClientRegistrations;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Mod texture atlases. */
@Mixin(AtlasManager.class)
public abstract class AtlasManagerEventsMixin {
    @ModifyExpressionValue(method = "<init>(Lnet/minecraft/client/renderer/texture/TextureManager;I)V", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/resources/model/sprite/AtlasManager;KNOWN_ATLASES:Ljava/util/List;"))
    private List<AtlasManager.AtlasConfig> weaver$atlases(List<AtlasManager.AtlasConfig> atlases) {
        return ClientRegistrations.atlases(atlases);
    }
}
