package net.weaver.api.game;

import net.weaver.api.ModContext;
import net.weaver.api.WeaverApi;
import net.weaver.api.event.EventBus;
import net.weaver.api.event.EventBusSubscriber;
import net.weaver.api.event.ModScopedEvent;
import net.weaver.api.event.Side;
import net.weaver.api.event.events.ChatEvent;
import net.weaver.api.event.events.CommandRegisterEvent;
import net.weaver.api.event.events.ContainerOpenEvent;
import net.weaver.api.event.events.EntityHurtEvent;
import net.weaver.api.event.events.EntityJoinLevelEvent;
import net.weaver.api.event.events.LevelEvent;
import net.weaver.api.event.events.LevelLoadEvent;
import net.weaver.api.event.events.LevelSaveEvent;
import net.weaver.api.event.events.LevelUnloadEvent;
import net.weaver.api.event.events.LootTableEvent;
import net.weaver.api.event.events.ModLifecycleEvent;
import net.weaver.api.event.events.PlayerDeathEvent;
import net.weaver.api.event.events.PlayerEvent;
import net.weaver.api.event.events.PlayerJoinEvent;
import net.weaver.api.event.events.PlayerLeaveEvent;
import net.weaver.api.event.events.PlayerLoginEvent;
import net.weaver.api.event.events.PlayerLogoutEvent;
import net.weaver.api.event.events.PlayerRespawnEvent;
import net.weaver.api.event.events.ResourceReloadEvent;
import net.weaver.api.event.events.ScreenCloseEvent;
import net.weaver.api.event.events.ScreenEvent;
import net.weaver.api.event.events.ScreenOpenEvent;
import net.weaver.api.event.events.ServerLifecycleEvent;
import net.weaver.api.event.events.ServerSaveEvent;
import net.weaver.api.event.events.ServerStartedEvent;
import net.weaver.api.event.events.ServerStartingEvent;
import net.weaver.api.event.events.ServerStoppedEvent;
import net.weaver.api.event.events.ServerStoppingEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaseEventCompatibilityTest {

    private static final List<Class<?>> DE_FINALIZED = List.of(
            PlayerJoinEvent.class,
            PlayerLeaveEvent.class,
            PlayerLoginEvent.class,
            PlayerLogoutEvent.class,
            LevelLoadEvent.class,
            LevelUnloadEvent.class,
            EntityJoinLevelEvent.class,
            ChatEvent.class,
            ScreenOpenEvent.class,
            ScreenCloseEvent.class,
            CommandRegisterEvent.class,
            ResourceReloadEvent.class,
            ServerStartingEvent.class,
            ServerStartedEvent.class,
            ServerStoppingEvent.class,
            ServerStoppedEvent.class,
            ServerSaveEvent.class,
            LevelSaveEvent.class,
            EntityHurtEvent.class,
            PlayerDeathEvent.class,
            ContainerOpenEvent.class,
            PlayerRespawnEvent.class,
            LootTableEvent.class);

    @Test
    void deFinalizedClassesAreExtensible() {
        for (Class<?> type : DE_FINALIZED) {
            assertFalse(Modifier.isFinal(type.getModifiers()), type.getName() + " is final");
        }
    }

    @Test
    void modLifecycleEventStaysFinalAndIsModScoped() {
        assertTrue(Modifier.isFinal(ModLifecycleEvent.class.getModifiers()));
        assertTrue(ModScopedEvent.class.isAssignableFrom(ModLifecycleEvent.class));

        TestContext context = new TestContext();
        ModLifecycleEvent event = new ModLifecycleEvent(context, ModLifecycleEvent.Phase.INITIALIZE);
        assertEquals("test", event.modId());
    }

    @Test
    void baseConstructorsKeepTheirOriginalDescriptors() throws Exception {
        assertEquals(2, PlayerJoinEvent.class.getConstructor(Object.class, Object.class)
                .getParameterCount());
        assertEquals(2, PlayerLeaveEvent.class.getConstructor(Object.class, Object.class)
                .getParameterCount());
        assertEquals(2, PlayerLoginEvent.class.getConstructor(Object.class, Object.class)
                .getParameterCount());
        assertEquals(2, PlayerLogoutEvent.class.getConstructor(Object.class, Object.class)
                .getParameterCount());
        assertEquals(1, LevelLoadEvent.class.getConstructor(Object.class).getParameterCount());
        assertEquals(1, LevelUnloadEvent.class.getConstructor(Object.class).getParameterCount());
        assertEquals(3, EntityJoinLevelEvent.class
                .getConstructor(Object.class, Object.class, boolean.class).getParameterCount());
        assertEquals(2, EntityJoinLevelEvent.class
                .getConstructor(Object.class, Object.class).getParameterCount());
        assertEquals(4, ChatEvent.class
                .getConstructor(Object.class, Object.class, Object.class, boolean.class)
                .getParameterCount());
        assertEquals(3, ChatEvent.class
                .getConstructor(Object.class, Object.class, Object.class).getParameterCount());
        assertEquals(2, ScreenOpenEvent.class.getConstructor(Object.class, Object.class)
                .getParameterCount());
        assertEquals(2, ScreenCloseEvent.class.getConstructor(Object.class, Object.class)
                .getParameterCount());
        assertEquals(1, CommandRegisterEvent.class.getConstructor(Object.class).getParameterCount());
        assertEquals(3, ResourceReloadEvent.class
                .getConstructor(Object.class, Side.class, ResourceReloadEvent.Phase.class)
                .getParameterCount());
        assertEquals(1, ServerStartingEvent.class.getConstructor(Object.class).getParameterCount());
        assertEquals(1, ServerStartedEvent.class.getConstructor(Object.class).getParameterCount());
        assertEquals(1, ServerStoppingEvent.class.getConstructor(Object.class).getParameterCount());
        assertEquals(1, ServerStoppedEvent.class.getConstructor(Object.class).getParameterCount());
        assertEquals(2, ServerSaveEvent.class
                .getConstructor(Object.class, ServerSaveEvent.Phase.class).getParameterCount());
        assertEquals(2, LevelSaveEvent.class
                .getConstructor(Object.class, LevelSaveEvent.Phase.class).getParameterCount());
        assertEquals(3, EntityHurtEvent.class
                .getConstructor(Object.class, Object.class, Object.class).getParameterCount());
        assertEquals(2, PlayerDeathEvent.class.getConstructor(Object.class, Object.class)
                .getParameterCount());
        assertEquals(2, ContainerOpenEvent.class.getConstructor(Object.class, Object.class)
                .getParameterCount());
        assertEquals(1, PlayerRespawnEvent.class.getConstructor(Object.class).getParameterCount());
        assertEquals(2, LootTableEvent.class.getConstructor(Object.class, Object.class)
                .getParameterCount());
    }

    @Test
    void baseAccessorsKeepObjectReturnTypes() throws Exception {
        assertObjectAccessor(PlayerEvent.class, "playerList");
        assertObjectAccessor(PlayerEvent.class, "player");
        assertObjectAccessor(PlayerJoinEvent.class, "player");
        assertObjectAccessor(LevelEvent.class, "level");
        assertObjectAccessor(EntityJoinLevelEvent.class, "level");
        assertObjectAccessor(EntityJoinLevelEvent.class, "entity");
        assertObjectAccessor(ChatEvent.class, "playerList");
        assertObjectAccessor(ChatEvent.class, "message");
        assertObjectAccessor(ChatEvent.class, "sender");
        assertObjectAccessor(ScreenEvent.class, "minecraft");
        assertObjectAccessor(ScreenEvent.class, "screen");
        assertObjectAccessor(CommandRegisterEvent.class, "commands");
        assertObjectAccessor(CommandRegisterEvent.class, "dispatcher");
        assertObjectAccessor(ResourceReloadEvent.class, "source");
        assertObjectAccessor(ServerLifecycleEvent.class, "server");
        assertObjectAccessor(ServerSaveEvent.class, "server");
        assertObjectAccessor(LevelSaveEvent.class, "level");
        assertObjectAccessor(EntityHurtEvent.class, "level");
        assertObjectAccessor(EntityHurtEvent.class, "entity");
        assertObjectAccessor(EntityHurtEvent.class, "source");
        assertObjectAccessor(PlayerDeathEvent.class, "player");
        assertObjectAccessor(PlayerDeathEvent.class, "source");
        assertObjectAccessor(ContainerOpenEvent.class, "player");
        assertObjectAccessor(ContainerOpenEvent.class, "menuProvider");
        assertObjectAccessor(PlayerRespawnEvent.class, "player");
        assertObjectAccessor(LootTableEvent.class, "lootTable");
        assertObjectAccessor(LootTableEvent.class, "params");
    }

    @Test
    void eventBusSubscriberBusOrdinalsStayStable() {
        assertEquals(0, EventBusSubscriber.Bus.GAME.ordinal());
        assertEquals(1, EventBusSubscriber.Bus.MOD.ordinal());
        assertEquals(2, EventBusSubscriber.Bus.GLOBAL_MOD.ordinal());
    }

    @Test
    void apiVersionIsCurrent() {
        assertEquals("2.1.0", WeaverApi.VERSION);
    }

    @Test
    void modContextUsesLocalBusConventionWithGlobalFallbacks() {
        ModContext context = new TestContext();

        assertSame(EventBus.MOD, context.modEvents());
        assertSame(EventBus.MOD, context.globalModEvents());
    }

    private static void assertObjectAccessor(Class<?> type, String name) throws Exception {
        assertEquals(Object.class, type.getMethod(name).getReturnType(),
                type.getName() + "." + name + " must keep its Object return type");
    }

    private static final class TestContext implements ModContext {

        @Override
        public String id() {
            return "test";
        }

        @Override
        public String side() {
            return "client";
        }

        @Override
        public Path configDir() {
            return Path.of(".");
        }

        @Override
        public Path dataDir() {
            return Path.of(".");
        }

        @Override
        public void log(String message) {
        }
    }
}
