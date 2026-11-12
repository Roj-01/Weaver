package net.weaver.api.game.client.mixin;

import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Opens the conditional item model property table for {@code ModelLoaderRegistry}. */
@Mixin(net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperties.class)
public interface ConditionalPropertiesAccessor
{
    @Accessor("ID_MAPPER")
    static ExtraCodecs.LateBoundIdMapper<Identifier, MapCodec<? extends net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty>> weaver$idMapper()
    {
        throw new AssertionError("mixin not applied");
    }
}
