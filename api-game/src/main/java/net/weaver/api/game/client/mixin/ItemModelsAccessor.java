package net.weaver.api.game.client.mixin;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModels;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Opens the item model type table for {@code ModelLoaderRegistry.registerItemModelType}. */
@Mixin(ItemModels.class)
public interface ItemModelsAccessor
{
    @Accessor("ID_MAPPER")
    static ExtraCodecs.LateBoundIdMapper<Identifier, MapCodec<? extends ItemModel.Unbaked>> weaver$idMapper()
    {
        throw new AssertionError("mixin not applied");
    }
}
