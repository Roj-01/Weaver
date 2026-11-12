package net.weaver.core.hook;

import net.weaver.api.event.EventBus;
import net.weaver.api.event.ListenerHandle;
import net.weaver.api.event.events.ServerStoppedEvent;
import net.weaver.api.event.events.ServerStoppingEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ServerLifecycleHookTest
{
    @BeforeEach
    void setUp()
    {
        GameEventFactoryHolder.resetForTests();
    }

    @AfterEach
    void tearDown()
    {
        GameEventFactoryHolder.resetForTests();
    }

    @Test
    void stoppedEventFiresAtRunLoopExitAndOnlyOnce()
    {
        Object server = new Object();
        List<String> events = new ArrayList<>();
        ListenerHandle stopping = EventBus.GAME.addListener(ServerStoppingEvent.class,
                event -> events.add("stopping"));
        ListenerHandle stopped = EventBus.GAME.addListener(ServerStoppedEvent.class,
                event -> events.add("stopped"));
        try
        {
            GameHooks.serverStarting(server);
            GameHooks.serverStopping(server);

            assertFalse(events.contains("stopped"), "stopServer entry must not report stopped");

            GameHooks.serverStopped(server);
            assertEquals(List.of("stopping", "stopped"), events);

            GameHooks.serverStopped(server);
            assertEquals(2, events.size(), "stopped must fire at most once per server");
        }
        finally
        {
            stopping.unregister();
            stopped.unregister();
        }
    }
}
