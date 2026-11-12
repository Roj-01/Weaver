package net.weaver.api.compat;

import net.weaver.api.WeaverApi;
import net.weaver.api.event.Event;
import net.weaver.api.event.EventBus;
import net.weaver.api.event.EventBusStats;
import net.weaver.api.event.EventBusSubscriber;
import net.weaver.api.event.EventFilter;
import net.weaver.api.event.EventPriority;
import net.weaver.api.event.EventResult;
import net.weaver.api.event.ListenerHandle;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.event.Side;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the public binary surface a mod compiled against Weaver 2.1 links
 * against. These assertions intentionally read descriptors through reflection
 * instead of compiling against the API, so a changed parameter, return type,
 * constant value, or removed member is reported here before it reaches a mod.
 */
class PublicApiBinaryCompatibilityTest {

    @Test
    void weaverApiVersionAndCompatibilityConstantsAreStable() throws Exception {
        assertPublicFinalClass(WeaverApi.class);

        assertPublicStaticField(WeaverApi.class, "VERSION", String.class, "2.1.0");
        assertPublicStaticField(WeaverApi.class, "API_MAJOR", int.class, 2);
        assertPublicStaticField(WeaverApi.class, "API_MINOR", int.class, 1);
        assertPublicStaticField(WeaverApi.class, "MIN_SUPPORTED_API", String.class, "2.1.0");
        assertPublicStaticField(WeaverApi.class, "FEATURE_LEVEL", String.class, "2.2");
        assertPublicStaticField(WeaverApi.class, "GAME_VERSION", String.class, "26.3");
        assertPublicStaticField(WeaverApi.class, "HOOK_TARGET", String.class, "26.3");
        assertPublicStaticField(WeaverApi.class, "PATCH_LEVEL", String.class, "1");

        method(WeaverApi.class, "isCompatibleApi", boolean.class, String.class);
        method(WeaverApi.class, "isCompatibleGame", boolean.class, String.class);
        method(WeaverApi.class, "requireCompatibleApi", void.class, String.class);
        method(WeaverApi.class, "requireCompatibleGame", void.class, String.class);
        method(WeaverApi.class, "isGameApiCompatible", boolean.class, ClassLoader.class);
        method(WeaverApi.class, "stabilityPolicy", String.class);

        assertTrue(WeaverApi.isCompatibleApi("2.1.0"));
        assertTrue(WeaverApi.isCompatibleApi("v2.1.0+local"));
        assertFalse(WeaverApi.isCompatibleApi("3.0.0"));
        assertFalse(WeaverApi.isCompatibleApi("2.0.0"));
        assertTrue(WeaverApi.isCompatibleGame("26.3"));
        assertFalse(WeaverApi.isCompatibleGame("26.2"));
        assertTrue(WeaverApi.stabilityPolicy().contains("additive-first"));
    }

    @Test
    void eventBusPublicSurfaceIsStable() throws Exception {
        assertPublicFinalClass(EventBus.class);

        assertPublicStaticField(EventBus.class, "GAME", EventBus.class, null);
        assertPublicStaticField(EventBus.class, "MOD", EventBus.class, null);
        assertSame(EventBus.GAME, EventBus.global());
        assertSame(EventBus.MOD, EventBus.local("compat-probe").parent());
        assertEquals("mod:compat-probe", EventBus.local("compat-probe").name());

        constructor(EventBus.class, String.class);
        constructor(EventBus.class, String.class, EventBus.class);

        assertStaticMethod(EventBus.class, "global", EventBus.class);
        assertStaticMethod(EventBus.class, "local", EventBus.class, String.class);
        method(EventBus.class, "name", String.class);
        method(EventBus.class, "parent", EventBus.class);

        method(EventBus.class, "addListener", ListenerHandle.class, Class.class,
                net.weaver.api.event.EventListener.class);
        method(EventBus.class, "addListener", ListenerHandle.class, Class.class,
                net.weaver.api.event.EventListener.class, EventPriority.class);
        method(EventBus.class, "addListener", ListenerHandle.class, Class.class,
                net.weaver.api.event.EventListener.class, EventPriority.class, boolean.class);
        method(EventBus.class, "addListener", ListenerHandle.class, Class.class,
                net.weaver.api.event.EventListener.class, EventFilter.class);
        method(EventBus.class, "addListener", ListenerHandle.class, Class.class, Object.class,
                net.weaver.api.event.EventListener.class, EventPriority.class, boolean.class);
        method(EventBus.class, "addListener", ListenerHandle.class, Class.class, Object.class,
                net.weaver.api.event.EventListener.class, EventPriority.class, boolean.class,
                EventFilter.class);

        method(EventBus.class, "register", List.class, Object.class);
        method(EventBus.class, "register", List.class, Object.class, EventFilter.class);
        method(EventBus.class, "register", List.class, Class.class);
        method(EventBus.class, "register", List.class, Class.class, EventFilter.class);
        method(EventBus.class, "unregister", boolean.class, ListenerHandle.class);
        method(EventBus.class, "unregister", int.class, Object.class);
        method(EventBus.class, "post", boolean.class, Event.class);
        method(EventBus.class, "postFast", boolean.class, Event.class);
        method(EventBus.class, "fire", Event.class, Event.class);
        method(EventBus.class, "hasListeners", boolean.class, Class.class);
        method(EventBus.class, "listenerCount", int.class);
        method(EventBus.class, "setExceptionHandler", EventBus.class,
                net.weaver.api.event.EventExceptionHandler.class);
        method(EventBus.class, "exceptionHandler",
                net.weaver.api.event.EventExceptionHandler.class);
        method(EventBus.class, "stats", EventBusStats.class);
        method(EventBus.class, "resetStats", void.class);

        EventBus bus = new EventBus("compat-local");
        assertEquals(0, bus.listenerCount());
        assertTrue(bus.post(new Event()));
    }

    @Test
    void eventBaseTypeSurfaceIsStable() throws Exception {
        assertPublicClass(Event.class);
        assertFalse(Modifier.isFinal(Event.class.getModifiers()),
                "Event must stay extensible for 2.x event subclasses");

        constructor(Event.class);
        constructor(Event.class, boolean.class);
        constructor(Event.class, boolean.class, boolean.class);

        method(Event.class, "isCancelable", boolean.class);
        method(Event.class, "isCanceled", boolean.class);
        method(Event.class, "setCanceled", void.class, boolean.class);
        method(Event.class, "hasResult", boolean.class);
        method(Event.class, "getResult", EventResult.class);
        method(Event.class, "setResult", void.class, EventResult.class);
        method(Event.class, "logicalSide", LogicalSide.class);
        method(Event.class, "setLogicalSide", Event.class, LogicalSide.class);
        method(Event.class, "getBus", EventBus.class);
        method(Event.class, "setBus", void.class, EventBus.class);
        method(Event.class, "getName", String.class);

        Event plain = new Event();
        assertFalse(plain.isCancelable());
        assertTrue(plain.setLogicalSide(LogicalSide.SERVER) == plain);
        assertEquals(LogicalSide.SERVER, plain.logicalSide());
        plain.setLogicalSide(null);
        assertEquals(LogicalSide.BOTH, plain.logicalSide());

        Event cancelable = new Event(true);
        cancelable.setCanceled(true);
        assertTrue(cancelable.isCanceled());

        Event withResult = new Event(false, true);
        withResult.setResult(EventResult.DENY);
        assertEquals(EventResult.DENY, withResult.getResult());
    }

    @Test
    void enumOrdinalsAndSideHelpersAreStable() {
        assertEquals(List.of("HIGHEST", "HIGH", "NORMAL", "LOW", "LOWEST", "MONITOR"),
                names(EventPriority.class));
        assertEquals(0, EventPriority.HIGHEST.ordinal());
        assertEquals(5, EventPriority.MONITOR.ordinal());

        assertEquals(List.of("DENY", "DEFAULT", "ALLOW"), names(EventResult.class));
        assertEquals(List.of("CLIENT", "SERVER", "BOTH"), names(Side.class));
        assertEquals(List.of("CLIENT", "SERVER", "BOTH"), names(LogicalSide.class));

        assertTrue(Side.BOTH.isClient());
        assertTrue(Side.BOTH.isServer());
        assertTrue(Side.CLIENT.matches(Side.BOTH));
        assertFalse(Side.CLIENT.matches(Side.SERVER));
        assertSame(LogicalSide.SERVER, LogicalSide.from(Side.SERVER));
        assertSame(LogicalSide.BOTH, LogicalSide.from(null));
    }

