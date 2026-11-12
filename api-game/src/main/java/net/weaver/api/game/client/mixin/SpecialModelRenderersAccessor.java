package net.weaver.api.game.client.mixin;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Opens the special model renderer type table for {@code ModelLoaderRegistry.registerSpecialModelType}. */
@Mixin(SpecialModelRenderers.class)
public interface SpecialModelRenderersAccessor
{
    @Accessor("ID_MAPPER")
    static ExtraCodecs.LateBoundIdMapper<Identifier, MapCodec<? extends SpecialModelRenderer.Unbaked<?>>> weaver$idMapper()
    {
        throw new AssertionError("mixin not applied");
    }
}
