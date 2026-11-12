package net.weaver.api.game.compat;

import net.weaver.api.WeaverMod;
import net.weaver.api.game.GameWeaverMod;
import net.weaver.api.game.TypedGameEventFactory;
import net.weaver.api.game.WeaverGameApi;
import net.weaver.api.game.event.TypedHookEventFactory;
import net.weaver.api.game.registry.GameRegistries;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the typed {@code api-game} catalog as a 2.1-compatible binary surface:
 * every typed event still extends its base event, constructors still take
 * Object payloads, covariant accessors and their Object bridges stay present,
 * and the two typed factories keep their exact method catalogs.
 */
class TypedEventCatalogCompatibilityTest {

    private static final String TYPED_PACKAGE = "net.weaver.api.game.event";

    private static final Set<String> TYPED_EVENTS = Set.of(
            "AnvilUpdateEvent",
            "BlockBreakEvent",
            "BlockEntityLoadEvent",
            "BlockEntitySaveEvent",
            "BlockInteractEvent",
            "BlockPlaceEvent",
            "ChatEvent",
            "ChunkLoadEvent",
            "ChunkUnloadEvent",
            "ClientPlayerTickEvent",
            "CommandRegisterEvent",
            "ContainerCloseEvent",
            "ContainerClosedEvent",
            "ContainerOpenEvent",
            "EnchantEvent",
            "EntityAttackEvent",
            "EntityHurtEvent",
            "EntityInteractEvent",
            "EntityJoinLevelEvent",
            "EntityLoadEvent",
            "EntityRemovalEvent",
            "EntitySaveEvent",
            "EntityTickEvent",
            "ItemReleaseEvent",
            "ItemUseEvent",
            "ItemUseFinishEvent",
            "LevelLoadEvent",
            "LevelSaveEvent",
            "LevelUnloadEvent",
            "LivingEntityDeathEvent",
            "LootTableEvent",
            "PlayerDeathEvent",
            "PlayerJoinEvent",
            "PlayerLeaveEvent",
            "PlayerLoginEvent",
            "PlayerLogoutEvent",
            "PlayerRespawnActionEvent",
            "PlayerRespawnEvent",
            "PlayerRespawnTargetEvent",
            "PlayerTeleportEvent",
            "ProjectileLaunchEvent",
            "ResourceReloadEvent",
            "ScreenCloseEvent",
            "ScreenOpenEvent",
            "ServerSaveEvent",
            "ServerStartedEvent",
            "ServerStartingEvent",
            "ServerStoppedEvent",
            "ServerStoppingEvent");

    private static final Set<String> GAME_FACTORY_METHODS = Set.of(
            "playerLogin", "playerJoin", "playerLogout", "playerLeave",
            "levelLoad", "levelUnload", "entityJoinLevel", "chat",
            "screenOpen", "screenClose", "commandRegister", "resourceReload",
            "serverStarting", "serverStarted", "serverStopping", "serverStopped",
            "serverSave", "levelSave", "entityHurt", "playerDeath",
            "containerOpen", "playerRespawn", "lootTable");

    private static final Set<String> HOOK_FACTORY_METHODS = Set.of(
            "blockInteract", "itemUse", "livingEntityDeath", "containerClose",
            "anvilUpdate", "enchant", "playerTeleport", "playerRespawnTarget",
            "entityHurt", "entityInteract", "entityAttack", "blockBreak",
            "blockPlace", "itemUseFinish", "itemRelease", "projectileLaunch",
            "clientPlayerTickPre", "clientPlayerTickPost", "entityTick",
            "chunkLoad", "chunkUnload", "entityRemoval", "containerClosed",
            "blockEntityLoad", "blockEntitySave", "entitySave", "entityLoad",
            "playerRespawnAction");