    @Test
    void eventFilterFactorySurfaceIsStable() throws Exception {
        assertPublicInterface(EventFilter.class);
        assertTrue(EventFilter.class.isAnnotationPresent(FunctionalInterface.class));

        assertPublicStaticField(EventFilter.class, "ALL", EventFilter.class, null);
        method(EventFilter.class, "accept", boolean.class, Event.class);
        assertStaticMethod(EventFilter.class, "all", EventFilter.class);
        assertStaticMethod(EventFilter.class, "logicalSide", EventFilter.class,
                LogicalSide.class);
        assertStaticMethod(EventFilter.class, "modId", EventFilter.class, String.class);
        assertStaticMethod(EventFilter.class, "and", EventFilter.class, EventFilter[].class);
        assertStaticMethod(EventFilter.class, "or", EventFilter.class, EventFilter[].class);

        assertTrue(EventFilter.all().accept(new Event()));
        Event serverEvent = new Event().setLogicalSide(LogicalSide.SERVER);
        assertTrue(EventFilter.logicalSide(LogicalSide.SERVER).accept(serverEvent));
        assertFalse(EventFilter.logicalSide(LogicalSide.CLIENT).accept(serverEvent));
    }

    @Test
    void eventBusStatsAndListenerHandleDescriptorsAreStable() throws Exception {
        assertPublicFinalClass(EventBusStats.class);
        method(EventBusStats.class, "posts", long.class);
        method(EventBusStats.class, "listenerInvocations", long.class);
        method(EventBusStats.class, "canceledPosts", long.class);
        method(EventBusStats.class, "exceptions", long.class);
        method(EventBusStats.class, "totalNanos", long.class);
        method(EventBusStats.class, "maxNanos", long.class);
        method(EventBusStats.class, "toString", String.class);
        for (Constructor<?> constructor : EventBusStats.class.getDeclaredConstructors()) {
            assertFalse(Modifier.isPublic(constructor.getModifiers()),
                    "EventBusStats must stay constructible only through EventBus.stats()");
        }

        assertPublicFinalClass(ListenerHandle.class);
        constructor(ListenerHandle.class, EventBus.class, Class.class, Object.class,
                Runnable.class);
        method(ListenerHandle.class, "bus", EventBus.class);
        method(ListenerHandle.class, "eventType", Class.class);
        method(ListenerHandle.class, "owner", Object.class);
        method(ListenerHandle.class, "isActive", boolean.class);
        method(ListenerHandle.class, "unregister", void.class);

        EventBus bus = new EventBus("compat-handle");
        ListenerHandle handle = bus.addListener(Event.class, ignored -> {
        }, EventPriority.NORMAL);
        assertSame(bus, handle.bus());
        assertSame(Event.class, handle.eventType());
        assertTrue(handle.isActive());
        handle.unregister();
        assertFalse(handle.isActive());
        handle.unregister();
    }

    @Test
    void annotationContractsAreStable() throws Exception {
        assertTrue(EventBusSubscriber.class.isAnnotationPresent(Retention.class));
        assertEquals(RetentionPolicy.RUNTIME,
                EventBusSubscriber.class.getAnnotation(Retention.class).value());
        assertTrue(EventBusSubscriber.class.isAnnotationPresent(Target.class));
        assertEquals(Set.of(java.lang.annotation.ElementType.TYPE),
                Set.of(EventBusSubscriber.class.getAnnotation(Target.class).value()));

        method(EventBusSubscriber.class, "bus", EventBusSubscriber.Bus.class);
        method(EventBusSubscriber.class, "side", Side.class);
        method(EventBusSubscriber.class, "logicalSide", LogicalSide.class);
        method(EventBusSubscriber.class, "modId", String.class);
        assertEquals(EventBusSubscriber.Bus.GAME,
                EventBusSubscriber.class.getMethod("bus").getDefaultValue());
        assertEquals(Side.BOTH,
                EventBusSubscriber.class.getMethod("side").getDefaultValue());
        assertEquals(LogicalSide.BOTH,
                EventBusSubscriber.class.getMethod("logicalSide").getDefaultValue());
        assertEquals("", EventBusSubscriber.class.getMethod("modId").getDefaultValue());
        assertEquals(List.of("GAME", "MOD", "GLOBAL_MOD"),
                names(EventBusSubscriber.Bus.class));

        assertTrue(net.weaver.api.event.SubscribeEvent.class
                .isAnnotationPresent(Retention.class));
        assertEquals(RetentionPolicy.RUNTIME,
                net.weaver.api.event.SubscribeEvent.class.getAnnotation(Retention.class).value());
        assertEquals(EventPriority.NORMAL,
                net.weaver.api.event.SubscribeEvent.class.getMethod("priority").getDefaultValue());
        assertEquals(false,
                net.weaver.api.event.SubscribeEvent.class
                        .getMethod("receiveCanceled").getDefaultValue());
    }

