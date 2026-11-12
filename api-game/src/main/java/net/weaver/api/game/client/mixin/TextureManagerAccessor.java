package net.weaver.api.game.client.mixin;

import java.util.Map;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Whether a texture is loaded, without loading it (getTexture loads and uploads). */
@Mixin(TextureManager.class)
public interface TextureManagerAccessor
{
    @Accessor("byPath")
    Map<Identifier, AbstractTexture> weaver$byPath();
}