    @Test
    void typedEventCatalogMatchesThePackageDirectory() throws Exception {
        Set<String> sourceNames = new TreeSet<>(sourceClassNames());
        assertEquals(new TreeSet<>(TYPED_EVENTS), sourceNames,
                "the source directory and the compatibility catalog must agree; "
                        + "adding or removing a typed event needs a review of this test");

        for (String name : TYPED_EVENTS) {
            Class<?> typed = type(TYPED_PACKAGE + "." + name);
            Class<?> base = type("net.weaver.api.event.events." + name);

            assertTrue(Modifier.isPublic(typed.getModifiers()), name + " must be public");
            assertTrue(Modifier.isFinal(typed.getModifiers()), name + " must stay final");
            assertSame(base, typed.getSuperclass(),
                    name + " must extend its same-named base event");
        }
    }

    @Test
    void typedConstructorsStillTakeObjectPayloads() throws Exception {
        for (String name : TYPED_EVENTS) {
            Class<?> typed = type(TYPED_PACKAGE + "." + name);
            Constructor<?>[] constructors = typed.getConstructors();
            assertTrue(constructors.length > 0, name + " must keep a public constructor");
            for (Constructor<?> constructor : constructors) {
                for (Class<?> parameter : constructor.getParameterTypes()) {
                    assertTrue(parameter == Object.class
                                    || parameter.isPrimitive()
                                    || parameter.isEnum(),
                            constructor + " must keep Object/primitive/enum payload parameters, "
                                    + "found " + parameter.getName());
                }
            }
        }

        assertTrue(hasConstructor(type(TYPED_PACKAGE + ".EntityJoinLevelEvent"),
                Object.class, Object.class, boolean.class));
        assertTrue(hasConstructor(type(TYPED_PACKAGE + ".EntityHurtEvent"),
                Object.class, Object.class, Object.class, boolean.class));
        assertTrue(hasConstructor(type(TYPED_PACKAGE + ".ResourceReloadEvent"),
                Object.class, net.weaver.api.event.Side.class,
                net.weaver.api.event.events.ResourceReloadEvent.Phase.class));
    }