    @Test
    void registryPublicDescriptorsAreStable() throws Exception {
        assertPublicFinalClass(net.weaver.api.registry.RegistryKey.class);
        assertTrue(Comparable.class.isAssignableFrom(net.weaver.api.registry.RegistryKey.class));
        assertStaticMethod(net.weaver.api.registry.RegistryKey.class, "of",
                net.weaver.api.registry.RegistryKey.class, String.class, String.class);
        assertStaticMethod(net.weaver.api.registry.RegistryKey.class, "parse",
                net.weaver.api.registry.RegistryKey.class, String.class);
        method(net.weaver.api.registry.RegistryKey.class, "namespace", String.class);
        method(net.weaver.api.registry.RegistryKey.class, "path", String.class);
        method(net.weaver.api.registry.RegistryKey.class, "id", String.class);
        method(net.weaver.api.registry.RegistryKey.class, "withNamespace",
                net.weaver.api.registry.RegistryKey.class, String.class);
        method(net.weaver.api.registry.RegistryKey.class, "withPath",
                net.weaver.api.registry.RegistryKey.class, String.class);
        method(net.weaver.api.registry.RegistryKey.class, "withPrefix",
                net.weaver.api.registry.RegistryKey.class, String.class);
        method(net.weaver.api.registry.RegistryKey.class, "withSuffix",
                net.weaver.api.registry.RegistryKey.class, String.class);
        method(net.weaver.api.registry.RegistryKey.class, "compareTo", int.class,
                net.weaver.api.registry.RegistryKey.class);

        assertPublicInterface(net.weaver.api.registry.RegistryView.class);
        method(net.weaver.api.registry.RegistryView.class, "registryKey",
                net.weaver.api.registry.RegistryKey.class);
        method(net.weaver.api.registry.RegistryView.class, "id", String.class);
        method(net.weaver.api.registry.RegistryView.class, "nativeRegistry", Object.class);
        method(net.weaver.api.registry.RegistryView.class, "isFrozen", boolean.class);
        method(net.weaver.api.registry.RegistryView.class, "containsKey", boolean.class,
                net.weaver.api.registry.RegistryKey.class);
        method(net.weaver.api.registry.RegistryView.class, "containsKey", boolean.class,
                String.class);
        method(net.weaver.api.registry.RegistryView.class, "get", Object.class,
                net.weaver.api.registry.RegistryKey.class);
        method(net.weaver.api.registry.RegistryView.class, "get", Object.class, String.class);
        method(net.weaver.api.registry.RegistryView.class, "getOptional", java.util.Optional.class,
                net.weaver.api.registry.RegistryKey.class);
        method(net.weaver.api.registry.RegistryView.class, "getEntry", java.util.Optional.class,
                net.weaver.api.registry.RegistryKey.class);
        method(net.weaver.api.registry.RegistryView.class, "getEntry", java.util.Optional.class,
                String.class);
        method(net.weaver.api.registry.RegistryView.class, "keySet", Set.class);
        method(net.weaver.api.registry.RegistryView.class, "values", java.util.Collection.class);
        method(net.weaver.api.registry.RegistryView.class, "entries", List.class);
        method(net.weaver.api.registry.RegistryView.class, "size", int.class);
        method(net.weaver.api.registry.RegistryView.class, "register",
                net.weaver.api.registry.RegistryEntry.class,
                net.weaver.api.registry.RegistryKey.class, Object.class);
        method(net.weaver.api.registry.RegistryView.class, "register",
                net.weaver.api.registry.RegistryEntry.class, String.class, Object.class);
        method(net.weaver.api.registry.RegistryView.class, "getTag",
                net.weaver.api.registry.TagView.class, net.weaver.api.registry.TagKey.class);

        assertPublicFinalClass(net.weaver.api.registry.RegistryEntry.class);
        method(net.weaver.api.registry.RegistryEntry.class, "key",
                net.weaver.api.registry.RegistryKey.class);
        method(net.weaver.api.registry.RegistryEntry.class, "id", String.class);
        method(net.weaver.api.registry.RegistryEntry.class, "get", Object.class);
        method(net.weaver.api.registry.RegistryEntry.class, "getOrNull", Object.class);
        method(net.weaver.api.registry.RegistryEntry.class, "optional", java.util.Optional.class);
        method(net.weaver.api.registry.RegistryEntry.class, "isPresent", boolean.class);
        method(net.weaver.api.registry.RegistryEntry.class, "isBound", boolean.class);
        method(net.weaver.api.registry.RegistryEntry.class, "holder", Object.class);
        method(net.weaver.api.registry.RegistryEntry.class, "holder", Object.class, Class.class);
        method(net.weaver.api.registry.RegistryEntry.class, "holderOptional",
                java.util.Optional.class, Class.class);
        method(net.weaver.api.registry.RegistryEntry.class, "ifPresent", void.class,
                Consumer.class);

        assertPublicFinalClass(net.weaver.api.registry.DeferredRegister.class);
        assertStaticMethod(net.weaver.api.registry.DeferredRegister.class, "create",
                net.weaver.api.registry.DeferredRegister.class,
                net.weaver.api.registry.RegistryKey.class, String.class);
        assertStaticMethod(net.weaver.api.registry.DeferredRegister.class, "create",
                net.weaver.api.registry.DeferredRegister.class,
                net.weaver.api.ModContext.class, net.weaver.api.registry.RegistryKey.class);
        method(net.weaver.api.registry.DeferredRegister.class, "registryKey",
                net.weaver.api.registry.RegistryKey.class);
        method(net.weaver.api.registry.DeferredRegister.class, "namespace", String.class);
        method(net.weaver.api.registry.DeferredRegister.class, "register",
                net.weaver.api.registry.RegistryEntry.class, String.class, Supplier.class);
        method(net.weaver.api.registry.DeferredRegister.class, "register",
                net.weaver.api.registry.RegistryEntry.class,
                net.weaver.api.registry.RegistryKey.class, Supplier.class);
        method(net.weaver.api.registry.DeferredRegister.class, "entries", List.class);
        method(net.weaver.api.registry.DeferredRegister.class, "size", int.class);

        assertPublicFinalClass(net.weaver.api.registry.WeaverRegistries.class);
        assertRegistryConstant("BLOCK", "minecraft:block");
        assertRegistryConstant("ITEM", "minecraft:item");
        assertRegistryConstant("ENTITY_TYPE", "minecraft:entity_type");
        assertRegistryConstant("BLOCK_ENTITY_TYPE", "minecraft:block_entity_type");
        assertRegistryConstant("FLUID", "minecraft:fluid");
        assertRegistryConstant("SOUND_EVENT", "minecraft:sound_event");
        assertRegistryConstant("MOB_EFFECT", "minecraft:mob_effect");
        assertRegistryConstant("POTION", "minecraft:potion");
        assertRegistryConstant("PARTICLE_TYPE", "minecraft:particle_type");
        assertRegistryConstant("MENU", "minecraft:menu");
        assertRegistryConstant("RECIPE_TYPE", "minecraft:recipe_type");
        assertRegistryConstant("RECIPE_SERIALIZER", "minecraft:recipe_serializer");
        assertRegistryConstant("ATTRIBUTE", "minecraft:attribute");
        assertRegistryConstant("CREATIVE_MODE_TAB", "minecraft:creative_mode_tab");
        assertRegistryConstant("COMMAND_ARGUMENT_TYPE", "minecraft:command_argument_type");
        assertRegistryConstant("DATA_COMPONENT_TYPE", "minecraft:data_component_type");
        assertRegistryConstant("STAT_TYPE", "minecraft:stat_type");
        assertRegistryConstant("TRIGGER_TYPE", "minecraft:trigger_type");
        assertRegistryConstant("LOOT_POOL_ENTRY_TYPE", "minecraft:loot_pool_entry_type");
        assertRegistryConstant("LOOT_FUNCTION_TYPE", "minecraft:loot_function_type");
        assertRegistryConstant("LOOT_CONDITION_TYPE", "minecraft:loot_condition_type");
        assertRegistryConstant("ENCHANTMENT_EFFECT_COMPONENT_TYPE",
                "minecraft:enchantment_effect_component_type");
        assertRegistryConstant("GAME_RULE", "minecraft:game_rule");
        assertRegistryConstant("TICKET_TYPE", "minecraft:ticket_type");
        assertRegistryConstant("STRUCTURE_TYPE", "minecraft:worldgen/structure_type");
        assertRegistryConstant("STRUCTURE_PIECE", "minecraft:worldgen/structure_piece");
        assertRegistryConstant("FEATURE_TYPE", "minecraft:worldgen/feature_type");
        assertRegistryConstant("CHUNK_STATUS", "minecraft:chunk_status");
        assertRegistryConstant("CARVER_TYPE", "minecraft:worldgen/carver_type");
        assertRegistryConstant("PLACEMENT_MODIFIER_TYPE",
                "minecraft:worldgen/placement_modifier_type");
        assertRegistryConstant("MATERIAL_CONDITION_TYPE",
                "minecraft:worldgen/material_condition_type");
        assertRegistryConstant("ENTITY_SUB_PREDICATE_TYPE",
                "minecraft:entity_sub_predicate_type");
        assertRegistryConstant("DATA_COMPONENT_PREDICATE_TYPE",
                "minecraft:data_component_predicate_type");
        assertRegistryConstant("MAP_DECORATION_TYPE", "minecraft:map_decoration_type");
        assertRegistryConstant("NUMBER_FORMAT_TYPE", "minecraft:number_format_type");
        assertRegistryConstant("DIALOG_TYPE", "minecraft:dialog_type");
        assertRegistryConstant("PERMISSION_TYPE", "minecraft:permission_type");
        assertRegistryConstant("ENVIRONMENT_ATTRIBUTE", "minecraft:environment_attribute");
        assertRegistryConstant("ATTRIBUTE_TYPE", "minecraft:attribute_type");
        assertStaticMethod(net.weaver.api.registry.WeaverRegistries.class, "of",
                net.weaver.api.registry.RegistryKey.class, String.class);
        assertStaticMethod(net.weaver.api.registry.WeaverRegistries.class, "of",
                net.weaver.api.registry.RegistryKey.class, String.class, String.class);
        assertStaticMethod(net.weaver.api.registry.WeaverRegistries.class, "availableIds",
                Set.class);
    }

    @Test
    void registryEventDescriptorsAreStable() throws Exception {
        assertPublicFinalClass(net.weaver.api.event.events.RegisterEvent.class);
        assertTrue(Event.class.isAssignableFrom(net.weaver.api.event.events.RegisterEvent.class));
        constructor(net.weaver.api.event.events.RegisterEvent.class,
                net.weaver.api.registry.RegistryKey.class);
        method(net.weaver.api.event.events.RegisterEvent.class, "registryKey",
                net.weaver.api.registry.RegistryKey.class);
        method(net.weaver.api.event.events.RegisterEvent.class, "registryId", String.class);
        method(net.weaver.api.event.events.RegisterEvent.class, "registry",
                net.weaver.api.registry.RegistryView.class);
        method(net.weaver.api.event.events.RegisterEvent.class, "registry",
                net.weaver.api.registry.RegistryView.class,
                net.weaver.api.registry.RegistryKey.class);
        method(net.weaver.api.event.events.RegisterEvent.class, "isFrozen", boolean.class);
        method(net.weaver.api.event.events.RegisterEvent.class, "register",
                net.weaver.api.registry.RegistryEntry.class,
                net.weaver.api.registry.RegistryKey.class, Supplier.class);
        method(net.weaver.api.event.events.RegisterEvent.class, "register",
                net.weaver.api.registry.RegistryEntry.class, String.class, String.class,
                Supplier.class);
        method(net.weaver.api.event.events.RegisterEvent.class, "register",
                net.weaver.api.registry.RegistryEntry.class, String.class, Supplier.class);
        method(net.weaver.api.event.events.RegisterEvent.class, "registerRaw", void.class,
                String.class, Object.class);

        assertPublicFinalClass(net.weaver.api.event.events.RegistryFreezeEvent.class);
        assertTrue(Event.class.isAssignableFrom(
                net.weaver.api.event.events.RegistryFreezeEvent.class));
        constructor(net.weaver.api.event.events.RegistryFreezeEvent.class,
                net.weaver.api.registry.RegistryKey.class);
        method(net.weaver.api.event.events.RegistryFreezeEvent.class, "registryKey",
                net.weaver.api.registry.RegistryKey.class);
        method(net.weaver.api.event.events.RegistryFreezeEvent.class, "registryId", String.class);
        method(net.weaver.api.event.events.RegistryFreezeEvent.class, "registry",
                net.weaver.api.registry.RegistryView.class);
        method(net.weaver.api.event.events.RegistryFreezeEvent.class, "registry",
                net.weaver.api.registry.RegistryView.class,
                net.weaver.api.registry.RegistryKey.class);
    }

