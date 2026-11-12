package net.weaver.api.game;

import net.weaver.api.event.EventBus;
import net.weaver.api.game.event.ChatEvent;
import net.weaver.api.game.event.EntityJoinLevelEvent;
import net.weaver.api.game.event.PlayerJoinEvent;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypedDispatchTest {

    @Test
    void baseListenerReceivesTypedSubclass() {
        EventBus bus = EventBus.local("typed-dispatch");
        AtomicInteger received = new AtomicInteger();
        bus.addListener(net.weaver.api.event.events.PlayerJoinEvent.class,
                event -> received.incrementAndGet());

        PlayerJoinEvent event = new PlayerJoinEvent(new Object(), new Object());
        assertTrue(bus.post(event));

        assertEquals(1, received.get());
        assertSame(bus, event.getBus());
    }

    @Test
    void twoArgumentTypedEntityJoinIsCancelable() {
        EventBus bus = EventBus.local("typed-cancel");
        bus.addListener(net.weaver.api.event.events.EntityJoinLevelEvent.class,
                event -> event.setCanceled(true));

        EntityJoinLevelEvent event = new EntityJoinLevelEvent(new Object(), new Object());

        assertTrue(event.isCancelable());
        assertFalse(bus.post(event));
        assertTrue(event.isCanceled());
    }

    @Test
    void explicitTypedEntityJoinCanBeObservationOnly() {
        EntityJoinLevelEvent event = new EntityJoinLevelEvent(new Object(), new Object(), false);

        assertFalse(event.isCancelable());
        assertFalse(event.isCanceled());
    }

    @Test
    void typedChatDefaultsToCancelable() {
        ChatEvent event = new ChatEvent(new Object(), new Object(), new Object());

        assertTrue(event.isCancelable());
        event.setCanceled(true);
        assertTrue(event.isCanceled());
    }
}
