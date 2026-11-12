package net.weaver.api.game;

import net.weaver.api.event.Event;
import net.weaver.api.event.EventBus;
import net.weaver.api.event.Side;
import net.weaver.api.event.events.ChatEvent;
import net.weaver.api.event.events.CommandRegisterEvent;
import net.weaver.api.event.events.ContainerOpenEvent;
import net.weaver.api.event.events.EntityHurtEvent;
import net.weaver.api.event.events.EntityJoinLevelEvent;
import net.weaver.api.event.events.LevelLoadEvent;
import net.weaver.api.event.events.LevelSaveEvent;
import net.weaver.api.event.events.LevelUnloadEvent;
import net.weaver.api.event.events.LootTableEvent;
import net.weaver.api.event.events.PlayerDeathEvent;
import net.weaver.api.event.events.PlayerJoinEvent;
import net.weaver.api.event.events.PlayerLeaveEvent;
import net.weaver.api.event.events.PlayerLoginEvent;
import net.weaver.api.event.events.PlayerLogoutEvent;
import net.weaver.api.event.events.PlayerRespawnEvent;
import net.weaver.api.event.events.ResourceReloadEvent;
import net.weaver.api.event.events.ScreenCloseEvent;
import net.weaver.api.event.events.ScreenOpenEvent;
import net.weaver.api.event.events.ServerSaveEvent;
import net.weaver.api.event.events.ServerStartedEvent;
import net.weaver.api.event.events.ServerStartingEvent;
import net.weaver.api.event.events.ServerStoppedEvent;
import net.weaver.api.event.events.ServerStoppingEvent;
import net.weaver.api.spi.DefaultGameEventFactory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultGameEventFactoryTest {

    private final DefaultGameEventFactory factory = new DefaultGameEventFactory();

    @Test
    void everyFactoryMethodPostsOnALocalBus() {
        EventBus bus = EventBus.local("fallback-factory");
        AtomicInteger received = new AtomicInteger();
        bus.addListener(Event.class, event -> received.incrementAndGet());

        List<Event> events = createAllEvents();

        for (Event event : events) {
            bus.post(event);
        }

        assertEquals(23, events.size());
        assertEquals(23, received.get());
    }

    @Test
    void fallbackCreatesBaseEventTypes() {
        assertEquals(PlayerLoginEvent.class, factory.playerLogin(new Object(), new Object()).getClass());
        assertEquals(PlayerJoinEvent.class, factory.playerJoin(new Object(), new Object()).getClass());
        assertEquals(PlayerLogoutEvent.class,
                factory.playerLogout(new Object(), new Object()).getClass());
        assertEquals(PlayerLeaveEvent.class, factory.playerLeave(new Object(), new Object()).getClass());
        assertEquals(LevelLoadEvent.class, factory.levelLoad(new Object()).getClass());
        assertEquals(LevelUnloadEvent.class, factory.levelUnload(new Object()).getClass());
        assertEquals(EntityJoinLevelEvent.class,
                factory.entityJoinLevel(new Object(), new Object()).getClass());
        assertEquals(ChatEvent.class,
                factory.chat(new Object(), new Object(), new Object()).getClass());
        assertEquals(ScreenOpenEvent.class,
                factory.screenOpen(new Object(), new Object()).getClass());
        assertEquals(ScreenCloseEvent.class,
                factory.screenClose(new Object(), new Object()).getClass());
        assertEquals(CommandRegisterEvent.class, factory.commandRegister(new Object()).getClass());
        assertEquals(ResourceReloadEvent.class,
                factory.resourceReload(new Object(), Side.SERVER,
                        ResourceReloadEvent.Phase.START).getClass());
        assertEquals(ServerStartingEvent.class, factory.serverStarting(new Object()).getClass());
        assertEquals(ServerStartedEvent.class, factory.serverStarted(new Object()).getClass());
        assertEquals(ServerStoppingEvent.class, factory.serverStopping(new Object()).getClass());
        assertEquals(ServerStoppedEvent.class, factory.serverStopped(new Object()).getClass());
        assertEquals(ServerSaveEvent.class,
                factory.serverSave(new Object(), ServerSaveEvent.Phase.START).getClass());
        assertEquals(LevelSaveEvent.class,
                factory.levelSave(new Object(), LevelSaveEvent.Phase.FINISHED).getClass());
        assertEquals(EntityHurtEvent.class,
                factory.entityHurt(new Object(), new Object(), new Object()).getClass());
        assertEquals(PlayerDeathEvent.class,
                factory.playerDeath(new Object(), new Object()).getClass());
        assertEquals(ContainerOpenEvent.class,
                factory.containerOpen(new Object(), new Object()).getClass());
        assertEquals(PlayerRespawnEvent.class, factory.playerRespawn(new Object()).getClass());
        assertEquals(LootTableEvent.class,
                factory.lootTable(new Object(), new Object()).getClass());
    }

    @Test
    void cancelableFallbackEventsCanBeCanceled() {
        EntityJoinLevelEvent join = factory.entityJoinLevel(new Object(), new Object());
        assertInstanceOf(EntityJoinLevelEvent.class, join);
        assertTrue(join.isCancelable());
        join.setCanceled(true);
        assertTrue(join.isCanceled());

        ChatEvent chat = factory.chat(new Object(), new Object(), new Object());
        assertTrue(chat.isCancelable());
        chat.setCanceled(true);
        assertTrue(chat.isCanceled());
    }

    @Test
    void canceledFallbackPostReturnsFalse() {
        EventBus bus = EventBus.local("fallback-cancel");
        EntityJoinLevelEvent join = factory.entityJoinLevel(new Object(), new Object());
        join.setCanceled(true);

        assertFalse(bus.post(join));
    }

    private List<Event> createAllEvents() {
        List<Event> events = new ArrayList<>();
        events.add(factory.playerLogin(new Object(), new Object()));
        events.add(factory.playerJoin(new Object(), new Object()));
        events.add(factory.playerLogout(new Object(), new Object()));
        events.add(factory.playerLeave(new Object(), new Object()));
        events.add(factory.levelLoad(new Object()));
        events.add(factory.levelUnload(new Object()));
        events.add(factory.entityJoinLevel(new Object(), new Object()));
        events.add(factory.chat(new Object(), new Object(), new Object()));
        events.add(factory.screenOpen(new Object(), new Object()));
        events.add(factory.screenClose(new Object(), new Object()));
        events.add(factory.commandRegister(new Object()));
        events.add(factory.resourceReload(new Object(), Side.CLIENT,
                ResourceReloadEvent.Phase.START));
        events.add(factory.serverStarting(new Object()));
        events.add(factory.serverStarted(new Object()));
        events.add(factory.serverStopping(new Object()));
        events.add(factory.serverStopped(new Object()));
        events.add(factory.serverSave(new Object(), ServerSaveEvent.Phase.START));
        events.add(factory.levelSave(new Object(), LevelSaveEvent.Phase.FINISHED));
        events.add(factory.entityHurt(new Object(), new Object(), new Object()));
        events.add(factory.playerDeath(new Object(), new Object()));
        events.add(factory.containerOpen(new Object(), new Object()));
        events.add(factory.playerRespawn(new Object()));
        events.add(factory.lootTable(new Object(), new Object()));
        return events;
    }
}