    @Test
    void configPublicSurfaceIsPresent() throws Exception {
        assertPublicTypes(
                "net.weaver.api.config.ConfigBuilder",
                "net.weaver.api.config.ConfigData",
                "net.weaver.api.config.ConfigDocument",
                "net.weaver.api.config.ConfigException",
                "net.weaver.api.config.ConfigLoadResult",
                "net.weaver.api.config.ConfigManager",
                "net.weaver.api.config.ConfigMigration",
                "net.weaver.api.config.ConfigProblem",
                "net.weaver.api.config.ConfigSpec",
                "net.weaver.api.config.ConfigStore",
                "net.weaver.api.config.ConfigStoreException",
                "net.weaver.api.config.ConfigSyncDocument",
                "net.weaver.api.config.ConfigSyncPolicy",
                "net.weaver.api.config.ConfigSyncResult",
                "net.weaver.api.config.ConfigType",
                "net.weaver.api.config.ConfigValidationException",
                "net.weaver.api.config.ConfigValue");

        Class<?> manager = type("net.weaver.api.config.ConfigManager");
        assertPublicFinalClass(manager);
        assertStaticMethod(manager, "open", manager,
                type("net.weaver.api.config.ConfigSpec"),
                type("net.weaver.api.config.ConfigStore"), Consumer.class);
        assertStaticMethod(manager, "open", manager,
                type("net.weaver.api.config.ConfigSpec"),
                type("net.weaver.api.config.ConfigStore"), EventBus.class, Consumer.class);
        method(manager, "load", type("net.weaver.api.config.ConfigLoadResult"));
        method(manager, "save", void.class);
        method(manager, "value", type("net.weaver.api.config.ConfigValue"), String.class);
        method(manager, "problems", List.class);
        method(manager, "document", type("net.weaver.api.config.ConfigDocument"));
        method(manager, "syncResult", java.util.Optional.class);
        method(manager, "applySyncedValues", type("net.weaver.api.config.ConfigSyncResult"),
                java.util.Map.class, int.class);
        assertStaticMethod(manager, "loadedManagers", List.class);

        Class<?> builder = type("net.weaver.api.config.ConfigBuilder");
        assertStaticMethod(builder, "create", builder, String.class, Side.class);
        method(builder, "booleanValue", type("net.weaver.api.config.ConfigValue"),
                String.class, boolean.class, String.class);
        method(builder, "intValue", type("net.weaver.api.config.ConfigValue"),
                String.class, int.class, int.class, int.class, String.class);
        method(builder, "sync", builder, type("net.weaver.api.config.ConfigSyncPolicy"));
        method(builder, "build", type("net.weaver.api.config.ConfigSpec"));

        Class<?> spec = type("net.weaver.api.config.ConfigSpec");
        method(spec, "modId", String.class);
        method(spec, "side", Side.class);
        method(spec, "schemaVersion", int.class);
        method(spec, "values", List.class);
        method(spec, "value", type("net.weaver.api.config.ConfigValue"), String.class);
        method(spec, "syncPolicy", type("net.weaver.api.config.ConfigSyncPolicy"));

        assertEquals(List.of("NONE", "SERVER_TO_CLIENT"),
                names(type("net.weaver.api.config.ConfigSyncPolicy")));

        Class<?> value = type("net.weaver.api.config.ConfigValue");
        method(value, "key", String.class);
        method(value, "type", type("net.weaver.api.config.ConfigType"));
        method(value, "get", Object.class);
        method(value, "set", void.class, Object.class);

        assertEquals(11, type("net.weaver.api.config.ConfigType").getEnumConstants().length);
        assertEquals(List.of("BOOLEAN", "INT", "LONG", "DOUBLE", "STRING", "ENUM",
                        "STRING_LIST", "INT_LIST", "LONG_LIST", "DOUBLE_LIST", "ENUM_LIST"),
                names(type("net.weaver.api.config.ConfigType")));

        Class<?> syncDocument = type("net.weaver.api.config.ConfigSyncDocument");
        assertTrue(syncDocument.isRecord());
        assertEquals(List.of("modId", "side", "schemaVersion", "values"),
                componentNames(syncDocument));
        assertPublicStaticField(syncDocument, "CODEC",
                type("net.weaver.api.network.PayloadCodec"), null);
        assertPublicStaticField(syncDocument, "FORMAT_VERSION", int.class, 1);
        assertPublicStaticField(syncDocument, "MAX_KEYS", int.class, 1024);
        assertPublicStaticField(syncDocument, "MAX_KEY_CHARS", int.class, 256);
        assertPublicStaticField(syncDocument, "MAX_VALUE_CHARS", int.class, 32_768);
        assertPublicStaticField(syncDocument, "MAX_COLLECTION_ELEMENTS", int.class, 8192);
        assertPublicStaticField(syncDocument, "MAX_DEPTH", int.class, 8);
        assertStaticMethod(syncDocument, "from", syncDocument, String.class, Side.class,
                type("net.weaver.api.config.ConfigDocument"));
        method(syncDocument, "document", type("net.weaver.api.config.ConfigDocument"));

        Class<?> syncResult = type("net.weaver.api.config.ConfigSyncResult");
        assertTrue(syncResult.isRecord());
        assertEquals(List.of("schemaVersion", "appliedValues", "defaultedValues",
                "ignoredKeys", "problems"), componentNames(syncResult));

        Class<?> syncedEvent = type("net.weaver.api.event.events.config.ConfigSyncedEvent");
        assertPublicTypes("net.weaver.api.event.events.config.ConfigSyncedEvent");
        assertPublicFinalClass(syncedEvent);
        assertTrue(Event.class.isAssignableFrom(syncedEvent));
        constructor(syncedEvent, type("net.weaver.api.config.ConfigSpec"),
                type("net.weaver.api.config.ConfigSyncResult"));
        method(syncedEvent, "spec", type("net.weaver.api.config.ConfigSpec"));
        method(syncedEvent, "result", type("net.weaver.api.config.ConfigSyncResult"));
        method(syncedEvent, "modId", String.class);
        method(syncedEvent, "side", Side.class);
        method(syncedEvent, "schemaVersion", int.class);
        method(syncedEvent, "appliedValues", int.class);
        method(syncedEvent, "defaultedValues", int.class);
        method(syncedEvent, "ignoredKeys", int.class);
        method(syncedEvent, "problems", List.class);

        Class<?> store = type("net.weaver.api.config.ConfigStore");
        method(store, "side", Side.class);
        method(store, "location", java.nio.file.Path.class);
        method(store, "read", type("net.weaver.api.config.ConfigDocument"));
        method(store, "write", void.class, type("net.weaver.api.config.ConfigDocument"));
        method(store, "backup", java.nio.file.Path.class, String.class);
    }