    @Test
    void covariantAccessorsKeepTheirObjectBridges() throws Exception {
        for (String name : TYPED_EVENTS) {
            Class<?> typed = type(TYPED_PACKAGE + "." + name);

            List<Method> accessors = Arrays.stream(typed.getDeclaredMethods())
                    .filter(method -> !method.isSynthetic())
                    .filter(method -> !method.isBridge())
                    .filter(method -> method.getParameterCount() == 0)
                    .filter(method -> method.getReturnType() != void.class)
                    .filter(method -> method.getReturnType() != Object.class)
                    .filter(method -> Modifier.isPublic(method.getModifiers()))
                    .collect(Collectors.toList());

            if (name.equals("ResourceReloadEvent")) {
                assertTrue(accessors.isEmpty(),
                        "ResourceReloadEvent must not narrow its heterogeneous source");
                Method source = typed.getMethod("source");
                assertEquals(Object.class, source.getReturnType());
                assertEquals("net.weaver.api.event.events.ResourceReloadEvent",
                        source.getDeclaringClass().getName());
                continue;
            }

            assertFalse(accessors.isEmpty(),
                    name + " must keep at least one covariant typed accessor");
            for (Method accessor : accessors) {
                assertTrue(isGameType(accessor.getReturnType()),
                        accessor + " must expose a game type");
                Method bridge = Arrays.stream(typed.getDeclaredMethods())
                        .filter(Method::isBridge)
                        .filter(method -> method.getName().equals(accessor.getName()))
                        .filter(method -> method.getParameterCount() == 0)
                        .findFirst()
                        .orElseThrow(() -> new AssertionError(
                                "Missing Object bridge for " + typed.getName() + "."
                                        + accessor.getName()));
                assertTrue(bridge.isSynthetic(), bridge + " must be synthetic");
                assertEquals(Object.class, bridge.getReturnType(),
                        bridge + " must keep the base Object descriptor");
            }
        }

        assertAccessor("PlayerJoinEvent", "player",
                "net.minecraft.server.level.ServerPlayer");
        assertAccessor("PlayerJoinEvent", "playerList",
                "net.minecraft.server.players.PlayerList");
        assertAccessor("LevelLoadEvent", "level", "net.minecraft.world.level.Level");
        assertAccessor("LevelSaveEvent", "level", "net.minecraft.server.level.ServerLevel");
        assertAccessor("EntityHurtEvent", "source",
                "net.minecraft.world.damagesource.DamageSource");
        assertAccessor("EntityJoinLevelEvent", "level",
                "net.minecraft.server.level.ServerLevel");
        assertAccessor("ChatEvent", "message",
                "net.minecraft.network.chat.PlayerChatMessage");
        assertAccessor("ServerStartedEvent", "server",
                "net.minecraft.server.MinecraftServer");
        assertAccessor("PlayerRespawnTargetEvent", "respawnTarget",
                "net.minecraft.world.level.portal.TeleportTransition");
        assertAccessor("ScreenOpenEvent", "screen",
                "net.minecraft.client.gui.screens.Screen");
        assertAccessor("EntityAttackEvent", "target",
                "net.minecraft.world.entity.Entity");
        assertAccessor("BlockBreakEvent", "pos", "net.minecraft.core.BlockPos");
        assertAccessor("BlockPlaceEvent", "placeContext",
                "net.minecraft.world.item.context.BlockPlaceContext");
        assertAccessor("ChunkLoadEvent", "chunk",
                "net.minecraft.world.level.chunk.LevelChunk");
        assertAccessor("EntityRemovalEvent", "reason",
                "net.minecraft.world.entity.Entity$RemovalReason");
        assertAccessor("EntityTickEvent", "entity",
                "net.minecraft.world.entity.Entity");
        assertAccessor("ContainerClosedEvent", "containerMenu",
                "net.minecraft.world.inventory.AbstractContainerMenu");
        assertAccessor("PlayerRespawnActionEvent", "oldPlayer",
                "net.minecraft.server.level.ServerPlayer");
        assertAccessor("EntitySaveEvent", "entity",
                "net.minecraft.world.entity.Entity");
        assertAccessor("EntityLoadEvent", "entity",
                "net.minecraft.world.entity.Entity");
    }

    @Test
    void typedGameEventFactoryKeepsItsTwentyThreeMethods() throws Exception {
        assertPublicFinalClass(TypedGameEventFactory.class);
        assertNotNull(TypedGameEventFactory.class.getConstructor());

        Set<String> names = Arrays.stream(TypedGameEventFactory.class.getDeclaredMethods())
                .filter(method -> !method.isSynthetic())
                .map(Method::getName)
                .collect(Collectors.toCollection(TreeSet::new));
        assertEquals(new TreeSet<>(GAME_FACTORY_METHODS), names,
                "TypedGameEventFactory must keep its 23 factory methods");
        assertEquals(23, TypedGameEventFactory.class.getDeclaredMethods().length);

        Object factory = TypedGameEventFactory.class.getConstructor().newInstance();
        for (Method method : TypedGameEventFactory.class.getDeclaredMethods()) {
            for (Class<?> parameter : method.getParameterTypes()) {
                assertTrue(parameter == Object.class || parameter.isEnum(),
                        method + " must keep Object/enum bridge parameters, found "
                                + parameter.getName());
            }
            Object result = invokeWithDummyPayloads(factory, method);
            Class<?> base = method.getReturnType();
            Class<?> typed = type("net.weaver.api.game.event." + base.getSimpleName());
            assertSame(typed, result.getClass(),
                    method.getName() + " must create the typed subclass");
            assertSame(base, typed.getSuperclass());
        }

        assertTrue(call(factory, "entityJoinLevel", Object.class, Object.class)
                .isCancelable(), "the typed entity-join factory event must stay cancelable");
        assertTrue(call(factory, "chat", Object.class, Object.class, Object.class)
                .isCancelable(), "the typed chat factory event must stay cancelable");
        assertFalse(call(factory, "entityHurt", Object.class, Object.class, Object.class)
                .isCancelable(), "the typed game factory entity-hurt event is observation-only");
    }

