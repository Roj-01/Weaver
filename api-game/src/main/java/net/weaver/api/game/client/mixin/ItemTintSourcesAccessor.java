package net.weaver.api.game.client.mixin;

import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Opens the item tint source type table for {@code ModelLoaderRegistry}. */
@Mixin(net.minecraft.client.color.item.ItemTintSources.class)
public interface ItemTintSourcesAccessor
{
    @Accessor("ID_MAPPER")
    static ExtraCodecs.LateBoundIdMapper<Identifier, MapCodec<? extends net.minecraft.client.color.item.ItemTintSource>> weaver$idMapper()
    {
        throw new AssertionError("mixin not applied");
    }
}