    @Test
    void capabilityPublicSurfaceIsPresent() throws Exception {
        assertPublicTypes(
                "net.weaver.api.capability.AttachmentChange",
                "net.weaver.api.capability.AttachmentChangeListener",
                "net.weaver.api.capability.AttachmentRegistry",
                "net.weaver.api.capability.AttachmentStore",
                "net.weaver.api.capability.AttachmentType",
                "net.weaver.api.capability.Capability",
                "net.weaver.api.capability.CapabilityCodec",
                "net.weaver.api.capability.CapabilityException",
                "net.weaver.api.capability.CapabilityId",
                "net.weaver.api.capability.CapabilitySync",
                "net.weaver.api.capability.CapabilitySyncProvider",
                "net.weaver.api.capability.PersistentAttachmentStore",
                "net.weaver.api.capability.RuntimeAttachmentStore");

        Class<?> capability = type("net.weaver.api.capability.Capability");
        assertPublicInterface(capability);
        method(capability, "key", type("net.weaver.api.capability.CapabilityId"));
        method(capability, "id", String.class);
        method(capability, "namespace", String.class);
        method(capability, "path", String.class);
        method(capability, "valueType", Class.class);
        method(capability, "defaultValue", Object.class);
        method(capability, "codec", type("net.weaver.api.capability.CapabilityCodec"));
        method(capability, "maxEncodedBytes", int.class);
        method(capability, "copyOnItemCopy", boolean.class);
        method(capability, "sync", type("net.weaver.api.capability.CapabilitySync"));
        method(capability, "persistent", boolean.class);

        Class<?> registry = type("net.weaver.api.capability.AttachmentRegistry");
        assertPublicFinalClass(registry);
        constructor(registry);
        assertStaticMethod(registry, "global", registry);
        method(registry, "register", void.class, capability);
        method(registry, "registeredCapabilities", List.class);
        method(registry, "find", java.util.Optional.class, String.class);
        method(registry, "get", Object.class, Object.class, capability);
        method(registry, "find", java.util.Optional.class, Object.class, capability);
        method(registry, "set", Object.class, Object.class, capability, Object.class);
        method(registry, "remove", Object.class, Object.class, capability);
        method(registry, "has", boolean.class, Object.class, capability);
        method(registry, "supports", boolean.class, Object.class, capability);
        method(registry, "clear", void.class, Object.class);
        method(registry, "addChangeListener", void.class,
                type("net.weaver.api.capability.AttachmentChangeListener"));
        method(registry, "removeChangeListener", boolean.class,
                type("net.weaver.api.capability.AttachmentChangeListener"));
        method(registry, "changeListenerCount", int.class);
        method(registry, "diagnostics", List.class);
        method(registry, "setDiagnostics", void.class, Consumer.class);

        Class<?> attachmentType = type("net.weaver.api.capability.AttachmentType");
        assertPublicFinalClass(attachmentType);
        assertStaticMethod(attachmentType, "builder",
                type("net.weaver.api.capability.AttachmentType$Builder"), String.class,
                Class.class);
        assertStaticMethod(attachmentType, "runtime", attachmentType, String.class, Class.class);
        assertStaticMethod(attachmentType, "of", attachmentType, String.class, Class.class,
                type("net.weaver.api.capability.CapabilityCodec"));
        assertEquals(List.of("NONE", "ITEM_STACK", "RUNTIME"),
                names(type("net.weaver.api.capability.CapabilitySync")));

        Class<?> change = type("net.weaver.api.capability.AttachmentChange");
        assertTrue(change.isRecord());
        assertEquals(List.of("holder", "capability", "previousValue", "newValue", "kind"),
                componentNames(change));

        Class<?> persistent = type("net.weaver.api.capability.PersistentAttachmentStore");
        assertTrue(persistent.isInterface());
        method(persistent, "supportsHolder", boolean.class, Object.class);
        method(persistent, "saveTo", void.class, Object.class, Object.class);
        method(persistent, "loadFrom", void.class, Object.class, Object.class);

        Class<?> syncProvider = type("net.weaver.api.capability.CapabilitySyncProvider");
        assertTrue(syncProvider.isInterface());
        method(syncProvider, "holderKind", String.class, Object.class);
        method(syncProvider, "holderKey", String.class, Object.class);
        method(syncProvider, "resolve", Object.class, String.class, String.class);

        Class<?> id = type("net.weaver.api.capability.CapabilityId");
        assertTrue(id.isRecord());
        assertEquals(List.of("namespace", "path"), componentNames(id));
        assertStaticMethod(id, "of", id, String.class, String.class);
        assertStaticMethod(id, "parse", id, String.class);
        method(id, "id", String.class);

        Class<?> store = type("net.weaver.api.capability.AttachmentStore");
        method(store, "priority", int.class);
        method(store, "supports", boolean.class, Object.class, capability);
        method(store, "get", Object.class, Object.class, capability);
        method(store, "set", Object.class, Object.class, capability, Object.class);
        method(store, "remove", Object.class, Object.class, capability);
        method(store, "has", boolean.class, Object.class, capability);
        method(store, "clear", void.class, Object.class);
    }

    @Test
    void networkPublicSurfaceIsPresent() throws Exception {
        assertPublicTypes(
                "net.weaver.api.network.HandshakeDecision",
                "net.weaver.api.network.HandshakePolicy",
                "net.weaver.api.network.HandshakeRequest",
                "net.weaver.api.network.HandshakeResponse",
                "net.weaver.api.network.NetworkConnection",
                "net.weaver.api.network.NetworkException",
                "net.weaver.api.network.NetworkModInfo",
                "net.weaver.api.network.NetworkPhase",
                "net.weaver.api.network.NetworkPlayer",
                "net.weaver.api.network.NetworkProtocol",
                "net.weaver.api.network.NetworkRegistrationException",
                "net.weaver.api.network.NetworkRegistry",
                "net.weaver.api.network.NetworkSide",
                "net.weaver.api.network.PayloadBuffer",
                "net.weaver.api.network.PayloadCodec",
                "net.weaver.api.network.PayloadContext",
                "net.weaver.api.network.PayloadDirection",
                "net.weaver.api.network.PayloadHandler",
                "net.weaver.api.network.PayloadRegistration",
                "net.weaver.api.network.PayloadType",
                "net.weaver.api.network.UnknownChannelPolicy");

        Class<?> protocol = type("net.weaver.api.network.NetworkProtocol");
        assertPublicFinalClass(protocol);
        assertPublicStaticField(protocol, "NAME", String.class, "weaver-network");
        assertPublicStaticField(protocol, "VERSION", int.class, 1);
        assertPublicStaticField(protocol, "CHANNEL_VERSION", int.class, 1);
        assertPublicStaticField(protocol, "MAX_PAYLOAD_BYTES", int.class, 1_048_576);
        assertPublicStaticField(protocol, "MAX_FRAME_BYTES", int.class, 1_052_672);
        assertPublicStaticField(protocol, "MAX_QUEUE_DEPTH", int.class, 1024);
        assertPublicStaticField(protocol, "MAX_STRING_CHARS", int.class, 256);
        assertPublicStaticField(protocol, "MAX_CHANNEL_CHARS", int.class, 160);
        assertPublicStaticField(protocol, "MAX_MODS", int.class, 1024);
        assertPublicStaticField(protocol, "MAX_MOD_ID_CHARS", int.class, 128);
        assertPublicStaticField(protocol, "MAX_MOD_VERSION_CHARS", int.class, 64);
        assertPublicStaticField(protocol, "MAX_CHANNELS", int.class, 4096);
        assertPublicStaticField(protocol, "MAX_REASON_CHARS", int.class, 512);
        assertPublicStaticField(protocol, "MAX_PLAYER_NAME_CHARS", int.class, 64);

        Class<?> registry = type("net.weaver.api.network.NetworkRegistry");
        assertPublicFinalClass(registry);
        assertStaticMethod(registry, "global", registry);
        method(registry, "registerClientbound",
                type("net.weaver.api.network.PayloadRegistration"), String.class,
                type("net.weaver.api.network.PayloadType"),
                type("net.weaver.api.network.NetworkPhase"),
                type("net.weaver.api.network.PayloadHandler"));
        method(registry, "registerServerbound",
                type("net.weaver.api.network.PayloadRegistration"), String.class,
                type("net.weaver.api.network.PayloadType"),
                type("net.weaver.api.network.NetworkPhase"),
                type("net.weaver.api.network.PayloadHandler"));
        method(registry, "find", java.util.Optional.class,
                type("net.weaver.api.network.PayloadDirection"),
                type("net.weaver.api.network.NetworkPhase"), String.class);
        method(registry, "findAnyPhase", java.util.Optional.class,
                type("net.weaver.api.network.PayloadDirection"), String.class);
        method(registry, "registrations", List.class);
        method(registry, "registrations", List.class,
                type("net.weaver.api.network.PayloadDirection"));
        method(registry, "channels", List.class);
        method(registry, "size", int.class);
        method(registry, "clear", void.class);

        Class<?> buffer = type("net.weaver.api.network.PayloadBuffer");
        assertPublicFinalClass(buffer);
        assertStaticMethod(buffer, "writer", buffer);
        assertStaticMethod(buffer, "writer", buffer, int.class);
        assertStaticMethod(buffer, "reader", buffer, byte[].class);
        method(buffer, "writeBoolean", buffer, boolean.class);
        method(buffer, "writeVarInt", buffer, int.class);
        method(buffer, "writeString", buffer, String.class);
        method(buffer, "writeByteArray", buffer, byte[].class);
        method(buffer, "writeUuid", buffer, java.util.UUID.class);
        method(buffer, "readBoolean", boolean.class);
        method(buffer, "readVarInt", int.class);
        method(buffer, "readString", String.class);
        method(buffer, "readByteArray", byte[].class);
        method(buffer, "readUuid", java.util.UUID.class);
        method(buffer, "toByteArray", byte[].class);

        assertEquals(List.of("LOGIN", "CONFIGURATION", "PLAY"),
                names(type("net.weaver.api.network.NetworkPhase")));
        assertEquals(List.of("CLIENTBOUND", "SERVERBOUND"),
                names(type("net.weaver.api.network.PayloadDirection")));
        assertEquals(List.of("CLIENT", "SERVER"),
                names(type("net.weaver.api.network.NetworkSide")));

        Class<?> payloadType = type("net.weaver.api.network.PayloadType");
        assertStaticMethod(payloadType, "of", payloadType, String.class, String.class,
                type("net.weaver.api.network.PayloadCodec"));
        method(payloadType, "channel", String.class);
        method(payloadType, "modId", String.class);
        method(payloadType, "path", String.class);
        method(payloadType, "version", int.class);
        method(payloadType, "codec", type("net.weaver.api.network.PayloadCodec"));
    }

