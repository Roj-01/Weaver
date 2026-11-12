package net.weaver.compatsuite;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.weaver.api.ModContext;
import net.weaver.api.event.EventBus;
import net.weaver.api.event.EventFilter;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.event.events.LevelLoadEvent;
import net.weaver.api.event.events.RegisterEvent;
import net.weaver.api.event.events.RegistryFreezeEvent;
import net.weaver.api.event.events.ServerTickEvent;
import net.weaver.api.game.GameWeaverMod;
import net.weaver.api.game.registry.GameRegistries;
import net.weaver.api.registry.DeferredRegister;
import net.weaver.api.registry.RegistryEntry;
import net.weaver.api.registry.RegistryKey;
import net.weaver.api.registry.RegistryView;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One real Weaver mod that exercises the cross-feature compatibility surface:
 * the local event bus, filters, deferred and direct registry registration, reads
 * after the freeze, typed {@code api-game} events, and game hook callbacks.
 */
public final class CompatSuiteMod implements GameWeaverMod {

    private static final int EXPECTED_CHECKS = 20;
    private static final long FAILURE_DEADLINE_TICK = 100L;

    private static final AtomicBoolean FILTER_REJECTED = new AtomicBoolean();
    private static DeferredRegister<Item> deferredItems;
    private static RegistryEntry<Item> deferredItem;
    private static RegistryKey<Item> deferredKey;
    private static RegistryKey<Item> directKey;

    @Override
    public void onInitialize(ModContext context) {
        EventBus local = context.modEvents();
        CompatSuiteState.check(local != EventBus.MOD, "Local bus distinct");
        CompatSuiteState.check(("mod:" + context.id()).equals(local.name()), "Local bus name");

        local.addListener(CompatPingEvent.class, event -> {
            CompatSuiteState.check(event.getBus() == local, "Local bus event bus");
            CompatSuiteState.check("ping".equals(event.payload()), "Local bus listener");
            CompatSuiteState.check(true, "Local bus filter accepts");
        }, EventFilter.modId(context.id()));
        local.addListener(CompatPingEvent.class,
                event -> FILTER_REJECTED.set(true),
                EventFilter.modId("weavercompatsuite-other"));
        local.post(new CompatPingEvent("ping"));
        CompatSuiteState.check(!FILTER_REJECTED.get(), "Local bus filter rejects");

        deferredItems = context.deferredRegister(GameRegistries.ITEM);
        deferredKey = RegistryKey.of(context.id(), "compat_item");
        deferredItem = deferredItems.register(deferredKey, () -> new Item(
                new Item.Properties().setId(itemKey(context.id(), "compat_item"))));

        context.events().addListener(RegisterEvent.class, event -> {
            if ("minecraft:item".equals(event.registryId())) {
                CompatSuiteState.check(event.registry(GameRegistries.ITEM) != null,
                        "Register event item");
            }
        });
        context.events().addListener(RegistryFreezeEvent.class, event -> {
            if ("minecraft:item".equals(event.registryId())) {
                CompatSuiteState.check(event.registry(GameRegistries.ITEM).isFrozen(),
                        "Freeze event item");
            }
        });
        context.events().addListener(LevelLoadEvent.class, event ->
                CompatSuiteState.check(
                        event instanceof net.weaver.api.game.event.LevelLoadEvent typed
                                && typed.level() != null,
                        "Base listener receives typed level event"));
        context.events().addListener(net.weaver.api.game.event.LevelLoadEvent.class, event -> {
            Level level = event.level();
            CompatSuiteState.check(level != null, "Typed level event");
        });
        context.events().addListener(net.weaver.api.game.event.ServerStartedEvent.class, event -> {
            MinecraftServer server = event.server();
            CompatSuiteState.check(server != null && server.isRunning(),
                    "Typed server started event");
        });
        context.events().addListener(ServerTickEvent.Pre.class, event -> {
            boolean serverSide = event.logicalSide() == LogicalSide.SERVER;
            CompatSuiteState.check(serverSide && event.tick() >= 1, "Server tick event");
        });

        context.log("COMPAT SUITE INIT side=" + context.side());
    }

    @Override
    public void onRegister(ModContext context) {
        CompatSuiteState.check(deferredItem != null && deferredItem.isBound(),
                "Deferred entry bound");
        try {
            RegistryView<Item> items = context.registry(GameRegistries.ITEM);
            directKey = RegistryKey.of(context.id(), "compat_direct_item");
            items.register(directKey, new Item(new Item.Properties()
                    .setId(itemKey(context.id(), "compat_direct_item"))));
            CompatSuiteState.check(items.containsKey(directKey), "Direct item registered");
        } catch (Throwable failure) {
            CompatSuiteState.check(false, "Direct item registered");
            context.log("COMPAT SUITE DIRECT REGISTER FAILED reason=" + failure);
        }
    }

    @Override
    public void onLoadComplete(ModContext context) {
        try {
            RegistryView<Item> items = context.registry(GameRegistries.ITEM);
            CompatSuiteState.check(deferredItem != null && deferredItem.isBound()
                            && items.get(deferredKey) == deferredItem.get(),
                    "Deferred item readable");
            CompatSuiteState.check(items.isFrozen(), "Registry frozen");
            CompatSuiteState.check(deferredKey != null && directKey != null
                            && items.get(deferredKey) != null
                            && items.get(directKey) != null,
                    "Registry read-back");
        } catch (Throwable failure) {
            CompatSuiteState.check(false, "Deferred item readable");
            CompatSuiteState.check(false, "Registry frozen");
            CompatSuiteState.check(false, "Registry read-back");
            context.log("COMPAT SUITE REGISTRY READ FAILED reason=" + failure);
        }
    }

    @Override
    public void onLevelLoad(ModContext context, Level level) {
        CompatSuiteState.check(level != null && context.isLogicalServer(),
                "Level load callback");
    }

    @Override
    public void onServerStarted(ModContext context) {
        CompatSuiteState.check(context.isServer(), "Server started callback");
    }

    @Override
    public void onServerTick(ModContext context, long tick) {
        CompatSuiteState.check(tick >= 1 && context.isLogicalServer(), "Server tick callback");
        maybeFinish(context, tick);
    }

    private static void maybeFinish(ModContext context, long tick) {
        int passed = CompatSuiteState.passedCount();
        int failed = CompatSuiteState.failedCount();
        if (passed == EXPECTED_CHECKS && failed == 0) {
            CompatSuiteState.printResultOnce(true, context.side(),
                    "check_names=" + String.join("|", CompatSuiteState.passedNames()));
            return;
        }
        if (failed > 0) {
            CompatSuiteState.printResultOnce(false, context.side(),
                    "reason=failed passed=" + passed + " failed=" + failed
                            + " failed_names=" + String.join("|", CompatSuiteState.failedNames()));
            return;
        }
        if (tick >= FAILURE_DEADLINE_TICK) {
            CompatSuiteState.printResultOnce(false, context.side(),
                    "reason=observation-timeout passed=" + passed
                            + " expected=" + EXPECTED_CHECKS
                            + " passed_names=" + String.join("|", CompatSuiteState.passedNames()));
        }
    }

    private static ResourceKey<Item> itemKey(String namespace, String path) {
        return ResourceKey.create(Registries.ITEM,
                Identifier.fromNamespaceAndPath(namespace, path));
    }
}
