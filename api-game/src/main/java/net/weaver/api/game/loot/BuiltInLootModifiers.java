package net.weaver.api.game.loot;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.codec.RegistryCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;

/** The loot modifier kinds Weaver ships. */
final class BuiltInLootModifiers {
    private BuiltInLootModifiers() {
    }

    /** {@code weaver:add_items}: appends copies of {@code items}. */
    record AddItems(List<ItemStack> items) implements LootModifier {
        static final MapCodec<AddItems> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                ItemStack.CODEC.listOf().fieldOf("items").forGetter(AddItems::items)
        ).apply(instance, AddItems::new));

        @Override
        public void apply(List<ItemStack> loot, LootContext context) {
            for (ItemStack stack : items) {
                loot.add(stack.copy());
            }
        }
    }

    /** {@code weaver:remove_items}: drops every stack whose item is in {@code items} (ids or a tag). */
    record RemoveItems(HolderSet<Item> items) implements LootModifier {
        static final MapCodec<RemoveItems> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                RegistryCodecs.holderSet(Registries.ITEM).fieldOf("items").forGetter(RemoveItems::items)
        ).apply(instance, RemoveItems::new));

        @Override
        public void apply(List<ItemStack> loot, LootContext context) {
            loot.removeIf(stack -> stack.is(items));
        }
    }

    /** {@code weaver:replace_items}: swaps the item of matching stacks, keeping counts. */
    record ReplaceItems(HolderSet<Item> from, Holder<Item> to) implements LootModifier {
        static final MapCodec<ReplaceItems> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                RegistryCodecs.holderSet(Registries.ITEM).fieldOf("from").forGetter(ReplaceItems::from),
                Item.CODEC.fieldOf("to").forGetter(ReplaceItems::to)
        ).apply(instance, ReplaceItems::new));

        @Override
        public void apply(List<ItemStack> loot, LootContext context) {
            loot.replaceAll(stack -> stack.is(from) ? new ItemStack(to, stack.getCount()) : stack);
        }
    }
}