    @Test
    void commandPublicSurfaceIsPresent() throws Exception {
        assertPublicTypes(
                "net.weaver.api.command.CommandArgumentType",
                "net.weaver.api.command.CommandBackend",
                "net.weaver.api.command.CommandContext",
                "net.weaver.api.command.CommandException",
                "net.weaver.api.command.CommandExecutor",
                "net.weaver.api.command.CommandNodeSpec",
                "net.weaver.api.command.CommandRegistration",
                "net.weaver.api.command.CommandRegistrationException",
                "net.weaver.api.command.CommandRegistry",
                "net.weaver.api.command.CommandSource",
                "net.weaver.api.command.CommandSpec",
                "net.weaver.api.command.CommandSuggestionContext",
                "net.weaver.api.command.CommandSuggestionProvider",
                "net.weaver.api.command.CommandTreeSnapshot",
                "net.weaver.api.command.CommandUnavailableException",
                "net.weaver.api.command.PermissionNode",
                "net.weaver.api.command.PermissionPolicy",
                "net.weaver.api.command.PermissionRegistration",
                "net.weaver.api.command.PermissionResolver",
                "net.weaver.api.command.PermissionService",
                "net.weaver.api.command.PermissionStore");

        Class<?> registry = type("net.weaver.api.command.CommandRegistry");
        assertPublicFinalClass(registry);
        assertStaticMethod(registry, "install", void.class,
                type("net.weaver.api.command.CommandBackend"));
        assertStaticMethod(registry, "uninstall", void.class);
        assertStaticMethod(registry, "reset", void.class);
        assertStaticMethod(registry, "isAvailable", boolean.class);
        assertStaticMethod(registry, "backend",
                type("net.weaver.api.command.CommandBackend"));
        assertStaticMethod(registry, "register",
                type("net.weaver.api.command.CommandRegistration"),
                type("net.weaver.api.command.CommandSpec"));
        assertStaticMethod(registry, "dispatcher", Object.class);
        assertStaticMethod(registry, "commandNames", List.class);
        assertStaticMethod(registry, "treeSnapshot",
                type("net.weaver.api.command.CommandTreeSnapshot"));
        assertStaticMethod(registry, "recordTreeSync", void.class, Object.class, List.class,
                String.class);
        assertStaticMethod(registry, "resetTreeSnapshot", void.class);

        Class<?> spec = type("net.weaver.api.command.CommandSpec");
        assertPublicFinalClass(spec);
        assertStaticMethod(spec, "command", type("net.weaver.api.command.CommandSpec$Builder"),
                String.class);
        assertStaticMethod(spec, "literal", type("net.weaver.api.command.CommandSpec$Builder"),
                String.class);
        method(spec, "name", String.class);
        method(spec, "modId", String.class);
        method(spec, "aliases", List.class);
        method(spec, "executor", type("net.weaver.api.command.CommandExecutor"));
        method(spec, "suggestions", type("net.weaver.api.command.CommandSuggestionProvider"));
        method(spec, "children", List.class);

        Class<?> backend = type("net.weaver.api.command.CommandBackend");
        method(backend, "resendTree", void.class, Object.class, List.class, List.class);
        method(backend, "rebuildTree", void.class, Object.class, List.class);

        Class<?> resolver = type("net.weaver.api.command.PermissionResolver");
        method(resolver, "subjectKey", String.class, Object.class);

        Class<?> store = type("net.weaver.api.command.PermissionStore");
        method(store, "load", java.util.Map.class);
        method(store, "save", void.class, java.util.Map.class);

        Class<?> permissions = type("net.weaver.api.command.PermissionService");
        assertPublicFinalClass(permissions);
        assertStaticMethod(permissions, "install", void.class,
                type("net.weaver.api.command.PermissionResolver"));
        assertStaticMethod(permissions, "install", void.class,
                type("net.weaver.api.command.PermissionResolver"),
                type("net.weaver.api.command.PermissionStore"));
        assertStaticMethod(permissions, "hasStore", boolean.class);
        assertStaticMethod(permissions, "snapshot", java.util.Map.class);
        assertStaticMethod(permissions, "resolver",
                type("net.weaver.api.command.PermissionResolver"));
        assertStaticMethod(permissions, "reset", void.class);
        assertStaticMethod(permissions, "isAllowed", boolean.class, Object.class,
                type("net.weaver.api.command.PermissionNode"));
        assertStaticMethod(permissions, "allow", void.class, Object.class,
                type("net.weaver.api.command.PermissionNode"));
        assertStaticMethod(permissions, "deny", void.class, Object.class,
                type("net.weaver.api.command.PermissionNode"));
        assertStaticMethod(permissions, "clear", void.class, Object.class,
                type("net.weaver.api.command.PermissionNode"));
        assertStaticMethod(permissions, "clearAll", void.class, Object.class);
        assertStaticMethod(permissions, "opLevel", int.class, Object.class);
        assertStaticMethod(permissions, "hasExplicitDecision", boolean.class, Object.class,
                type("net.weaver.api.command.PermissionNode"));

        Class<?> node = type("net.weaver.api.command.PermissionNode");
        assertStaticMethod(node, "of", node, String.class, String.class);
        assertStaticMethod(node, "parse", node, String.class);
        assertStaticMethod(node, "wildcard", node, String.class);
        method(node, "namespace", String.class);
        method(node, "path", String.class);
        method(node, "parent", java.util.Optional.class);
        method(node, "ancestors", List.class);
        method(node, "isWildcard", boolean.class);

        assertEquals(List.of("STRING", "WORD", "GREEDY_STRING", "INTEGER", "LONG",
                        "DOUBLE", "FLOAT", "BOOLEAN", "CUSTOM"),
                names(type("net.weaver.api.command.CommandArgumentType$Kind")));
    }

