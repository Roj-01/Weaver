package net.weaver.datagenproof;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.weaver.api.registry.DeferredRegister;
import net.weaver.api.registry.RegistryEntry;
import net.weaver.api.registry.WeaverRegistries;

/** Deferred block and item content matching the generated pack. */
public final class DatagenProofContent {

    private static final String NAMESPACE = "datagenproof";

    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(WeaverRegistries.BLOCK, NAMESPACE);
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(WeaverRegistries.ITEM, NAMESPACE);

    public static final RegistryEntry<Block> GEM_BLOCK = BLOCKS.register("gem_block",
            () -> new Block(BlockBehaviour.Properties.of()
                    .setId(blockKey("gem_block"))
                    .strength(4.0f)));

    public static final RegistryEntry<Item> RUBY = ITEMS.register("ruby",
            () -> new Item(new Item.Properties().setId(itemKey("ruby"))));

    public static final RegistryEntry<Item> GEM_BLOCK_ITEM = ITEMS.register("gem_block",
            () -> new BlockItem(GEM_BLOCK.get(), new Item.Properties()
                    .setId(itemKey("gem_block"))));

    private DatagenProofContent() {
    }

    /** Forces this class to initialize so the deferred registers are tracked. */
    public static void init() {
    }

    private static ResourceKey<Block> blockKey(String path) {
        return ResourceKey.create(Registries.BLOCK,
                Identifier.fromNamespaceAndPath(NAMESPACE, path));
    }

    private static ResourceKey<Item> itemKey(String path) {
        return ResourceKey.create(Registries.ITEM,
                Identifier.fromNamespaceAndPath(NAMESPACE, path));
    }
}
