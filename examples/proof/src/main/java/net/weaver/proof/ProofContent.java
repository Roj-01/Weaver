package net.weaver.proof;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.weaver.api.ModContext;
import net.weaver.api.registry.DeferredRegister;
import net.weaver.api.registry.RegistryEntry;
import net.weaver.api.registry.RegistryKey;
import net.weaver.api.registry.RegistryManager;
import net.weaver.api.registry.RegistryView;
import net.weaver.api.registry.TagKey;
import net.weaver.api.registry.TagView;
import net.weaver.api.registry.WeaverRegistries;
import net.weaver.api.game.registry.GameRegistries;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

/** Registers real content and checks the access transformer, Mixin, and data components. */
public final class ProofContent {

    private static Block block;
    private static Item item;
    private static EntityType<?> entityType;
    private static RegistryEntry<Item> deferredItem;

    private ProofContent() {
    }

    public static void defer(ModContext context) {
        DeferredRegister<Item> items = context.deferredRegister(WeaverRegistries.ITEM);
        deferredItem = items.register("proof_deferred_item", () -> {
            Identifier id = Identifier.fromNamespaceAndPath("weaverproof", "proof_deferred_item");
            ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
            return new ProofItem(new Item.Properties().setId(key));
        });
        context.log("PROOF DEFERRED item=" + deferredItem.id());
    }

    public static void register(ModContext context) {
        GooFluid.register();
        ExtensionsProof.registerContent();
        Identifier blockId = Identifier.fromNamespaceAndPath("weaverproof", "proof_block");
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, blockId);
        ProofBlock proofBlock = new ProofBlock(BlockBehaviour.Properties.of()
                .setId(blockKey)
                .strength(1.0f));
        Registry.register(BuiltInRegistries.BLOCK, blockKey, proofBlock);

