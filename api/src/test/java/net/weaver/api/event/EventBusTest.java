package net.weaver.api.event;

import net.weaver.api.ModContext;
import net.weaver.api.event.events.ModLifecycleEvent;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventBusTest
{
    @Test
    void priorityOrderIsStableWithinPriority()
    {
        EventBus bus = new EventBus("priority");
        List<String> order = new ArrayList<>();
        bus.addListener(Event.class, event -> order.add("normal-1"));
        bus.addListener(Event.class, event -> order.add("monitor"), EventPriority.MONITOR);
        bus.addListener(Event.class, event -> order.add("highest"), EventPriority.HIGHEST);
        bus.addListener(Event.class, event -> order.add("normal-2"));
        bus.addListener(Event.class, event -> order.add("lowest"), EventPriority.LOWEST);
        bus.addListener(Event.class, event -> order.add("high"), EventPriority.HIGH);
        bus.addListener(Event.class, event -> order.add("low"), EventPriority.LOW);

        bus.post(new BasicEvent());

        assertEquals(List.of("highest", "high", "normal-1", "normal-2", "low", "lowest", "monitor"),
                order);
    }

    @Test
    void superclassListenersReceiveSubclassEventsOnly()
    {
        EventBus bus = new EventBus("hierarchy");
        AtomicInteger base = new AtomicInteger();
        AtomicInteger sub = new AtomicInteger();
        bus.addListener(BasicEvent.class, event -> base.incrementAndGet());
        bus.addListener(SubEvent.class, event -> sub.incrementAndGet());

        bus.post(new SubEvent());

        assertEquals(1, base.get());
        assertEquals(1, sub.get());

        bus.post(new BasicEvent());

        assertEquals(2, base.get());
        assertEquals(1, sub.get());
    }

    @Test
    void canceledEventsOnlyReachReceiveCanceledListeners()
    {
        EventBus bus = new EventBus("cancel");
        List<String> seen = new ArrayList<>();
        bus.addListener(CancelableEvent.class, event -> event.setCanceled(true), EventPriority.HIGHEST);
        bus.addListener(CancelableEvent.class, event -> seen.add("normal"));
        bus.addListener(CancelableEvent.class, event -> seen.add("aware"), EventPriority.LOWEST, true);
        CancelableEvent event = new CancelableEvent();

        assertFalse(bus.post(event));

        assertTrue(event.isCanceled());
        assertEquals(List.of("aware"), seen);
    }

    @Test
    void receiveCanceledListenerCanUncancelForLaterListeners()
    {
        EventBus bus = new EventBus("uncancel");
        List<String> seen = new ArrayList<>();
        bus.addListener(CancelableEvent.class, event -> event.setCanceled(true), EventPriority.HIGHEST);
        bus.addListener(CancelableEvent.class, event -> event.setCanceled(false),
                EventPriority.NORMAL, true);
        bus.addListener(CancelableEvent.class, event -> seen.add("after"), EventPriority.LOW);
        CancelableEvent event = new CancelableEvent();

        assertTrue(bus.post(event));

        assertEquals(List.of("after"), seen);
    }

    @Test
    void listenerFailureDoesNotStopDispatchAndReachesHandler()
    {
        EventBus bus = new EventBus("exceptions");
        List<String> seen = new ArrayList<>();
        AtomicReference<Event> handledEvent = new AtomicReference<>();
        AtomicReference<EventListener<?>> handledListener = new AtomicReference<>();
        AtomicReference<Throwable> handledFailure = new AtomicReference<>();
        RuntimeException boom = new RuntimeException("boom");
        bus.setExceptionHandler((event, listener, failure) ->
        {
            handledEvent.set(event);
            handledListener.set(listener);
            handledFailure.set(failure);
        });
        bus.addListener(BasicEvent.class, event -> {
            throw boom;
        }, EventPriority.HIGHEST);
        bus.addListener(BasicEvent.class, event -> seen.add("after"));
        BasicEvent event = new BasicEvent();

        assertTrue(bus.post(event));

        assertEquals(List.of("after"), seen);
        assertSame(event, handledEvent.get());
        assertSame(boom, handledFailure.get());
        assertTrue(handledListener.get() != null);
        assertEquals(1, bus.stats().exceptions());
    }

    @Test
    void failingExceptionHandlerDoesNotStopDispatch()
    {
        EventBus bus = new EventBus("handler");
        List<String> seen = new ArrayList<>();
        bus.setExceptionHandler((event, listener, failure) -> {
            throw new IllegalStateException("handler");
        });
        bus.addListener(BasicEvent.class, event -> {
            throw new IllegalStateException("listener");
        }, EventPriority.HIGHEST);
        bus.addListener(BasicEvent.class, event -> seen.add("after"));

        bus.post(new BasicEvent());

        assertEquals(List.of("after"), seen);
        assertEquals(1, bus.stats().exceptions());
    }

    @Test
    void registrationDuringDispatchAppliesToLaterPosts()
    {
        EventBus bus = new EventBus("registering");
        List<String> seen = new ArrayList<>();
        AtomicBoolean registered = new AtomicBoolean();
        bus.addListener(BasicEvent.class, event -> {
            seen.add("first");
            if (registered.compareAndSet(false, true))
            {
                bus.addListener(BasicEvent.class, later -> seen.add("second"));
            }
        }, EventPriority.HIGHEST);

        bus.post(new BasicEvent());
        assertEquals(List.of("first"), seen);

        bus.post(new BasicEvent());
        assertEquals(List.of("first", "first", "second"), seen);
    }

    @Test
    void removalDuringDispatchKeepsCurrentSnapshotThenStopsDelivery()
    {
        EventBus bus = new EventBus("removing");
        List<String> seen = new ArrayList<>();
        AtomicReference<ListenerHandle> second = new AtomicReference<>();
        AtomicBoolean removed = new AtomicBoolean();
        bus.addListener(BasicEvent.class, event -> {
            seen.add("first");
            if (removed.compareAndSet(false, true))
            {
                second.get().unregister();
            }
        }, EventPriority.HIGHEST);
        second.set(bus.addListener(BasicEvent.class, event -> seen.add("second")));

        bus.post(new BasicEvent());
        assertEquals(List.of("first", "second"), seen);
        assertFalse(second.get().isActive());

        bus.post(new BasicEvent());
        assertEquals(List.of("first", "second", "first"), seen);
    }

    @Test
    void unregisterByOwnerUsesReferenceIdentity()
    {
        EventBus bus = new EventBus("owners");
        EqualOwner owner = new EqualOwner();
        EqualOwner equalButDifferent = new EqualOwner();
        AtomicInteger ownerCalls = new AtomicInteger();
        AtomicInteger differentCalls = new AtomicInteger();
        bus.addListener(Event.class, owner, event -> ownerCalls.incrementAndGet(),
                EventPriority.NORMAL, false);
        bus.addListener(Event.class, equalButDifferent, event -> differentCalls.incrementAndGet(),
                EventPriority.NORMAL, false);

        assertEquals(1, bus.unregister(equalButDifferent));
        bus.post(new BasicEvent());

        assertEquals(1, ownerCalls.get());
        assertEquals(0, differentCalls.get());
        assertEquals(1, bus.listenerCount());
        assertEquals(0, bus.unregister(equalButDifferent));
    }

    @Test
    void ownerUnregistrationDeactivatesHandles()
    {
        EventBus bus = new EventBus("owner-scan");
        MultiSubscriber subscriber = new MultiSubscriber();
        List<ListenerHandle> handles = bus.register(subscriber);
        assertEquals(2, handles.size());
        assertTrue(handles.stream().allMatch(ListenerHandle::isActive));

        assertEquals(2, bus.unregister(subscriber));

        assertTrue(handles.stream().noneMatch(ListenerHandle::isActive));
        assertEquals(0, bus.listenerCount());
    }

    @Test
    void registerScansPrivateInstanceAndStaticMethods()
    {
        EventBus bus = new EventBus("reflection");
        InstanceSubscriber instance = new InstanceSubscriber();
        StaticSubscriber.CALLS.set(0);

        List<ListenerHandle> instanceHandles = bus.register(instance);
        List<ListenerHandle> staticHandles = bus.register(StaticSubscriber.class);

        assertEquals(1, instanceHandles.size());
        assertEquals(1, staticHandles.size());
        assertSame(instance, instanceHandles.get(0).owner());
        assertSame(StaticSubscriber.class, staticHandles.get(0).owner());

        bus.post(new BasicEvent());

        assertEquals(1, instance.calls);
        assertEquals(1, StaticSubscriber.CALLS.get());
    }

    @Test
    void registerScansSuperclasses()
    {
        EventBus bus = new EventBus("inherited");
        ChildSubscriber subscriber = new ChildSubscriber();

        bus.register(subscriber);
        bus.post(new BasicEvent());

        assertEquals(1, subscriber.calls);
    }

    @Test
    void overriddenSubscriberRegistersOnce()
    {
        EventBus bus = new EventBus("override");
        OverridingSubscriber subscriber = new OverridingSubscriber();

        bus.register(subscriber);
        bus.post(new BasicEvent());

        assertEquals(1, subscriber.calls);
        assertEquals(1, bus.listenerCount());
    }

    @Test
    void overrideWithoutSubscribeEventDisablesInheritedListener()
    {
        EventBus bus = new EventBus("silent-override");
        SilentOverrideSubscriber subscriber = new SilentOverrideSubscriber();

        bus.register(subscriber);
        bus.post(new BasicEvent());

        assertEquals(0, subscriber.calls);
        assertEquals(0, bus.listenerCount());
    }

    @Test
    void siblingSubscribersRegisterIndependently()
    {
        EventBus bus = new EventBus("siblings");
        LeftSiblingSubscriber left = new LeftSiblingSubscriber();
        RightSiblingSubscriber right = new RightSiblingSubscriber();

        bus.register(left);
        bus.register(right);
        bus.post(new BasicEvent());

        assertEquals(1, left.calls);
        assertEquals(1, right.calls);
        assertEquals(2, bus.listenerCount());
    }

    @Test
    void registerHonorsPriorityAndReceiveCanceled()
    {
        EventBus bus = new EventBus("annotation");
        List<String> seen = new ArrayList<>();
        AnnotatedSubscriber subscriber = new AnnotatedSubscriber(seen);

        bus.register(subscriber);
        boolean result = bus.post(new CancelableEvent());

        assertFalse(result);
        assertEquals(List.of("first", "monitor"), seen);
    }

    @Test
    void registerClassRejectsInstanceMethodsWithoutRegisteringAnything()
    {
        EventBus bus = new EventBus("static-only");

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> bus.register(InstanceSubscriber.class));

        assertTrue(failure.getMessage().contains("static"));
        assertEquals(0, bus.listenerCount());
    }

    @Test
    void nonVoidSubscriberIsRejected()
    {
        EventBus bus = new EventBus("non-void");

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> bus.register(new NonVoidSubscriber()));

        assertTrue(failure.getMessage().contains("void"));
        assertEquals(0, bus.listenerCount());
    }

    @Test
    void unregisterHandleDeactivatesIt()
    {
        EventBus bus = new EventBus("handle");
        AtomicInteger calls = new AtomicInteger();
        ListenerHandle handle = bus.addListener(BasicEvent.class, event -> calls.incrementAndGet());

        assertTrue(handle.isActive());
        assertTrue(bus.unregister(handle));
        assertFalse(handle.isActive());
        assertFalse(bus.unregister(handle));

        bus.post(new BasicEvent());

        assertEquals(0, calls.get());
    }

    @Test
    void childBusDispatchesToParentButNotTheReverse()
    {
        EventBus parent = new EventBus("parent");
        EventBus child = new EventBus("child", parent);
        List<String> seen = new ArrayList<>();
        parent.addListener(BasicEvent.class, event -> seen.add("parent"));
        child.addListener(BasicEvent.class, event -> seen.add("child"));

        BasicEvent childEvent = new BasicEvent();
        child.post(childEvent);

        assertEquals(List.of("child", "parent"), seen);
        assertSame(child, childEvent.getBus());

        seen.clear();
        parent.post(new BasicEvent());

        assertEquals(List.of("parent"), seen);
    }

    @Test
    void statsTrackPostsInvocationsCancellationsAndExceptions()
    {
        EventBus bus = new EventBus("stats");
        bus.addListener(CancelableEvent.class, event -> {
        });
        bus.addListener(CancelableEvent.class, event -> {
            throw new IllegalStateException("bad");
        }, EventPriority.HIGH);
        bus.addListener(CancelableEvent.class, event -> event.setCanceled(true),
                EventPriority.LOWEST, true);

        bus.post(new CancelableEvent());

        EventBusStats stats = bus.stats();
        assertEquals(1, stats.posts());
        assertEquals(3, stats.listenerInvocations());
        assertEquals(1, stats.canceledPosts());
        assertEquals(1, stats.exceptions());
        assertTrue(stats.totalNanos() >= 0);
        assertTrue(stats.maxNanos() >= 0);
        assertTrue(stats.maxNanos() <= stats.totalNanos());

        bus.resetStats();

        EventBusStats reset = bus.stats();
        assertEquals(0, reset.posts());
        assertEquals(0, reset.listenerInvocations());
        assertEquals(0, reset.canceledPosts());
        assertEquals(0, reset.exceptions());
        assertEquals(0, reset.totalNanos());
        assertEquals(0, reset.maxNanos());
    }

    @Test
    void postFastDispatchesLocalBusThenAncestorsInOrder()
    {
        EventBus local = EventBus.local("fast-order");
        List<String> seen = new ArrayList<>();
        ListenerHandle localHandle = local.addListener(FastOrderEvent.class,
                event -> seen.add("local"));
        ListenerHandle modHandle = EventBus.MOD.addListener(FastOrderEvent.class,
                event -> seen.add("mod"));
        ListenerHandle gameHandle = EventBus.GAME.addListener(FastOrderEvent.class,
                event -> seen.add("game"));
        try
        {
            FastOrderEvent event = new FastOrderEvent();
            assertTrue(local.postFast(event));

            assertEquals(List.of("local", "mod", "game"), seen);
            assertSame(local, event.getBus());

            new EventBus("fast-other").postFast(event);

            assertSame(local, event.getBus());
        }
        finally
        {
            localHandle.unregister();
            modHandle.unregister();
            gameHandle.unregister();
        }
    }

    @Test
    void postFastHonorsFiltersAndCancellation()
    {
        EventBus bus = new EventBus("fast-filter-cancel");
        List<String> seen = new ArrayList<>();
        bus.addListener(CancelableEvent.class, event -> event.setCanceled(true),
                EventPriority.HIGHEST);
        bus.addListener(CancelableEvent.class, event -> seen.add("normal"));
        bus.addListener(CancelableEvent.class, seen, event -> seen.add("filtered"),
                EventPriority.NORMAL, true, (EventFilter) event -> false);
        bus.addListener(CancelableEvent.class, seen, event -> {
            seen.add("uncancel");
            event.setCanceled(false);
        }, EventPriority.LOW, true, EventFilter.all());
        bus.addListener(CancelableEvent.class, event -> seen.add("after"),
                EventPriority.LOWEST);

        assertTrue(bus.postFast(new CancelableEvent()));

        assertEquals(List.of("uncancel", "after"), seen);
    }

    @Test
    void postFastIsolatesListenerExceptions()
    {
        EventBus bus = new EventBus("fast-exceptions");
        List<String> seen = new ArrayList<>();
        AtomicReference<Throwable> handled = new AtomicReference<>();
        RuntimeException boom = new RuntimeException("boom");
        bus.setExceptionHandler((event, listener, failure) -> handled.set(failure));
        bus.addListener(BasicEvent.class, event -> {
            throw boom;
        }, EventPriority.HIGHEST);
        bus.addListener(BasicEvent.class, event -> seen.add("after"));

        assertTrue(bus.postFast(new BasicEvent()));

        assertEquals(List.of("after"), seen);
        assertSame(boom, handled.get());
    }

    @Test
    void postFastLeavesEveryStatUnchanged()
    {
        EventBus parent = new EventBus("fast-stats-parent");
        EventBus bus = new EventBus("fast-stats", parent);
        bus.addListener(CancelableEvent.class, event -> {
        });
        parent.addListener(CancelableEvent.class, event -> {
            throw new IllegalStateException("bad");
        }, EventPriority.NORMAL, true);
        bus.addListener(CancelableEvent.class, event -> event.setCanceled(true),
                EventPriority.LOWEST, true);

        assertFalse(bus.post(new CancelableEvent()));
        EventBusStats busBefore = bus.stats();
        EventBusStats parentBefore = parent.stats();
        assertEquals(1, busBefore.canceledPosts());
        assertEquals(1, parentBefore.exceptions());

        assertFalse(bus.postFast(new CancelableEvent()));

        assertStatsEqual(busBefore, bus.stats());
        assertStatsEqual(parentBefore, parent.stats());
    }

    @Test
    void postFastReturnsCancellationState()
    {
        EventBus canceled = new EventBus("fast-return-canceled");
        canceled.addListener(CancelableEvent.class, event -> event.setCanceled(true),
                EventPriority.HIGHEST);
        EventBus open = new EventBus("fast-return-open");

        assertFalse(canceled.postFast(new CancelableEvent()));
        assertTrue(open.postFast(new BasicEvent()));
    }

    @Test
    void hasListenersAndListenerCountTrackRegistrations()
    {
        EventBus bus = new EventBus("counts");
        assertFalse(bus.hasListeners(Event.class));
        assertEquals(0, bus.listenerCount());

        ListenerHandle handle = bus.addListener(BasicEvent.class, event -> {
        });

        assertTrue(bus.hasListeners(BasicEvent.class));
        assertTrue(bus.hasListeners(SubEvent.class));
        assertFalse(bus.hasListeners(CancelableEvent.class));
        assertEquals(1, bus.listenerCount());

        assertTrue(bus.unregister(handle));

        assertFalse(bus.hasListeners(BasicEvent.class));
        assertEquals(0, bus.listenerCount());
    }

    @Test
    void firePostsAndReturnsSameEvent()
    {
        EventBus bus = new EventBus("fire");
        AtomicInteger calls = new AtomicInteger();
        bus.addListener(BasicEvent.class, event -> calls.incrementAndGet());
        BasicEvent event = new BasicEvent();

        assertSame(event, bus.fire(event));
        assertSame(bus, event.getBus());
        assertEquals(1, calls.get());
    }

    @Test
    void gameAndModBusesAreLinked()
    {
        assertSame(EventBus.GAME, EventBus.global());
        assertSame(EventBus.GAME, EventBus.MOD.parent());
        assertNull(EventBus.GAME.parent());
        assertSame(EventBus.GAME, EventBus.GAME);
        assertSame(EventBus.MOD, EventBus.MOD);
    }

    @Test
    void localBusDispatchOrderIsLocalThenModThenGame()
    {
        EventBus local = EventBus.local("order");
        List<String> seen = new ArrayList<>();
        ListenerHandle localHandle = local.addListener(LocalOrderEvent.class,
                event -> seen.add("local"));
        ListenerHandle modHandle = EventBus.MOD.addListener(LocalOrderEvent.class,
                event -> seen.add("mod"));
        ListenerHandle gameHandle = EventBus.GAME.addListener(LocalOrderEvent.class,
                event -> seen.add("game"));
        try
        {
            LocalOrderEvent event = new LocalOrderEvent();
            assertTrue(local.post(event));

            assertEquals(List.of("local", "mod", "game"), seen);
            assertSame(local, event.getBus());
            assertEquals("mod:order", local.name());
            assertSame(EventBus.MOD, local.parent());
        }
        finally
        {
            localHandle.unregister();
            modHandle.unregister();
            gameHandle.unregister();
        }
    }

    @Test
    void localBusListenersAreIsolatedFromOtherLocalBuses()
    {
        EventBus first = EventBus.local("first");
        EventBus second = EventBus.local("second");
        AtomicInteger firstCalls = new AtomicInteger();
        AtomicInteger secondCalls = new AtomicInteger();
        first.addListener(IsolatedEvent.class, event -> firstCalls.incrementAndGet());
        second.addListener(IsolatedEvent.class, event -> secondCalls.incrementAndGet());

        first.post(new IsolatedEvent());

        assertEquals(1, firstCalls.get());
        assertEquals(0, secondCalls.get());
    }

    @Test
    void modBusSeesEventsFromEveryLocalBus()
    {
        EventBus first = EventBus.local("multi-a");
        EventBus second = EventBus.local("multi-b");
        AtomicInteger modCalls = new AtomicInteger();
        ListenerHandle handle = EventBus.MOD.addListener(MultiLocalEvent.class,
                event -> modCalls.incrementAndGet());
        try
        {
            first.post(new MultiLocalEvent());
            second.post(new MultiLocalEvent());
        }
        finally
        {
            handle.unregister();
        }

        assertEquals(2, modCalls.get());
    }

    @Test
    void lifecycleEventPostedOnLocalBusReportsLocalBus()
    {
        EventBus local = EventBus.local("lifecycle");
        AtomicReference<EventBus> reported = new AtomicReference<>();
        ListenerHandle handle = local.addListener(ModLifecycleEvent.class,
                event -> reported.set(event.getBus()));
        try
        {
            ModLifecycleEvent event = new ModLifecycleEvent(fakeContext("lifecycle"),
                    ModLifecycleEvent.Phase.INITIALIZE);
            local.post(event);

            assertSame(local, event.getBus());
            assertSame(local, reported.get());
        }
        finally
        {
            handle.unregister();
        }
    }

    @Test
    void listenerCountExcludesParentsAndChildren()
    {
        EventBus parent = new EventBus("count-parent");
        EventBus child = new EventBus("count-child", parent);
        EventBus sibling = new EventBus("count-sibling", parent);
        child.addListener(CountEvent.class, event -> {
        });
        sibling.addListener(CountEvent.class, event -> {
        });
        parent.addListener(CountEvent.class, event -> {
        });

        assertEquals(1, child.listenerCount());
        assertEquals(1, sibling.listenerCount());
        assertEquals(1, parent.listenerCount());
        assertTrue(child.hasListeners(CountEvent.class));
        assertTrue(parent.hasListeners(CountEvent.class));
    }

    @Test
    void parentPostDoesNotReachChild()
    {
        EventBus parent = new EventBus("route-parent");
        EventBus child = new EventBus("route-child", parent);
        List<String> seen = new ArrayList<>();
        parent.addListener(RouteEvent.class, event -> seen.add("parent"));
        child.addListener(RouteEvent.class, event -> seen.add("child"));

        parent.post(new RouteEvent());

        assertEquals(List.of("parent"), seen);
    }

    @Test
    void reentrantPostRunsNestedDispatchInline()
    {
        EventBus bus = new EventBus("reentrant");
        List<String> seen = new ArrayList<>();
        bus.addListener(OuterEvent.class, event ->
        {
            seen.add("outer-before");
            bus.post(new InnerEvent());
            seen.add("outer-after");
        });
        bus.addListener(InnerEvent.class, event -> seen.add("inner"));

        bus.post(new OuterEvent());

        assertEquals(List.of("outer-before", "inner", "outer-after"), seen);
    }

    @Test
    void concurrentRegistrationAndPostingIsSafe() throws Exception
    {
        EventBus bus = new EventBus("concurrent");
        AtomicInteger calls = new AtomicInteger();
        ListenerHandle stable = bus.addListener(ConcurrentEvent.class,
                event -> calls.incrementAndGet());
        int posters = 4;
        int postsPerThread = 10_000;
        int churnIterations = 10_000;
        ExecutorService executor = Executors.newFixedThreadPool(posters + 1);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try
        {
            for (int poster = 0; poster < posters; poster++)
            {
                futures.add(executor.submit(() ->
                {
                    start.await();
                    for (int post = 0; post < postsPerThread; post++)
                    {
                        bus.post(new ConcurrentEvent());
                    }
                    return null;
                }));
            }
            futures.add(executor.submit(() ->
            {
                start.await();
                for (int iteration = 0; iteration < churnIterations; iteration++)
                {
                    ListenerHandle handle = bus.addListener(ConcurrentEvent.class, event -> {
                    });
                    handle.unregister();
                }
                return null;
            }));
            start.countDown();
            for (Future<?> future : futures)
            {
                future.get(30, TimeUnit.SECONDS);
            }
        }
        finally
        {
            executor.shutdownNow();
            stable.unregister();
        }

        assertEquals(posters * postsPerThread, calls.get());
    }

    @Test
    void filteredOutListenerIsNotInvokedOrCounted()
    {
        EventBus bus = new EventBus("filter-stats");
        AtomicInteger filteredCalls = new AtomicInteger();
        AtomicInteger acceptedCalls = new AtomicInteger();
        bus.addListener(FilteredEvent.class, event -> filteredCalls.incrementAndGet(),
                EventFilter.modId("other"));
        bus.addListener(FilteredEvent.class, event -> acceptedCalls.incrementAndGet(),
                EventFilter.all());
        bus.resetStats();

        bus.post(new FilteredEvent());

        assertEquals(0, filteredCalls.get());
        assertEquals(1, acceptedCalls.get());
        assertEquals(1, bus.stats().posts());
        assertEquals(1, bus.stats().listenerInvocations());
    }

    @Test
    void filterRunsAfterCancellationCheck()
    {
        EventBus bus = new EventBus("filter-cancel");
        List<String> seen = new ArrayList<>();
        bus.addListener(CancelableEvent.class, event -> event.setCanceled(true),
                EventPriority.HIGHEST);
        bus.addListener(CancelableEvent.class, seen, event -> seen.add("rejected"),
                EventPriority.NORMAL, true, EventFilter.modId("other"));
        bus.addListener(CancelableEvent.class, event -> seen.add("skipped"), EventPriority.LOW);
        bus.addListener(CancelableEvent.class, seen, event -> seen.add("accepted"),
                EventPriority.LOWEST, true, EventFilter.all());
        bus.resetStats();

        assertFalse(bus.post(new CancelableEvent()));

        assertEquals(List.of("accepted"), seen);
        assertEquals(2, bus.stats().listenerInvocations());
    }

    @Test
    void nullFilterAtRegistrationBehavesAsAll()
    {
        EventBus bus = new EventBus("null-filter");
        AtomicInteger calls = new AtomicInteger();
        bus.addListener(FilteredEvent.class, event -> calls.incrementAndGet(), (EventFilter) null);

        bus.post(new FilteredEvent());

        assertEquals(1, calls.get());
    }

    @Test
    void registerObjectWithFilterAppliesToScannedMethods()
    {
        EventBus bus = new EventBus("register-filter-instance");
        FilteredInstanceSubscriber subscriber = new FilteredInstanceSubscriber();
        List<ListenerHandle> rejected = bus.register(subscriber, event -> false);
        assertEquals(1, rejected.size());
        assertEquals(1, bus.listenerCount());

        bus.post(new BasicEvent());
        assertEquals(0, subscriber.calls);

        assertEquals(1, bus.unregister(subscriber));

        List<ListenerHandle> accepted = bus.register(subscriber, EventFilter.all());
        assertEquals(1, accepted.size());
        bus.post(new BasicEvent());
        assertEquals(1, subscriber.calls);
    }

    @Test
    void registerClassWithFilterAppliesToScannedStaticMethods()
    {
        EventBus bus = new EventBus("register-filter-static");
        FilteredStaticSubscriber.CALLS.set(0);
        List<ListenerHandle> rejected = bus.register(FilteredStaticSubscriber.class, event -> false);
        assertEquals(1, rejected.size());
        assertEquals(1, bus.listenerCount());

        bus.post(new BasicEvent());
        assertEquals(0, FilteredStaticSubscriber.CALLS.get());

        assertEquals(1, bus.unregister(FilteredStaticSubscriber.class));

        List<ListenerHandle> accepted = bus.register(FilteredStaticSubscriber.class,
                EventFilter.all());
        assertEquals(1, accepted.size());
        bus.post(new BasicEvent());
        assertEquals(1, FilteredStaticSubscriber.CALLS.get());
    }

    private static void assertStatsEqual(EventBusStats expected, EventBusStats actual)
    {
        assertEquals(expected.posts(), actual.posts());
        assertEquals(expected.listenerInvocations(), actual.listenerInvocations());
        assertEquals(expected.canceledPosts(), actual.canceledPosts());
        assertEquals(expected.exceptions(), actual.exceptions());
        assertEquals(expected.totalNanos(), actual.totalNanos());
        assertEquals(expected.maxNanos(), actual.maxNanos());
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

    private static class BasicEvent extends Event
    {
    }

    private static final class LocalOrderEvent extends Event
    {
    }

    private static final class FastOrderEvent extends Event
    {
    }

    private static final class IsolatedEvent extends Event
    {
    }

    private static final class MultiLocalEvent extends Event
    {
    }

    private static final class CountEvent extends Event
    {
    }

    private static final class RouteEvent extends Event
    {
    }

    private static final class OuterEvent extends Event
    {
    }

    private static final class InnerEvent extends Event
    {
    }

    private static final class ConcurrentEvent extends Event
    {
    }

    private static final class FilteredEvent extends Event
    {
    }

    private static final class FilteredInstanceSubscriber
    {
        private int calls;

        @SubscribeEvent
        private void onBasic(BasicEvent event)
        {
            calls++;
        }
    }

    private static final class FilteredStaticSubscriber
    {
        private static final AtomicInteger CALLS = new AtomicInteger();

        @SubscribeEvent
        private static void onBasic(BasicEvent event)
        {
            CALLS.incrementAndGet();
        }
    }

    private static final class SubEvent extends BasicEvent
    {
    }

    private static final class CancelableEvent extends Event
    {
        private CancelableEvent()
        {
            super(true);
        }
    }

    private static final class EqualOwner
    {
        @Override
        public boolean equals(Object other)
        {
            return other instanceof EqualOwner;
        }

        @Override
        public int hashCode()
        {
            return 1;
        }
    }

    private static final class InstanceSubscriber
    {
        private int calls;

        @SubscribeEvent
        private void onBasic(BasicEvent event)
        {
            calls++;
        }
    }

    private static final class StaticSubscriber
    {
        private static final AtomicInteger CALLS = new AtomicInteger();

        @SubscribeEvent
        private static void onBasic(BasicEvent event)
        {
            CALLS.incrementAndGet();
        }
    }

    private static class ParentSubscriber
    {
        int calls;

        @SubscribeEvent
        private void onBasic(BasicEvent event)
        {
            calls++;
        }
    }

    private static final class ChildSubscriber extends ParentSubscriber
    {
    }

    private static class OverriddenParentSubscriber
    {
        int calls;

        @SubscribeEvent
        void onBasic(BasicEvent event)
        {
            calls++;
        }
    }

    private static final class OverridingSubscriber extends OverriddenParentSubscriber
    {
        @Override
        @SubscribeEvent
        void onBasic(BasicEvent event)
        {
            calls++;
        }
    }

    private static class SilentOverrideParentSubscriber
    {
        int calls;

        @SubscribeEvent
        void onBasic(BasicEvent event)
        {
            calls++;
        }
    }

    private static final class SilentOverrideSubscriber extends SilentOverrideParentSubscriber
    {
        @Override
        void onBasic(BasicEvent event)
        {
            calls++;
        }
    }

    private abstract static class SiblingParentSubscriber
    {
        int calls;
    }

    private static final class LeftSiblingSubscriber extends SiblingParentSubscriber
    {
        @SubscribeEvent
        void onBasic(BasicEvent event)
        {
            calls++;
        }
    }

    private static final class RightSiblingSubscriber extends SiblingParentSubscriber
    {
        @SubscribeEvent
        void onBasic(BasicEvent event)
        {
            calls++;
        }
    }

    private static final class MultiSubscriber
    {
        @SubscribeEvent
        private void onBasic(BasicEvent event)
        {
        }

        @SubscribeEvent
        private void onSub(SubEvent event)
        {
        }
    }

    private static final class NonVoidSubscriber
    {
        @SubscribeEvent
        private int onBasic(BasicEvent event)
        {
            return 1;
        }
    }

    private static final class AnnotatedSubscriber
    {
        private final List<String> seen;

        private AnnotatedSubscriber(List<String> seen)
        {
            this.seen = seen;
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        private void first(CancelableEvent event)
        {
            seen.add("first");
            event.setCanceled(true);
        }

        @SubscribeEvent
        private void skipped(CancelableEvent event)
        {
            seen.add("skipped");
        }

        @SubscribeEvent(priority = EventPriority.MONITOR, receiveCanceled = true)
        private void monitor(CancelableEvent event)
        {
            seen.add("monitor");
        }
    }
}
