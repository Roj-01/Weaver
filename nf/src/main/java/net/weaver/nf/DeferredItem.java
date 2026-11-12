package net.weaver.nf;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/** A {@link DeferredHolder} for an item, usable wherever an {@link ItemLike} is. */
public class DeferredItem<T extends Item> extends DeferredHolder<Item, T> implements ItemLike {
    protected DeferredItem(ResourceKey<Item> key) {
        super(key);
    }

    public static <T extends Item> DeferredItem<T> createItem(Identifier id) {
        return createItem(ResourceKey.create(Registries.ITEM, id));
    }

    public static <T extends Item> DeferredItem<T> createItem(ResourceKey<Item> key) {
        return new DeferredItem<>(key);
    }

    /** A stack of one. */
    public ItemStack toStack() {
        return toStack(1);
    }

    public ItemStack toStack(int count) {
        return new ItemStack(get(), count);
    }

    @Override
    public Item asItem() {
        return get();
    }
}