        Identifier itemId = Identifier.fromNamespaceAndPath("weaverproof", "proof_item");
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, itemId);
        ProofItem proofItem = new ProofItem(new Item.Properties().setId(itemKey));
        Registry.register(BuiltInRegistries.ITEM, itemKey, proofItem);

        ResourceKey<Item> blockItemKey = ResourceKey.create(Registries.ITEM, blockId);
        BlockItem blockItem = new BlockItem(proofBlock, new Item.Properties().setId(blockItemKey));
        Registry.register(BuiltInRegistries.ITEM, blockItemKey, blockItem);

        Identifier entityId = Identifier.fromNamespaceAndPath("weaverproof", "proof_entity");
        ResourceKey<EntityType<?>> entityKey = ResourceKey.create(Registries.ENTITY_TYPE, entityId);
        EntityType<ProofEntity> proofEntity = EntityType.Builder
                .of(ProofEntity::new, MobCategory.MISC)
                .sized(0.5f, 0.5f)
                .build(entityKey);
        Registry.register(BuiltInRegistries.ENTITY_TYPE, entityKey, proofEntity);

        RegistryView<Item> items = context.registry(WeaverRegistries.ITEM);
        RegistryKey<Item> viewItemKey = RegistryKey.of("weaverproof", "proof_view_item");
        ResourceKey<Item> viewItemGameKey = ResourceKey.create(Registries.ITEM,
                Identifier.fromNamespaceAndPath(viewItemKey.namespace(), viewItemKey.path()));
        RegistryEntry<Item> viewItem = items.register(viewItemKey,
                new ProofItem(new Item.Properties().setId(viewItemGameKey)));

        Identifier tabId = Identifier.fromNamespaceAndPath("weaverproof", "proof_tab");
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, tabId,
                net.weaver.api.game.creative.ModCreativeTabs.builder()
                        .title(Component.translatable("itemGroup.weaverproof"))
                        .icon(ProofContent::tabIcon)
                        .displayItems(net.weaver.api.game.creative.ModCreativeTabs.items(ProofContent::tabItems))
                        .build());

        block = proofBlock;
        item = proofItem;
        entityType = proofEntity;
        context.log("PROOF CONTENT ok=true block=" + blockId + " item=" + itemId
                + " entity=" + entityId + " view=" + viewItem.id());
    }

    private static ItemStack tabIcon() {
        return new ItemStack(item);
    }

    private static void tabItems(net.weaver.api.game.creative.CreativeTabItems items) {
        items.accept(item);
    }

    public static void check(ModContext context) {
        try {
            Field count = ItemStack.class.getDeclaredField("count");
            require(Modifier.isPublic(count.getModifiers()), "AT ItemStack.count public");

            Field holder = Block.class.getDeclaredField("builtInRegistryHolder");
            require(Modifier.isPublic(holder.getModifiers()),
                    "AT Block.builtInRegistryHolder public");
            require(!Modifier.isFinal(holder.getModifiers()),
                    "AT Block.builtInRegistryHolder not final");

            ItemStack stack = new ItemStack(item);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal("Weaver Proof"));
            Component name = stack.get(DataComponents.CUSTOM_NAME);
            require(name != null && name.getString().contains("Weaver Proof"),
                    "ItemStack DataComponents.CUSTOM_NAME");
            require(stack.getItem() == item, "ItemStack.getItem");

            require(deferredItem != null && deferredItem.isPresent(),
                    "DeferredRegister item bound");
            require(deferredItem.get() instanceof ProofItem, "DeferredRegister item type");
            require(deferredItem.holder() != null, "DeferredRegister item holder");
            RegistryView<Item> itemRegistry = context.registry(WeaverRegistries.ITEM);
            require(itemRegistry.isFrozen(), "Registry frozen at load complete");
            require(itemRegistry.get("weaverproof:proof_deferred_item") == deferredItem.get(),
                    "Registry lookup matches deferred entry");
            require(itemRegistry.get("weaverproof:proof_item") == item,
                    "Registry lookup matches direct entry");

            require(itemRegistry.size() > 0, "Registry size > 0");
            require(itemRegistry.keySet().contains(RegistryKey.parse("weaverproof:proof_item")),
                    "Registry keySet contains weaverproof:proof_item");
            require(itemRegistry.values().contains(item),
                    "Registry values contains weaverproof:proof_item");
            require(itemRegistry.entries().stream()
                            .anyMatch(entry -> "weaverproof:proof_item".equals(entry.id())),
                    "Registry entries contain weaverproof:proof_item");
            require(itemRegistry.getEntry("weaverproof:proof_item").isPresent(),
                    "Registry getEntry weaverproof:proof_item present");
            require(itemRegistry.containsKey("weaverproof:proof_item"),
                    "Registry containsKey weaverproof:proof_item");
            require(itemRegistry.get("weaverproof:proof_view_item") != null,
                    "Registry get weaverproof:proof_view_item");
            require(itemRegistry.get("weaverproof:proof_event_item") != null,
                    "Registry get weaverproof:proof_event_item");
            require(itemRegistry.get("weaverproof:proof_raw_item") != null,
                    "Registry get weaverproof:proof_raw_item");

            TagKey<Item> logsKey = TagKey.of(WeaverRegistries.ITEM, "minecraft:logs");
            TagView<Item> logs = itemRegistry.getTag(logsKey);
            require(logs.key().equals(logsKey), "Tag key minecraft:logs");
            require(logs.size() > 0, "Tag size > 0");
            require(!logs.values().isEmpty(), "Tag values not empty");
            require(!logs.contents().isEmpty(), "Tag contents not empty");
            require(!logs.isEmpty(), "Registry tag minecraft:logs loaded");
            RegistryView<?> typedItems = context.registry(GameRegistries.ITEM);
            require(GameRegistries.ITEM.id().equals(typedItems.id())
                            && WeaverRegistries.ITEM.id().equals(typedItems.id()),
                    "GameRegistries ITEM");
            checkRegistryConstants(context);

            require(ProofHooks.hasEventContaining("RegisterEvent minecraft:item"),
                    "RegisterEvent item");
            require(ProofHooks.hasEventContaining("RegistryFreezeEvent"),
                    "RegistryFreezeEvent");
            require(ProofHooks.hasEventContaining("ServerTickEvent"),
                    "ServerTickEvent");
            require(ProofHooks.hasEventContaining("LevelLoadEvent"),
                    "LevelLoadEvent");
            require(ProofHooks.hasEventContaining("CommandRegisterEvent"),
                    "CommandRegisterEvent");
            require(ProofHooks.hasEventContaining("ModLifecycle REGISTER"),
                    "ModLifecycleEvent");
            require(!ProofHooks.hasEventContaining(" BAD SIDE "),
                    "No event logical side mismatch");
            require(ProofHooks.hasEvent("LogicalServerSubscriber"),
                    "Logical SERVER subscriber loaded via logicalSide");
            require(ProofHooks.hasEventContaining("ModHook LogicalServer context=SERVER"),
                    "Server callback logical side");
            if ("client".equals(context.side())) {
                require(ProofHooks.hasEventContaining("ClientTickEvent"),
                        "ClientTickEvent");
                require(ProofHooks.hasEventContaining("ModHook LogicalClient context=CLIENT"),
                        "Client callback logical side");
                require(ProofHooks.hasEventContaining("ModHook LogicalPlayer context=SERVER"),
                        "Player callback logical side");
            }

            block.getName();
            require(ProofHooks.hits("Block.getName") > 0, "Block.getName mixin");

            context.log("PROOF CONTENT SUMMARY block=" + block + " item=" + item
                    + " entity=" + entityType + " mixin=" + ProofHooks.hits("Block.getName"));
            ProofChecks.mainChecksComplete(context);
        } catch (Throwable failure) {
            context.log("PROOF RESULT ok=false side=" + context.side() + " reason=" + failure);
            throw new IllegalStateException("proof failed", failure);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("check failed: " + message);
        }
        System.out.println("PROOF CHECK ok=true " + message);
    }

    private static void checkRegistryConstants(ModContext context) {
        Set<String> ids = new HashSet<>(RegistryManager.registryIds());
        int checked = 0;
        int missing = 0;
        for (Field field : WeaverRegistries.class.getFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != RegistryKey.class) {
                continue;
            }
            try {
                RegistryKey<?> key = (RegistryKey<?>) field.get(null);
                checked++;
                if (!ids.contains(key.id())) {
                    missing++;
                    context.log("PROOF REGISTRY CONSTANT MISSING " + field.getName()
                            + "=" + key.id());
                }
            } catch (Throwable failure) {
                context.log("PROOF REGISTRY CONSTANT FAILED " + field.getName()
                        + " reason=" + failure);
            }
        }
        context.log("PROOF REGISTRY CONSTANTS checked=" + checked + " missing=" + missing);
        require(missing == 0, "WeaverRegistries constants resolve");
    }
}
