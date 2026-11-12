package net.weaver.proof;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.weaver.api.event.EventBusSubscriber;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.event.SubscribeEvent;
import net.weaver.api.event.events.ClientTickEvent;
import net.weaver.api.event.events.CommandRegisterEvent;
import net.weaver.api.event.events.LevelLoadEvent;
import net.weaver.api.event.events.PlayerJoinEvent;
import net.weaver.api.event.events.PlayerTickEvent;
import net.weaver.api.event.events.RegisterEvent;
import net.weaver.api.event.events.RegistryFreezeEvent;
import net.weaver.api.event.events.ResourceReloadEvent;
import net.weaver.api.event.events.ScreenOpenEvent;
import net.weaver.api.event.events.ServerStartedEvent;
import net.weaver.api.event.events.ServerStartingEvent;
import net.weaver.api.event.events.ServerTickEvent;
import net.weaver.api.game.registry.GameRegistries;
import net.weaver.api.registry.RegistryView;

/** Auto-discovered event subscribers for the real-game proof. */
@EventBusSubscriber
public final class ProofEvents {

    private ProofEvents() {
    }

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        if ("minecraft:block".equals(event.registryId())
                || "minecraft:item".equals(event.registryId())) {
            ProofHooks.event("RegisterEvent " + event.registryId());
        }
        if (!"minecraft:item".equals(event.registryId())) {
            return;
        }
        RegistryView<Item> typedRegistry = event.registry(GameRegistries.ITEM);
        ProofHooks.check(typedRegistry != null
                        && GameRegistries.ITEM.id().equals(typedRegistry.id()),
                "RegisterEvent typed registry");
        event.<Item>register("weaverproof", "proof_event_item", () -> {
            ResourceKey<Item> key = ResourceKey.create(Registries.ITEM,
                    Identifier.fromNamespaceAndPath("weaverproof", "proof_event_item"));
            return new ProofItem(new Item.Properties().setId(key));
        });
        ResourceKey<Item> rawKey = ResourceKey.create(Registries.ITEM,
                Identifier.fromNamespaceAndPath("weaverproof", "proof_raw_item"));
        event.registerRaw("weaverproof:proof_raw_item",
                new ProofItem(new Item.Properties().setId(rawKey)));
    }

    @SubscribeEvent
    public static void onFreeze(RegistryFreezeEvent event) {
        if ("minecraft:block".equals(event.registryId())) {
            ProofHooks.eventOnce("RegistryFreezeEvent");
        }
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        ProofHooks.eventOnce("ServerStartingEvent " + event.server().getClass().getSimpleName());
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ProofHooks.eventOnce("ServerStartedEvent " + event.server().getClass().getSimpleName());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Pre event) {
        if (event.logicalSide() != LogicalSide.SERVER) {
            ProofHooks.event("ServerTickEvent BAD SIDE " + event.logicalSide());
            return;
        }
        ProofHooks.eventOnce("ServerTickEvent");
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        if (event.logicalSide() != LogicalSide.CLIENT) {
            ProofHooks.event("ClientTickEvent BAD SIDE " + event.logicalSide());
            return;
        }
        ProofHooks.eventOnce("ClientTickEvent");
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelLoadEvent event) {
        ProofHooks.eventOnce("LevelLoadEvent " + event.level().getClass().getSimpleName()
                + " side=" + event.logicalSide());
    }

    @SubscribeEvent
    public static void onCommandRegister(CommandRegisterEvent event) {
        ProofHooks.eventOnce("CommandRegisterEvent " + event.dispatcher().getClass().getSimpleName());
    }

    @SubscribeEvent
    public static void onResourceReload(ResourceReloadEvent event) {
        ProofHooks.eventOnce("ResourceReloadEvent " + event.phase());
    }

    @SubscribeEvent
    public static void onScreenOpen(ScreenOpenEvent event) {
        ProofHooks.eventOnce("ScreenOpenEvent " + event.screen().getClass().getSimpleName());
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerJoinEvent event) {
        if (event.logicalSide() != LogicalSide.SERVER) {
            ProofHooks.event("PlayerJoinEvent BAD SIDE " + event.logicalSide());
            return;
        }
        ProofHooks.event("PlayerJoinEvent");
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Pre event) {
        ProofHooks.eventOnce("PlayerTickEvent");
    }
}