    @Test
    void typedHookEventFactoryKeepsItsTwentyEightMethods() throws Exception {
        assertPublicFinalClass(TypedHookEventFactory.class);
        assertNotNull(TypedHookEventFactory.class.getConstructor());

        Set<String> names = Arrays.stream(TypedHookEventFactory.class.getDeclaredMethods())
                .filter(method -> !method.isSynthetic())
                .map(Method::getName)
                .collect(Collectors.toCollection(TreeSet::new));
        assertEquals(new TreeSet<>(HOOK_FACTORY_METHODS), names,
                "TypedHookEventFactory must keep its 28 hook methods");
        assertEquals(28, TypedHookEventFactory.class.getDeclaredMethods().length);

        Object factory = TypedHookEventFactory.class.getConstructor().newInstance();
        for (Method method : TypedHookEventFactory.class.getDeclaredMethods()) {
            for (Class<?> parameter : method.getParameterTypes()) {
                assertTrue(parameter == Object.class || parameter.isEnum(),
                        method + " must keep Object/enum bridge parameters, found "
                                + parameter.getName());
            }
            Object result = invokeWithDummyPayloads(factory, method);
            Class<?> base = method.getReturnType();
            Class<?> typed = type("net.weaver.api.game.event." + base.getSimpleName());
            assertSame(typed, result.getClass(),
                    method.getName() + " must create the typed subclass");
            assertSame(base, typed.getSuperclass());
        }

        assertTrue(call(factory, "entityHurt", Object.class, Object.class, Object.class)
                .isCancelable(), "the typed hook entity-hurt event must stay cancelable");
        assertTrue(call(factory, "playerRespawnTarget", Object.class, Object.class)
                .hasResult(), "the typed respawn-target event must stay result-based");
        assertFalse(call(factory, "playerRespawnTarget", Object.class, Object.class)
                .isCancelable(), "the typed respawn-target event is not cancelable");
    }

    @Test
    void gameWeaverModKeepsObjectBridgesAndTypedExtensionPoints() throws Exception {
        assertTrue(GameWeaverMod.class.isInterface());
        assertTrue(WeaverMod.class.isAssignableFrom(GameWeaverMod.class));

        Class<?> context = net.weaver.api.ModContext.class;
        Class<?> player = type("net.minecraft.server.level.ServerPlayer");
        Class<?> level = type("net.minecraft.world.level.Level");

        assertDefaultVoidMethod(GameWeaverMod.class, "onPlayerJoin", context, player);
        assertDefaultVoidMethod(GameWeaverMod.class, "onPlayerJoin", context, Object.class);
        assertDefaultVoidMethod(GameWeaverMod.class, "onPlayerLeave", context, player);
        assertDefaultVoidMethod(GameWeaverMod.class, "onPlayerLeave", context, Object.class);
        assertDefaultVoidMethod(GameWeaverMod.class, "onLevelLoad", context, level);
        assertDefaultVoidMethod(GameWeaverMod.class, "onLevelLoad", context, Object.class);
        assertDefaultVoidMethod(GameWeaverMod.class, "onLevelUnload", context, level);
        assertDefaultVoidMethod(GameWeaverMod.class, "onLevelUnload", context, Object.class);

        TypedOnlyMod mod = new TypedOnlyMod();
        mod.onPlayerJoin(null, new Object());
        mod.onPlayerLeave(null, new Object());
        mod.onLevelLoad(null, new Object());
        mod.onLevelUnload(null, new Object());
        assertEquals(0, mod.typedCalls,
                "the inherited Object bridges must ignore payloads that are not game types");
    }

