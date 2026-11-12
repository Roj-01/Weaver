package net.weaver.api.event;

import net.weaver.api.ModContext;
import net.weaver.api.event.events.ModLifecycleEvent;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventFilterTest
{
    @Test
    void allAcceptsEveryEventAndIsASingleton()
    {
        EventFilter filter = EventFilter.all();

        assertSame(filter, EventFilter.all());
        assertTrue(filter.accept(new Event()));
        assertTrue(filter.accept(new Event().setLogicalSide(LogicalSide.SERVER)));
    }

    @Test
    void logicalSideFiltersMatchMetadata()
    {
        Event client = new Event().setLogicalSide(LogicalSide.CLIENT);
        Event server = new Event().setLogicalSide(LogicalSide.SERVER);
        Event both = new Event().setLogicalSide(LogicalSide.BOTH);

        EventFilter serverFilter = EventFilter.logicalSide(LogicalSide.SERVER);
        assertFalse(serverFilter.accept(client));
        assertTrue(serverFilter.accept(server));
        assertTrue(serverFilter.accept(both));

        EventFilter clientFilter = EventFilter.logicalSide(LogicalSide.CLIENT);
        assertTrue(clientFilter.accept(client));
        assertFalse(clientFilter.accept(server));
        assertTrue(clientFilter.accept(both));

        EventFilter bothFilter = EventFilter.logicalSide(LogicalSide.BOTH);
        assertSame(EventFilter.all(), bothFilter);
        assertTrue(bothFilter.accept(client));
        assertTrue(bothFilter.accept(server));
        assertTrue(bothFilter.accept(both));
    }

    @Test
    void modIdAcceptsMatchingLifecycleEvents()
    {
        EventFilter filter = EventFilter.modId("alpha");
        ModLifecycleEvent matching = new ModLifecycleEvent(fakeContext("alpha"),
                ModLifecycleEvent.Phase.INITIALIZE);
        ModLifecycleEvent other = new ModLifecycleEvent(fakeContext("beta"),
                ModLifecycleEvent.Phase.INITIALIZE);

        assertTrue(filter.accept(matching));
        assertFalse(filter.accept(other));
    }

    @Test
    void modIdAcceptsMatchingModScopedEvents()
    {
        EventFilter filter = EventFilter.modId("alpha");

        assertTrue(filter.accept(new ScopedEvent("alpha")));
        assertFalse(filter.accept(new ScopedEvent("beta")));
        assertFalse(filter.accept(new Event()));
    }

    @Test
    void modIdAcceptsEventsPostedOnTheMatchingLocalBus()
    {
        EventFilter filter = EventFilter.modId("alpha");
        Event matching = new Event();
        Event other = new Event();

        EventBus.local("alpha").post(matching);
        EventBus.local("beta").post(other);

        assertTrue(filter.accept(matching));
        assertFalse(filter.accept(other));
    }

    @Test
    void andEvaluatesInOrderAndShortCircuits()
    {
        AtomicInteger firstCalls = new AtomicInteger();
        AtomicInteger secondCalls = new AtomicInteger();
        EventFilter rejecting = event ->
        {
            firstCalls.incrementAndGet();
            return false;
        };
        EventFilter accepting = event ->
        {
            secondCalls.incrementAndGet();
            return true;
        };
        Event event = new Event();

        assertFalse(EventFilter.and(rejecting, accepting).accept(event));
        assertEquals(1, firstCalls.get());
        assertEquals(0, secondCalls.get());

        assertTrue(EventFilter.and(accepting, accepting).accept(event));
        assertEquals(2, secondCalls.get());
    }

    @Test
    void orEvaluatesInOrderAndShortCircuits()
    {
        AtomicInteger firstCalls = new AtomicInteger();
        AtomicInteger secondCalls = new AtomicInteger();
        EventFilter accepting = event ->
        {
            firstCalls.incrementAndGet();
            return true;
        };
        EventFilter rejecting = event ->
        {
            secondCalls.incrementAndGet();
            return false;
        };

        assertTrue(EventFilter.or(accepting, rejecting).accept(new Event()));
        assertEquals(1, firstCalls.get());
        assertEquals(0, secondCalls.get());

        assertFalse(EventFilter.or(rejecting, rejecting).accept(new Event()));
        assertEquals(2, secondCalls.get());
    }

    @Test
    void emptyCompositionHasDocumentedDefaults()
    {
        assertSame(EventFilter.all(), EventFilter.and());
        assertFalse(EventFilter.or().accept(new Event()));
    }

    @Test
    void nullArgumentsAreRejectedAtConstruction()
    {
        assertThrows(NullPointerException.class, () -> EventFilter.and((EventFilter[]) null));
        assertThrows(NullPointerException.class, () -> EventFilter.or((EventFilter[]) null));
        assertThrows(NullPointerException.class, () -> EventFilter.and(EventFilter.all(), null));
        assertThrows(NullPointerException.class, () -> EventFilter.or(null, EventFilter.all()));
        assertThrows(NullPointerException.class, () -> EventFilter.modId(null));
        assertThrows(NullPointerException.class, () -> EventFilter.logicalSide(null));
    }

    private static ModContext fakeContext(String id)
    {
        return new ModContext()
        {
            @Override
            public String id()
            {
                return id;
            }

            @Override
            public String side()
            {
                return "client";
            }

            @Override
            public Path configDir()
            {
                return Path.of(".");
            }

            @Override
            public Path dataDir()
            {
                return Path.of(".");
            }

            @Override
            public void log(String message)
            {
            }
        };
    }

    private static final class ScopedEvent extends Event implements ModScopedEvent
    {
        private final String modId;

        private ScopedEvent(String modId)
        {
            this.modId = modId;
        }

        @Override
        public String modId()
        {
            return modId;
        }
    }
}
