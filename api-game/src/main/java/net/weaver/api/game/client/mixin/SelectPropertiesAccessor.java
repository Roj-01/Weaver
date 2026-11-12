package net.weaver.api.game.client.mixin;

import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Opens the select item model property table for {@code ModelLoaderRegistry}. */
@Mixin(net.minecraft.client.renderer.item.properties.select.SelectItemModelProperties.class)
public interface SelectPropertiesAccessor
{
    @Accessor("ID_MAPPER")
    static ExtraCodecs.LateBoundIdMapper<Identifier, net.minecraft.client.renderer.item.properties.select.SelectItemModelProperty.Type<?, ?>> weaver$idMapper()
    {
        throw new AssertionError("mixin not applied");
    }
}