    @Test
    void weaverGameApiAndGameRegistriesKeepTheirSurface() throws Exception {
        assertPublicFinalClass(WeaverGameApi.class);
        Field version = WeaverGameApi.class.getField("VERSION");
        assertEquals(String.class, version.getType());
        assertEquals("26.3", version.get(null));

        assertPublicFinalClass(GameRegistries.class);
        assertRegistryKey("ITEM", "minecraft:item", "net.minecraft.world.item.Item");
        assertRegistryKey("BLOCK", "minecraft:block",
                "net.minecraft.world.level.block.Block");
        assertRegistryKey("ENTITY_TYPE", "minecraft:entity_type",
                "net.minecraft.world.entity.EntityType<?>");
        assertRegistryKey("FLUID", "minecraft:fluid", "net.minecraft.world.level.material.Fluid");
        assertRegistryKey("MOB_EFFECT", "minecraft:mob_effect",
                "net.minecraft.world.effect.MobEffect");
        assertRegistryKey("ENCHANTMENT", "minecraft:enchantment",
                "net.minecraft.world.item.enchantment.Enchantment");
        assertRegistryKey("BLOCK_ENTITY_TYPE", "minecraft:block_entity_type",
                "net.minecraft.world.level.block.entity.BlockEntityType<?>");
        assertRegistryKey("MENU", "minecraft:menu", "net.minecraft.world.inventory.MenuType<?>");
        assertRegistryKey("RECIPE_TYPE", "minecraft:recipe_type",
                "net.minecraft.world.item.crafting.RecipeType<?>");
        assertRegistryKey("ATTRIBUTE", "minecraft:attribute",
                "net.minecraft.world.entity.ai.attributes.Attribute");
        assertRegistryKey("PARTICLE_TYPE", "minecraft:particle_type",
                "net.minecraft.core.particles.ParticleType<?>");
        assertRegistryKey("SOUND_EVENT", "minecraft:sound_event",
                "net.minecraft.sounds.SoundEvent");
        assertRegistryKey("POTION", "minecraft:potion",
                "net.minecraft.world.item.alchemy.Potion");

        assertEquals(13, Arrays.stream(GameRegistries.class.getDeclaredFields())
                .filter(field -> field.getType() == net.weaver.api.registry.RegistryKey.class)
                .count(), "GameRegistries must keep its 13 typed registry keys");
        assertEquals(0, GameRegistries.class.getConstructors().length,
                "GameRegistries must stay non-instantiable");
    }

    private static void assertRegistryKey(String name, String expectedId,
                                           String expectedValueType) throws Exception {
        Field field = GameRegistries.class.getField(name);
        assertTrue(Modifier.isPublic(field.getModifiers()));
        assertTrue(Modifier.isStatic(field.getModifiers()));
        assertTrue(Modifier.isFinal(field.getModifiers()));
        assertEquals(net.weaver.api.registry.RegistryKey.class, field.getType());

        Type genericType = field.getGenericType();
        assertTrue(genericType instanceof ParameterizedType,
                name + " must keep its typed RegistryKey generic");
        Type argument = ((ParameterizedType) genericType).getActualTypeArguments()[0];
        assertEquals(expectedValueType, argument.getTypeName(), name + " value type");

        Object value = field.get(null);
        assertNotNull(value);
        assertEquals(expectedId, value.getClass().getMethod("id").invoke(value));
    }

    private static void assertAccessor(String simpleName, String accessor,
                                       String expectedReturnType) throws Exception {
        Class<?> typed = type(TYPED_PACKAGE + "." + simpleName);
        Method method = typed.getMethod(accessor);
        assertEquals(expectedReturnType, method.getReturnType().getName(),
                simpleName + "." + accessor + " typed return");
    }

    private static boolean hasConstructor(Class<?> owner, Class<?>... parameterTypes)
            throws Exception {
        owner.getConstructor(parameterTypes);
        return true;
    }