    @Test
    void dataAndClientPublicSurfaceIsPresent() throws Exception {
        assertPublicTypes(
                "net.weaver.api.data.DataCodecs",
                "net.weaver.api.data.DataComponentKey",
                "net.weaver.api.data.DataComponentView");

        Class<?> codecs = type("net.weaver.api.data.DataCodecs");
        assertPublicFinalClass(codecs);
        assertStaticMethod(codecs, "utf8String",
                type("net.weaver.api.capability.CapabilityCodec"), int.class);
        assertStaticMethod(codecs, "bool",
                type("net.weaver.api.capability.CapabilityCodec"));
        assertStaticMethod(codecs, "varInt",
                type("net.weaver.api.capability.CapabilityCodec"));
        assertStaticMethod(codecs, "varLong",
                type("net.weaver.api.capability.CapabilityCodec"));
        assertStaticMethod(codecs, "bytes",
                type("net.weaver.api.capability.CapabilityCodec"), int.class);
        assertStaticMethod(codecs, "uuid",
                type("net.weaver.api.capability.CapabilityCodec"));

        Class<?> key = type("net.weaver.api.data.DataComponentKey");
        assertPublicFinalClass(key);
        assertStaticMethod(key, "builder",
                type("net.weaver.api.data.DataComponentKey$Builder"), String.class,
                Class.class);
        assertStaticMethod(key, "of", key, String.class, Class.class,
                type("net.weaver.api.capability.CapabilityCodec"), int.class);
        method(key, "attachmentType",
                type("net.weaver.api.capability.AttachmentType"));

        Class<?> view = type("net.weaver.api.data.DataComponentView");
        assertPublicInterface(view);
        assertStaticMethod(view, "of", view, Object.class);
        method(view, "get", Object.class, key);
        method(view, "find", java.util.Optional.class, key);
        method(view, "set", Object.class, key, Object.class);
        method(view, "remove", Object.class, key);
        method(view, "has", boolean.class, key);

        assertPublicTypes(
                "net.weaver.api.client.ClientBackend",
                "net.weaver.api.client.ClientException",
                "net.weaver.api.client.ClientRegistration",
                "net.weaver.api.client.ClientRegistry",
                "net.weaver.api.client.ClientThreadException",
                "net.weaver.api.client.ClientTickContext",
                "net.weaver.api.client.ClientTickListener",
                "net.weaver.api.client.ClientUnavailableException",
                "net.weaver.api.client.HudLayer",
                "net.weaver.api.client.HudRenderContext",
                "net.weaver.api.client.KeyMappingSpec",
                "net.weaver.api.client.ResourceReloadContext",
                "net.weaver.api.client.ResourceReloadListener",
                "net.weaver.api.client.ScreenFactory",
                "net.weaver.api.client.TooltipContext",
                "net.weaver.api.client.TooltipProvider");

        Class<?> clientRegistry = type("net.weaver.api.client.ClientRegistry");
        assertPublicFinalClass(clientRegistry);
        assertStaticMethod(clientRegistry, "install", void.class,
                type("net.weaver.api.client.ClientBackend"));
        assertStaticMethod(clientRegistry, "uninstall", void.class);
        assertStaticMethod(clientRegistry, "isAvailable", boolean.class);
        assertStaticMethod(clientRegistry, "backend",
                type("net.weaver.api.client.ClientBackend"));
        assertStaticMethod(clientRegistry, "registerKeyMapping",
                type("net.weaver.api.client.ClientRegistration"),
                type("net.weaver.api.client.KeyMappingSpec"), Runnable.class);
        assertStaticMethod(clientRegistry, "registerKeyMapping",
                type("net.weaver.api.client.ClientRegistration"),
                type("net.weaver.api.client.KeyMappingSpec"));
        assertStaticMethod(clientRegistry, "registerScreenFactory",
                type("net.weaver.api.client.ClientRegistration"),
                type("net.weaver.api.client.ScreenFactory"));
        assertStaticMethod(clientRegistry, "registerHudLayer",
                type("net.weaver.api.client.ClientRegistration"),
                type("net.weaver.api.client.HudLayer"));
        assertStaticMethod(clientRegistry, "registerTooltipProvider",
                type("net.weaver.api.client.ClientRegistration"),
                type("net.weaver.api.client.TooltipProvider"));
        assertStaticMethod(clientRegistry, "registerTickListener",
                type("net.weaver.api.client.ClientRegistration"),
                type("net.weaver.api.client.ClientTickListener"));
        assertStaticMethod(clientRegistry, "registerResourceReloadListener",
                type("net.weaver.api.client.ClientRegistration"),
                type("net.weaver.api.client.ResourceReloadListener"));
        assertStaticMethod(clientRegistry, "openScreen", void.class, String.class);
        assertStaticMethod(clientRegistry, "renderHud", void.class,
                type("net.weaver.api.client.HudRenderContext"));
        assertStaticMethod(clientRegistry, "tooltipLines", List.class,
                type("net.weaver.api.client.TooltipContext"));
        assertStaticMethod(clientRegistry, "registrationCount", int.class);
        assertStaticMethod(clientRegistry, "reset", void.class);

        Class<?> keyMapping = type("net.weaver.api.client.KeyMappingSpec");
        assertTrue(keyMapping.isRecord());
        assertStaticMethod(keyMapping, "key", keyMapping, String.class, String.class, int.class);
        assertStaticMethod(keyMapping, "builder",
                type("net.weaver.api.client.KeyMappingSpec$Builder"), String.class,
                String.class, int.class);
        method(keyMapping, "id", String.class);
        method(keyMapping, "modId", String.class);
        method(keyMapping, "translationKey", String.class);
        method(keyMapping, "categoryId", String.class);
        method(keyMapping, "keyCode", int.class);
        method(keyMapping, "mouse", boolean.class);
        method(keyMapping, "modifiers", int.class);
        method(keyMapping, "order", int.class);
        method(keyMapping, "screenFactoryId", String.class);

        Class<?> hudLayer = type("net.weaver.api.client.HudLayer");
        method(hudLayer, "id", String.class);
        method(hudLayer, "order", int.class);
        method(hudLayer, "render", void.class, type("net.weaver.api.client.HudRenderContext"));

        Class<?> tooltip = type("net.weaver.api.client.TooltipProvider");
        method(tooltip, "id", String.class);
        method(tooltip, "append", void.class,
                type("net.weaver.api.client.TooltipContext"), List.class);

        Class<?> screenFactory = type("net.weaver.api.client.ScreenFactory");
        method(screenFactory, "id", String.class);
        method(screenFactory, "createScreen", Object.class, Object.class, Object.class);

        assertEquals(List.of("START", "FINISHED", "FAILED"),
                names(type("net.weaver.api.client.ResourceReloadContext$Phase")));
    }

    @Test
    void hookAndGameEventFactoriesKeepTheirObjectBridges() throws Exception {
        Class<?> hookFactory = type("net.weaver.api.event.events.HookEventFactory");
        assertPublicInterface(hookFactory);
        assertFactoryMethod(hookFactory, "blockInteract",
                "net.weaver.api.event.events.BlockInteractEvent", 5);
        assertFactoryMethod(hookFactory, "itemUse",
                "net.weaver.api.event.events.ItemUseEvent", 4);
        assertFactoryMethod(hookFactory, "livingEntityDeath",
                "net.weaver.api.event.events.LivingEntityDeathEvent", 2);
        assertFactoryMethod(hookFactory, "containerClose",
                "net.weaver.api.event.events.ContainerCloseEvent", 2);
        assertFactoryMethod(hookFactory, "anvilUpdate",
                "net.weaver.api.event.events.AnvilUpdateEvent", 1);
        assertFactoryMethod(hookFactory, "enchant",
                "net.weaver.api.event.events.EnchantEvent", 2);
        assertFactoryMethod(hookFactory, "playerTeleport",
                "net.weaver.api.event.events.PlayerTeleportEvent", 2);
        assertFactoryMethod(hookFactory, "playerRespawnTarget",
                "net.weaver.api.event.events.PlayerRespawnTargetEvent", 2);
        assertFactoryMethod(hookFactory, "entityHurt",
                "net.weaver.api.event.events.EntityHurtEvent", 3);
        assertFactoryMethod(hookFactory, "entityInteract",
                "net.weaver.api.event.events.EntityInteractEvent", 4);
        assertFactoryMethod(hookFactory, "entityAttack",
                "net.weaver.api.event.events.EntityAttackEvent", 2);
        assertFactoryMethod(hookFactory, "blockBreak",
                "net.weaver.api.event.events.BlockBreakEvent", 2);
        assertFactoryMethod(hookFactory, "blockPlace",
                "net.weaver.api.event.events.BlockPlaceEvent", 1);
        assertFactoryMethod(hookFactory, "itemUseFinish",
                "net.weaver.api.event.events.ItemUseFinishEvent", 1);
        assertFactoryMethod(hookFactory, "itemRelease",
                "net.weaver.api.event.events.ItemReleaseEvent", 1);
        assertFactoryMethod(hookFactory, "projectileLaunch",
                "net.weaver.api.event.events.ProjectileLaunchEvent", 1);
        assertFactoryMethod(hookFactory, "clientPlayerTickPre",
                "net.weaver.api.event.events.ClientPlayerTickEvent", 1);
        assertFactoryMethod(hookFactory, "clientPlayerTickPost",
                "net.weaver.api.event.events.ClientPlayerTickEvent", 1);
        assertFactoryMethod(hookFactory, "entityTick",
                "net.weaver.api.event.events.EntityTickEvent", 3);
        assertFactoryMethod(hookFactory, "chunkLoad",
                "net.weaver.api.event.events.ChunkLoadEvent", 2);
        assertFactoryMethod(hookFactory, "chunkUnload",
                "net.weaver.api.event.events.ChunkUnloadEvent", 2);
        assertFactoryMethod(hookFactory, "entityRemoval",
                "net.weaver.api.event.events.EntityRemovalEvent", 2);
        assertFactoryMethod(hookFactory, "containerClosed",
                "net.weaver.api.event.events.ContainerClosedEvent", 2);
        assertFactoryMethod(hookFactory, "blockEntityLoad",
                "net.weaver.api.event.events.BlockEntityLoadEvent", 1);
        assertFactoryMethod(hookFactory, "blockEntitySave",
                "net.weaver.api.event.events.BlockEntitySaveEvent", 1);
        assertFactoryMethod(hookFactory, "entitySave",
                "net.weaver.api.event.events.EntitySaveEvent", 1);
        assertFactoryMethod(hookFactory, "entityLoad",
                "net.weaver.api.event.events.EntityLoadEvent", 1);
        assertFactoryMethod(hookFactory, "playerRespawnAction",
                "net.weaver.api.event.events.PlayerRespawnActionEvent", 3);
        assertEquals(28, hookFactory.getDeclaredMethods().length,
                "HookEventFactory is a twenty-eight-method bridge");

        Class<?> hookDefault = type("net.weaver.api.event.events.DefaultHookEventFactory");
        assertPublicFinalClass(hookDefault);
        assertTrue(hookFactory.isAssignableFrom(hookDefault));
        constructor(hookDefault);
        Object factory = hookDefault.getConstructor().newInstance();
        Object hurt = hookFactory.getMethod("entityHurt", Object.class, Object.class,
                Object.class).invoke(factory, new Object(), new Object(), new Object());
        assertEquals("net.weaver.api.event.events.EntityHurtEvent", hurt.getClass().getName());

        Class<?> gameFactory = type("net.weaver.api.spi.GameEventFactory");
        assertPublicInterface(gameFactory);
        assertEquals(23, gameFactory.getDeclaredMethods().length,
                "GameEventFactory is a twenty-three-method Object bridge");
        for (Method factoryMethod : gameFactory.getDeclaredMethods()) {
            for (Class<?> parameter : factoryMethod.getParameterTypes()) {
                assertTrue(parameter == Object.class || parameter == Side.class
                                || parameter.isEnum(),
                        factoryMethod + " must keep Object/enum bridge parameters, found "
                                + parameter.getName());
            }
        }
        Class<?> gameDefault = type("net.weaver.api.spi.DefaultGameEventFactory");
        assertPublicFinalClass(gameDefault);
        assertTrue(gameFactory.isAssignableFrom(gameDefault));
        constructor(gameDefault);
    }

