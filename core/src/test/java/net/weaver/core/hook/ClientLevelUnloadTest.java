package net.weaver.core.hook;

import net.weaver.api.event.EventBus;
import net.weaver.api.event.ListenerHandle;
import net.weaver.api.event.events.LevelUnloadEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

class ClientLevelUnloadTest
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
    void reportsTheLevelFieldInsteadOfTheMinecraftInstance()
    {
        Object level = new Object();
        HasLevel minecraft = new HasLevel(level);
        AtomicReference<Object> reported = new AtomicReference<>();
        ListenerHandle handle = EventBus.GAME.addListener(LevelUnloadEvent.class,
                event -> reported.set(event.level()));
        try
        {
            GameHooks.clientLevelUnload(minecraft);

            assertSame(level, reported.get());
        }
        finally
        {
            handle.unregister();
        }
    }

    @Test
    void skipsWhenTheLevelFieldIsNull()
    {
        AtomicBoolean fired = new AtomicBoolean();
        ListenerHandle handle = EventBus.GAME.addListener(LevelUnloadEvent.class,
                event -> fired.set(true));
        try
        {
            GameHooks.clientLevelUnload(new HasLevel(null));

            assertFalse(fired.get());
        }
        finally
        {
            handle.unregister();
        }
    }

    @Test
    void skipsWhenTheLevelFieldIsMissing()
    {
        AtomicBoolean fired = new AtomicBoolean();
        ListenerHandle handle = EventBus.GAME.addListener(LevelUnloadEvent.class,
                event -> fired.set(true));
        try
        {
            GameHooks.clientLevelUnload(new NoLevel());

            assertFalse(fired.get());
        }
        finally
        {
            handle.unregister();
        }
    }

    private static final class HasLevel
    {
        public Object level;

        private HasLevel(Object level)
        {
            this.level = level;
        }
    }

    private static final class NoLevel
    {
    }
}
