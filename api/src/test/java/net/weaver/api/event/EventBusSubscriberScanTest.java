package net.weaver.api.event;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Regression tests for reflective subscriber scanning (generics, bridges, statics). */
class EventBusSubscriberScanTest
{
    static class TickEvent extends Event
    {
    }

    static class OtherEvent extends Event
    {
    }

    abstract static class GenericBase<E extends Event>
    {
        final List<String> calls = new ArrayList<>();

        @SubscribeEvent
        public void on(E event)
        {
            calls.add("base");
        }
    }

    static final class AnnotatedGenericOverride extends GenericBase<TickEvent>
    {
        @SubscribeEvent
        @Override
        public void on(TickEvent event)
        {
            calls.add("sub");
        }
    }

    static final class PlainGenericOverride extends GenericBase<TickEvent>
    {
        @Override
        public void on(TickEvent event)
        {
            calls.add("sub");
        }
    }

    @Test
    void annotatedGenericOverrideRegistersOnceAndOnlyForItsEventType()
    {
        EventBus bus = new EventBus("generic-override");
        AtomicInteger failures = new AtomicInteger();
        bus.setExceptionHandler((event, listener, failure) -> failures.incrementAndGet());
        AnnotatedGenericOverride subscriber = new AnnotatedGenericOverride();

        bus.register(subscriber);
        bus.post(new TickEvent());
        bus.post(new OtherEvent());

        assertEquals(List.of("sub"), subscriber.calls);
        assertEquals(0, failures.get(), "no ClassCastException from the erased base listener");
    }

    @Test
    void unannotatedGenericOverrideDisablesTheInheritedListener()
    {
        EventBus bus = new EventBus("generic-disable");
        PlainGenericOverride subscriber = new PlainGenericOverride();

        bus.register(subscriber);
        bus.post(new TickEvent());

        assertEquals(List.of(), subscriber.calls);
    }

    static final AtomicInteger STATIC_CALLS = new AtomicInteger();

    abstract static class StaticBase
    {
        @SubscribeEvent
        public static void onTick(TickEvent event)
        {
            STATIC_CALLS.incrementAndGet();
        }
    }

    static final class StaticChildA extends StaticBase
    {
    }

    static final class StaticChildB extends StaticBase
    {
    }

    @Test
    void registerClassOwnsOnlyItsOwnStaticListeners()
    {
        EventBus bus = new EventBus("static-scan");
        STATIC_CALLS.set(0);

        // The auto-subscriber scanner registers every annotated class it finds.
        bus.register(StaticBase.class);
        bus.register(StaticChildA.class);
        bus.register(StaticChildB.class);
        bus.post(new TickEvent());

        assertEquals(1, STATIC_CALLS.get());
    }

    static final class FastPathSubscriber
    {
        final List<String> calls = new ArrayList<>();

        @SubscribeEvent
        public void publicInstance(TickEvent event)
        {
            calls.add("public");
        }

        @SubscribeEvent
        private void privateInstance(TickEvent event)
        {
            calls.add("private");
        }

        @SubscribeEvent
        public static void staticListener(TickEvent event)
        {
            STATIC_CALLS.incrementAndGet();
        }
    }

    @Test
    void invokersUseTheLambdaMetafactoryFastPath() throws Exception
    {
        FastPathSubscriber subscriber = new FastPathSubscriber();
        STATIC_CALLS.set(0);
        for (String name : List.of("publicInstance", "privateInstance", "staticListener"))
        {
            java.lang.reflect.Method method =
                    FastPathSubscriber.class.getDeclaredMethod(name, TickEvent.class);
            boolean isStatic = java.lang.reflect.Modifier.isStatic(method.getModifiers());
            EventListener<Event> listener = EventBus.createInvoker(method, isStatic ? null : subscriber);

            // The metafactory spins a hidden class nested in the subscriber's class;
            // the fallback wrapper would be a lambda defined in EventBus instead.
            String implementation = listener.getClass().getName();
            org.junit.jupiter.api.Assertions.assertTrue(
                    implementation.startsWith(FastPathSubscriber.class.getName() + "$$Lambda"),
                    name + " used " + implementation);
            listener.invoke(new TickEvent());
        }

        assertEquals(List.of("public", "private"), subscriber.calls);
        assertEquals(1, STATIC_CALLS.get());
    }
}
