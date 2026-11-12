package net.weaver.nf;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;

/** A {@link DeferredHolder} for a block, usable wherever an {@link ItemLike} is (its block item). */
public class DeferredBlock<T extends Block> extends DeferredHolder<Block, T> implements ItemLike {
    protected DeferredBlock(ResourceKey<Block> key) {
        super(key);
    }

    public static <T extends Block> DeferredBlock<T> createBlock(Identifier id) {
        return createBlock(ResourceKey.create(Registries.BLOCK, id));
    }

    public static <T extends Block> DeferredBlock<T> createBlock(ResourceKey<Block> key) {
        return new DeferredBlock<>(key);
    }

    /** A stack of one of the block's item. */
    public ItemStack toStack() {
        return toStack(1);
    }

    public ItemStack toStack(int count) {
        return new ItemStack(get(), count);
    }

    @Override
    public Item asItem() {
        return get().asItem();
    }
}
