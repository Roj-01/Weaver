package com.example.firstmod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.weaver.api.ModContext;
import net.weaver.api.game.GameWeaverMod;
import net.weaver.api.game.creative.ModCreativeTabs;
import net.weaver.api.game.event.PlayerJoinEvent;
import net.weaver.api.game.gametest.GameTests;

/** The mod from docs/getting-started.md: an item, a creative tab, an event, and a game test. */
public final class FirstMod implements GameWeaverMod {
    public static final String MOD_ID = "firstmod";
    public static Item RUBY;

    static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize(ModContext context) {
        // 1. An item, registered straight into the game registry.
        ResourceKey<Item> rubyKey = ResourceKey.create(Registries.ITEM, id("ruby"));
        RUBY = Registry.register(BuiltInRegistries.ITEM, rubyKey, new Item(new Item.Properties().setId(rubyKey)));

        // 2. A creative tab; Weaver gives mod tabs their own page.
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, id("main"), ModCreativeTabs.builder()
                .title(Component.translatable("itemGroup.firstmod"))
                .icon(() -> new ItemStack(RUBY))
                .displayItems(ModCreativeTabs.items(items -> items.accept(RUBY)))
                .build());

        // 3. An event: greet players when they join a server.
        context.events().addListener(PlayerJoinEvent.class, event ->
                event.player().sendSystemMessage(Component.literal("Welcome! First Mod is installed.")));

        // 4. A game test, run headless by `gradlew weaverGameTest`.
        GameTests.register(id("ruby_is_registered"), helper -> {
            helper.assertTrue(BuiltInRegistries.ITEM.getValue(id("ruby")) == RUBY, "the ruby is in the item registry");
            helper.succeed();
        });

        context.log("First Mod loaded");
    }
}