    private static void assertDefaultVoidMethod(Class<?> owner, String name,
                                                Class<?>... parameterTypes) throws Exception {
        Method method = owner.getMethod(name, parameterTypes);
        assertTrue(method.isDefault(), owner.getName() + "." + name + " must stay default");
        assertEquals(void.class, method.getReturnType());
    }

    private static net.weaver.api.event.Event call(Object factory, String name,
                                                   Class<?>... parameterTypes) throws Exception {
        Method method = factory.getClass().getMethod(name, parameterTypes);
        return (net.weaver.api.event.Event) invokeWithDummyPayloads(factory, method);
    }

    private static Object invokeWithDummyPayloads(Object factory, Method method)
            throws Exception {
        Class<?>[] parameterTypes = method.getParameterTypes();
        Object[] arguments = new Object[parameterTypes.length];
        for (int index = 0; index < parameterTypes.length; index++) {
            Class<?> parameter = parameterTypes[index];
            if (parameter == Object.class) {
                arguments[index] = new Object();
            } else if (parameter.isEnum()) {
                Object[] constants = parameter.getEnumConstants();
                assertNotNull(constants, parameter + " must be an enum");
                arguments[index] = constants[0];
            } else {
                throw new AssertionError(method + " has a non-bridge parameter "
                        + parameter.getName());
            }
        }
        return method.invoke(factory, arguments);
    }

    private static boolean isGameType(Class<?> type) {
        return type.getName().startsWith("net.minecraft.")
                || type.getName().startsWith("com.mojang.");
    }

    private static Set<String> sourceClassNames() {
        Path directory = eventSourceDirectory();
        try (var stream = Files.list(directory)) {
            return stream
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".java"))
                    .map(name -> name.substring(0, name.length() - ".java".length()))
                    .filter(name -> !name.equals("TypedHookEventFactory"))
                    .collect(Collectors.toCollection(TreeSet::new));
        } catch (IOException failure) {
            throw new AssertionError("Cannot enumerate " + directory, failure);
        }
    }

    private static Path eventSourceDirectory() {
        Path moduleRelative = Path.of("api-game", "src", "main", "java",
                "net", "weaver", "api", "game", "event");
        Path direct = Path.of("src", "main", "java", "net", "weaver", "api", "game", "event");
        if (Files.isDirectory(direct)) {
            return direct;
        }
        if (Files.isDirectory(moduleRelative)) {
            return moduleRelative;
        }
        Path cursor = Path.of("").toAbsolutePath();
        while (cursor != null) {
            Path candidate = cursor.resolve(moduleRelative);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            cursor = cursor.getParent();
        }
        throw new AssertionError("Cannot locate the typed event source directory from "
                + Path.of("").toAbsolutePath());
    }

    private static Class<?> type(String name) {
        try {
            return Class.forName(name, false,
                    TypedEventCatalogCompatibilityTest.class.getClassLoader());
        } catch (ClassNotFoundException missing) {
            throw new AssertionError("Missing typed compatibility type: " + name, missing);
        }
    }

    private static void assertPublicFinalClass(Class<?> type) {
        assertTrue(Modifier.isPublic(type.getModifiers()), type.getName() + " must be public");
        assertTrue(Modifier.isFinal(type.getModifiers()), type.getName() + " must stay final");
    }

    private static final class TypedOnlyMod implements GameWeaverMod {
        private int typedCalls;

        @Override
        public void onPlayerJoin(net.weaver.api.ModContext context,
                                 net.minecraft.server.level.ServerPlayer player) {
            typedCalls++;
        }

        @Override
        public void onPlayerLeave(net.weaver.api.ModContext context,
                                  net.minecraft.server.level.ServerPlayer player) {
            typedCalls++;
        }

        @Override
        public void onLevelLoad(net.weaver.api.ModContext context,
                                net.minecraft.world.level.Level level) {
            typedCalls++;
        }

        @Override
        public void onLevelUnload(net.weaver.api.ModContext context,
                                  net.minecraft.world.level.Level level) {
            typedCalls++;
        }
    }
}