    @Test
    void modContextAndWeaverModExtensionPointsAreStable() throws Exception {
        Class<?> context = type("net.weaver.api.ModContext");
        assertPublicInterface(context);
        method(context, "id", String.class);
        method(context, "side", String.class);
        method(context, "configDir", java.nio.file.Path.class);
        method(context, "dataDir", java.nio.file.Path.class);
        method(context, "log", void.class, String.class);
        method(context, "events", EventBus.class);
        method(context, "modEvents", EventBus.class);
        method(context, "globalModEvents", EventBus.class);
        method(context, "logicalSide", LogicalSide.class);
        method(context, "isPhysicalClient", boolean.class);
        method(context, "isPhysicalServer", boolean.class);
        method(context, "isLogicalClient", boolean.class);
        method(context, "isLogicalServer", boolean.class);
        method(context, "isClient", boolean.class);
        method(context, "isServer", boolean.class);
        method(context, "deferredRegister",
                net.weaver.api.registry.DeferredRegister.class,
                net.weaver.api.registry.RegistryKey.class);
        method(context, "registry", net.weaver.api.registry.RegistryView.class,
                net.weaver.api.registry.RegistryKey.class);
        method(context, "findRegistry", java.util.Optional.class,
                net.weaver.api.registry.RegistryKey.class);

        Class<?> mod = type("net.weaver.api.WeaverMod");
        assertPublicInterface(mod);
        assertEquals(24, mod.getDeclaredMethods().length,
                "WeaverMod must keep its 24 default lifecycle methods");
        for (Method lifecycle : mod.getDeclaredMethods()) {
            assertEquals(void.class, lifecycle.getReturnType(),
                    lifecycle.getName() + " must stay void");
            assertTrue(lifecycle.isDefault(), lifecycle.getName() + " must stay a default method");
        }
        method(mod, "onInitialize", void.class, context);
        method(mod, "onRegister", void.class, context);
        method(mod, "onCommonSetup", void.class, context);
        method(mod, "onClientSetup", void.class, context);
        method(mod, "onServerSetup", void.class, context);
        method(mod, "onLoadComplete", void.class, context);
        method(mod, "onServerStarting", void.class, context);
        method(mod, "onServerStarted", void.class, context);
        method(mod, "onServerStopping", void.class, context);
        method(mod, "onServerStopped", void.class, context);
        method(mod, "onClientTick", void.class, context, long.class);
        method(mod, "onServerTick", void.class, context, long.class);
        method(mod, "onPlayerJoin", void.class, context, Object.class);
        method(mod, "onPlayerLeave", void.class, context, Object.class);
        method(mod, "onLevelLoad", void.class, context, Object.class);
        method(mod, "onLevelUnload", void.class, context, Object.class);
        method(mod, "onResourceReload", void.class, context, Object.class, boolean.class);
        method(mod, "onCommandRegister", void.class, context, Object.class);
        method(mod, "onScreenOpen", void.class, context, Object.class);
        method(mod, "onScreenClose", void.class, context, Object.class);
        method(mod, "onChat", void.class, context, Object.class, Object.class);
        method(mod, "onPlayerTick", void.class, context, Object.class);
        method(mod, "onServerSave", void.class, context, boolean.class);
        method(mod, "onLevelSave", void.class, context, Object.class, boolean.class);
    }

    private static void assertFactoryMethod(Class<?> owner, String name, String returnTypeName,
                                            int parameterCount) throws Exception {
        Method candidate = null;
        for (Method method : owner.getDeclaredMethods()) {
            if (method.getName().equals(name)) {
                candidate = method;
                break;
            }
        }
        assertNotNull(candidate, owner.getName() + "." + name + " is missing");
        assertEquals(returnTypeName, candidate.getReturnType().getName());
        assertEquals(parameterCount, candidate.getParameterCount(), candidate.toString());
        for (Class<?> parameter : candidate.getParameterTypes()) {
            assertTrue(parameter == Object.class || parameter.isEnum(),
                    candidate + " must take Object/enum payloads, found "
                            + parameter.getName());
        }
    }

    private static void assertRegistryConstant(String name, String expectedId) throws Exception {
        Field field = net.weaver.api.registry.WeaverRegistries.class.getField(name);
        assertTrue(Modifier.isPublic(field.getModifiers()));
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertEquals(net.weaver.api.registry.RegistryKey.class, field.getType());
        Object value = field.get(null);
        assertNotNull(value, name);
        assertEquals(expectedId,
                value.getClass().getMethod("id").invoke(value), name + " id");
    }

    private static void assertPublicTypes(String... names) throws Exception {
        for (String name : names) {
            Class<?> type = type(name);
            assertTrue(Modifier.isPublic(type.getModifiers()), name + " must be public");
            assertFalse(type.isAnonymousClass(), name + " must not be anonymous");
            assertFalse(type.isLocalClass(), name + " must not be local");
        }
    }

    private static Class<?> type(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException missing) {
            throw new AssertionError("Missing public API type: " + name, missing);
        }
    }

    private static void assertPublicClass(Class<?> type) {
        assertTrue(Modifier.isPublic(type.getModifiers()), type.getName() + " must be public");
    }

    private static void assertPublicFinalClass(Class<?> type) {
        assertPublicClass(type);
        assertTrue(Modifier.isFinal(type.getModifiers()), type.getName() + " must stay final");
    }

    private static void assertPublicInterface(Class<?> type) {
        assertPublicClass(type);
        assertTrue(type.isInterface(), type.getName() + " must stay an interface");
    }

    private static Constructor<?> constructor(Class<?> owner, Class<?>... parameterTypes)
            throws Exception {
        Constructor<?> constructor = owner.getConstructor(parameterTypes);
        assertTrue(Modifier.isPublic(constructor.getModifiers()));
        return constructor;
    }

    private static Method method(Class<?> owner, String name, Class<?> returnType,
                                 Class<?>... parameterTypes) throws Exception {
        Method method = owner.getMethod(name, parameterTypes);
        assertEquals(returnType, method.getReturnType(),
                owner.getName() + "." + name + " return descriptor changed");
        assertTrue(Modifier.isPublic(method.getModifiers()));
        return method;
    }

    private static Method assertStaticMethod(Class<?> owner, String name, Class<?> returnType,
                                             Class<?>... parameterTypes) throws Exception {
        Method method = method(owner, name, returnType, parameterTypes);
        assertTrue(Modifier.isStatic(method.getModifiers()),
                owner.getName() + "." + name + " must stay static");
        return method;
    }

    private static void assertPublicStaticField(Class<?> owner, String name, Class<?> fieldType,
                                                Object expected) throws Exception {
        Field field = owner.getField(name);
        assertTrue(Modifier.isPublic(field.getModifiers()));
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
        assertEquals(fieldType, field.getType(), owner.getName() + "." + name + " type");
        if (expected != null) {
            assertEquals(expected, field.get(null), owner.getName() + "." + name + " value");
        }
    }

    private static List<String> names(Class<?> enumType) {
        Object[] constants = enumType.getEnumConstants();
        assertNotNull(constants, enumType.getName() + " must stay an enum");
        return Arrays.stream(constants).map(Object::toString).collect(Collectors.toList());
    }

    private static List<String> componentNames(Class<?> recordType) {
        return Arrays.stream(recordType.getRecordComponents())
                .map(component -> component.getName())
                .collect(Collectors.toList());
    }
}
