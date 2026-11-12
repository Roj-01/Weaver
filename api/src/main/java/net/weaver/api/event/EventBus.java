package net.weaver.api.event;

import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A synchronous, in-process event bus.
 *
 * <p>Buses form a fixed parent chain. Posting on a bus dispatches to that bus
 * first and then to each ancestor in turn; posting on a parent never reaches
 * its children. The global {@link #GAME} bus is the root, {@link #MOD} is its
 * child, and {@link #local(String)} creates a bus private to one mod.
 *
 * <p>Dispatch is thread-safe. Listener snapshots are immutable arrays cached
 * per event class, so registration or removal never corrupts an in-flight
 * post.
 */
public final class EventBus
{
    public static final EventBus GAME = new EventBus("game");
    public static final EventBus MOD = new EventBus("mod", GAME);

    private static final Comparator<Registration> ORDER = Comparator
            .comparingInt((Registration registration) -> registration.priority.ordinal())
            .thenComparingLong(registration -> registration.sequence);

    private final String name;
    private final EventBus parent;
    private final EventBus[] ancestry;
    private final Map<Class<? extends Event>, List<Registration>> registrations = new HashMap<>();
    private final Map<Class<? extends Event>, DispatchSnapshot> snapshots = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Stats stats = new Stats();
    private volatile EventExceptionHandler exceptionHandler = EventBus::reportFailure;
    private long sequence;

    /** The default handler: reports a failing listener, throttled so a listener failing every tick cannot flood the log. */
    private static void reportFailure(Event event, Object listener, Throwable failure)
    {
        FailureThrottle.Decision decision = FailureThrottle.record(
                event.getName() + "|" + listener + "|" + failure.getClass().getName());
        if (!decision.report())
        {
            return;
        }
        System.err.println("[weaver] EVENT LISTENER FAILED event=" + event.getName()
                + " listener=" + listener + " reason=" + failure + decision.suffix());
        if (decision.first())
        {
            failure.printStackTrace();
        }
    }

    public EventBus(String name)
    {
        this(name, null);
    }

    public EventBus(String name, EventBus parent)
    {
        this.name = Objects.requireNonNull(name, "name");
        this.parent = parent;
        EventBus[] chain = new EventBus[parent == null ? 1 : parent.ancestry.length + 1];
        chain[0] = this;
        if (parent != null)
        {
            System.arraycopy(parent.ancestry, 0, chain, 1, parent.ancestry.length);
        }
        this.ancestry = chain;
    }

    public static EventBus global()
    {
        return GAME;
    }

    /**
     * Creates a bus private to one mod, named {@code mod:<modId>} and parented
     * to {@link #MOD}.
     *
     * <p>Events posted on the returned bus reach local listeners first, then
     * {@link #MOD} listeners, then {@link #GAME} listeners.
     *
     * @param modId the mod id that owns this bus, not {@code null}
     * @return a fresh local bus; each call returns a new bus
     */
    public static EventBus local(String modId)
    {
        Objects.requireNonNull(modId, "modId");
        return new EventBus("mod:" + modId, MOD);
    }

    public String name()
    {
        return name;
    }

    public EventBus parent()
    {
        return parent;
    }

    public <T extends Event> ListenerHandle addListener(Class<T> eventType, EventListener<? super T> listener)
    {
        return addListener(eventType, listener, EventPriority.NORMAL);
    }

    public <T extends Event> ListenerHandle addListener(Class<T> eventType, EventListener<? super T> listener,
                                                        EventPriority priority)
    {
        return addListener(eventType, listener, priority, false);
    }

    public <T extends Event> ListenerHandle addListener(Class<T> eventType, EventListener<? super T> listener,
                                                        EventPriority priority, boolean receiveCanceled)
    {
        return addListener(eventType, listener, listener, priority, receiveCanceled, EventFilter.all());
    }

    /**
     * Registers a listener that only runs when {@code filter} accepts the
     * event. A {@code null} filter is treated as {@link EventFilter#all()}.
     *
     * @param eventType the event type to listen for
     * @param listener  the callback to invoke
     * @param filter    the dispatch-time filter, or {@code null} for all
     * @param <T>       the event type
     * @return a handle that removes the registration
     */
    public <T extends Event> ListenerHandle addListener(Class<T> eventType, EventListener<? super T> listener,
                                                        EventFilter filter)
    {
        return addListener(eventType, listener, listener, EventPriority.NORMAL, false, filter);
    }

    public <T extends Event> ListenerHandle addListener(Class<T> eventType, Object owner,
                                                        EventListener<? super T> listener,
                                                        EventPriority priority, boolean receiveCanceled)
    {
        return addListener(eventType, owner, listener, priority, receiveCanceled, EventFilter.all());
    }

    /**
     * Registers a listener with an explicit owner and dispatch-time filter.
     * A {@code null} filter is treated as {@link EventFilter#all()}.
     *
     * @param eventType       the event type to listen for
     * @param owner           the owner used by {@link #unregister(Object)}
     * @param listener        the callback to invoke
     * @param priority        execution order
     * @param receiveCanceled whether the listener still runs after cancellation
     * @param filter          the dispatch-time filter, or {@code null} for all
     * @param <T>             the event type
     * @return a handle that removes the registration
     */
    public <T extends Event> ListenerHandle addListener(Class<T> eventType, Object owner,
                                                        EventListener<? super T> listener,
                                                        EventPriority priority, boolean receiveCanceled,
                                                        EventFilter filter)
    {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(listener, "listener");
        Objects.requireNonNull(priority, "priority");
        EventFilter effectiveFilter = filter == null ? EventFilter.all() : filter;
        lock.writeLock().lock();
        try
        {
            Registration registration = new Registration(eventType, owner, listener, priority,
                    receiveCanceled, effectiveFilter, sequence++);
            registrations.computeIfAbsent(eventType, key -> new ArrayList<>()).add(registration);
            ListenerHandle handle = new ListenerHandle(this, eventType, owner,
                    () -> removeRegistration(registration));
            registration.handle = handle;
            snapshots.clear();
            return handle;
        }
        finally
        {
            lock.writeLock().unlock();
        }
    }

    public List<ListenerHandle> register(Object target)
    {
        return register(target, EventFilter.all());
    }

    /**
     * Scans {@code target} for {@link SubscribeEvent} methods and registers
     * each one with the supplied filter. A {@code null} filter is treated as
     * {@link EventFilter#all()}.
     *
     * <p>If any annotated method cannot be turned into an invoker, nothing is
     * registered and {@link IllegalArgumentException} is thrown.
     *
     * @param target the object whose annotated methods are registered
     * @param filter the filter applied to every discovered listener
     * @return one handle per registered method
     */
    public List<ListenerHandle> register(Object target, EventFilter filter)
    {
        Objects.requireNonNull(target, "target");
        EventFilter effectiveFilter = filter == null ? EventFilter.all() : filter;
        List<Method> methods = subscriberMethods(target.getClass(), false);
        return registerMethods(methods, target, target, effectiveFilter);
    }

    public List<ListenerHandle> register(Class<?> target)
    {
        return register(target, EventFilter.all());
    }

    /**
     * Scans a class for static {@link SubscribeEvent} methods and registers
     * each one with the supplied filter. A {@code null} filter is treated as
     * {@link EventFilter#all()}.
     *
     * @param target the class whose static annotated methods are registered
     * @param filter the filter applied to every discovered listener
     * @return one handle per registered method
     * @throws IllegalArgumentException if the class declares an annotated
     *                                  instance method or an invoker cannot be
     *                                  created
     */
    public List<ListenerHandle> register(Class<?> target, EventFilter filter)
    {
        Objects.requireNonNull(target, "target");
        EventFilter effectiveFilter = filter == null ? EventFilter.all() : filter;
        List<Method> methods = subscriberMethods(target, true);
        return registerMethods(methods, null, target, effectiveFilter);
    }

    public boolean unregister(ListenerHandle handle)
    {
        Objects.requireNonNull(handle, "handle");
        if (handle.bus() != this || !handle.isActive())
        {
            return false;
        }
        handle.unregister();
        return true;
    }

    public int unregister(Object owner)
    {
        Objects.requireNonNull(owner, "owner");
        List<ListenerHandle> removed = new ArrayList<>();
        lock.writeLock().lock();
        try
        {
            for (List<Registration> listeners : registrations.values())
            {
                Iterator<Registration> iterator = listeners.iterator();
                while (iterator.hasNext())
                {
                    Registration registration = iterator.next();
                    if (registration.owner == owner)
                    {
                        iterator.remove();
                        removed.add(registration.handle);
                    }
                }
            }
            if (removed.isEmpty())
            {
                return 0;
            }
            registrations.entrySet().removeIf(entry -> entry.getValue().isEmpty());
            snapshots.clear();
        }
        finally
        {
            lock.writeLock().unlock();
        }
        for (ListenerHandle handle : removed)
        {
            handle.unregister();
        }
        return removed.size();
    }

    public boolean post(Event event)
    {
        return post(event, true);
    }

    /**
     * Posts {@code event} without recording any {@link EventBusStats}.
     *
     * <p>Dispatch semantics are identical to {@link #post(Event)}: the event
     * bus is assigned on first post, the same ancestor chain is walked in the
     * same order with the same immutable per-event snapshots, cancellation and
     * filters behave the same way, and listener failures reach the same
     * {@link EventExceptionHandler} without escaping. The return value is
     * {@code !event.isCanceled()}.
     *
     * <p>Every statistic is skipped, on the posting bus and on every ancestor:
     * {@code posts()}, {@code listenerInvocations()}, {@code canceledPosts()},
     * {@code exceptions()}, {@code totalNanos()}, and {@code maxNanos()} are
     * untouched. {@link #stats()} and {@link #resetStats()} therefore continue
     * to describe only {@link #post(Event)} traffic.
     *
     * <p>Use this for high-frequency internal events where per-post
     * statistics are not wanted; {@link #post(Event)} remains the default.
     * Steady-state dispatch allocates nothing.
     *
     * @param event the event to dispatch, not {@code null}
     * @return {@code true} when the event is not canceled after dispatch
     */
    public boolean postFast(Event event)
    {
        return post(event, false);
    }

    private boolean post(Event event, boolean recordStats)
    {
        Objects.requireNonNull(event, "event");
        event.setBus(this);
        long started = recordStats ? System.nanoTime() : 0L;
        for (EventBus bus : ancestry)
        {
            bus.dispatch(bus.snapshotForPost(event.getClass()), event, recordStats);
        }
        if (recordStats)
        {
            stats.recordPost(System.nanoTime() - started, event.isCanceled());
        }
        return !event.isCanceled();
    }

    public <T extends Event> T fire(T event)
    {
        post(event);
        return event;
    }

    public boolean hasListeners(Class<? extends Event> eventType)
    {
        Objects.requireNonNull(eventType, "eventType");
        for (EventBus bus : ancestry)
        {
            if (bus.hasLocalListeners(eventType))
            {
                return true;
            }
        }
        return false;
    }

    public int listenerCount()
    {
        lock.readLock().lock();
        try
        {
            int count = 0;
            for (List<Registration> listeners : registrations.values())
            {
                count += listeners.size();
            }
            return count;
        }
        finally
        {
            lock.readLock().unlock();
        }
    }

    public EventBus setExceptionHandler(EventExceptionHandler handler)
    {
        exceptionHandler = Objects.requireNonNull(handler, "handler");
        return this;
    }

    public EventExceptionHandler exceptionHandler()
    {
        return exceptionHandler;
    }

    public EventBusStats stats()
    {
        return stats.snapshot();
    }

    public void resetStats()
    {
        stats.reset();
    }

    private List<ListenerHandle> registerMethods(List<Method> methods, Object target, Object owner,
                                                EventFilter filter)
    {
        List<PreparedSubscriber> prepared = new ArrayList<>(methods.size());
        for (Method method : methods)
        {
            SubscribeEvent annotation = method.getAnnotation(SubscribeEvent.class);
            boolean isStatic = Modifier.isStatic(method.getModifiers());
            EventListener<Event> listener = createInvoker(method, isStatic ? null : target);
            prepared.add(new PreparedSubscriber(eventType(method), listener,
                    annotation.priority(), annotation.receiveCanceled()));
        }
        List<ListenerHandle> handles = new ArrayList<>(prepared.size());
        for (PreparedSubscriber subscriber : prepared)
        {
            handles.add(addListener(subscriber.eventType(), owner, subscriber.listener(),
                    subscriber.priority(), subscriber.receiveCanceled(), filter));
        }
        return List.copyOf(handles);
    }

    private DispatchSnapshot snapshotForPost(Class<? extends Event> eventType)
    {
        DispatchSnapshot snapshot = snapshots.get(eventType);
        if (snapshot != null)
        {
            return snapshot;
        }
        lock.readLock().lock();
        try
        {
            return snapshots.computeIfAbsent(eventType, this::buildSnapshot);
        }
        finally
        {
            lock.readLock().unlock();
        }
    }

    private void dispatch(DispatchSnapshot snapshot, Event event, boolean recordStats)
    {
        for (Registration registration : snapshot.registrations())
        {
            if (event.isCanceled() && !registration.receiveCanceled)
            {
                continue;
            }
            try
            {
                if (!registration.filter.accept(event))
                {
                    continue;
                }
            }
            catch (Throwable failure)
            {
                if (recordStats)
                {
                    stats.recordException();
                }
                notifyException(event, registration.listener, failure);
                continue;
            }
            if (recordStats)
            {
                stats.recordInvocation();
            }
            try
            {
                registration.listener.invoke(event);
            }
            catch (Throwable failure)
            {
                if (recordStats)
                {
                    stats.recordException();
                }
                notifyException(event, registration.listener, failure);
            }
        }
    }

    private void notifyException(Event event, EventListener<?> listener, Throwable failure)
    {
        try
        {
            exceptionHandler.handle(event, listener, failure);
        }
        catch (Throwable ignored)
        {
        }
    }

    private boolean hasLocalListeners(Class<? extends Event> eventType)
    {
        DispatchSnapshot snapshot = snapshots.get(eventType);
        if (snapshot != null)
        {
            return snapshot.registrations().length > 0;
        }
        lock.readLock().lock();
        try
        {
            return snapshots.computeIfAbsent(eventType, this::buildSnapshot).registrations().length > 0;
        }
        finally
        {
            lock.readLock().unlock();
        }
    }

    private DispatchSnapshot buildSnapshot(Class<? extends Event> eventType)
    {
        List<Registration> matched = new ArrayList<>();
        for (Class<?> type = eventType; type != null; type = type.getSuperclass())
        {
            List<Registration> listeners = registrations.get(type);
            if (listeners != null)
            {
                matched.addAll(listeners);
            }
        }
        matched.sort(ORDER);
        return new DispatchSnapshot(matched.toArray(new Registration[0]));
    }

    private void removeRegistration(Registration registration)
    {
        lock.writeLock().lock();
        try
        {
            List<Registration> listeners = registrations.get(registration.eventType);
            if (listeners == null)
            {
                return;
            }
            Iterator<Registration> iterator = listeners.iterator();
            while (iterator.hasNext())
            {
                if (iterator.next() == registration)
                {
                    iterator.remove();
                    if (listeners.isEmpty())
                    {
                        registrations.remove(registration.eventType);
                    }
                    snapshots.clear();
                    return;
                }
            }
        }
        finally
        {
            lock.writeLock().unlock();
        }
    }

    private static List<Method> subscriberMethods(Class<?> type, boolean requireStatic)
    {
        List<Method> methods = new ArrayList<>();
        Set<String> signatures = new HashSet<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass())
        {
            for (Method method : current.getDeclaredMethods())
            {
                if (method.isBridge() || method.isSynthetic())
                {
                    // A bridge marks its erased signature as overridden, so a generic
                    // superclass listener such as Base<E>.on(E) is not registered a
                    // second time (and for every event) through the erased signature.
                    signatures.add(signature(method));
                    continue;
                }
                if (!signatures.add(signature(method)))
                {
                    continue;
                }
                if (requireStatic && current != type && Modifier.isStatic(method.getModifiers()))
                {
                    // Static methods are not inherited. register(Class) owns only the
                    // statics declared on the class itself; otherwise every annotated
                    // subclass found by the scanner re-registers its superclass statics.
                    continue;
                }
                if (!method.isAnnotationPresent(SubscribeEvent.class))
                {
                    continue;
                }
                validateSubscriber(method);
                if (requireStatic && !Modifier.isStatic(method.getModifiers()))
                {
                    throw new IllegalArgumentException(
                            "register(Class) requires static listener methods: " + method);
                }
                makeAccessible(method);
                methods.add(method);
            }
        }
        return methods;
    }

    private static String signature(Method method)
    {
        // The return type is deliberately excluded: a covariant override must still
        // replace the inherited subscriber, and @SubscribeEvent methods must be void.
        return method.getName() + MethodType
                .methodType(void.class, method.getParameterTypes())
                .toMethodDescriptorString();
    }

    private static void validateSubscriber(Method method)
    {
        Class<?>[] parameters = method.getParameterTypes();
        if (parameters.length != 1 || !Event.class.isAssignableFrom(parameters[0]))
        {
            throw new IllegalArgumentException(
                    "@SubscribeEvent method must take exactly one Event parameter: " + method);
        }
        if (method.getReturnType() != void.class)
        {
            throw new IllegalArgumentException(
                    "@SubscribeEvent method must return void: " + method);
        }
    }

    private static void makeAccessible(Method method)
    {
        if (!method.trySetAccessible())
        {
            throw new IllegalArgumentException("Cannot access @SubscribeEvent method: " + method);
        }
    }

    @SuppressWarnings("unchecked")
    private static Class<Event> eventType(Method method)
    {
        return (Class<Event>) method.getParameterTypes()[0];
    }

    static EventListener<Event> createInvoker(Method method, Object target)
    {
        boolean isStatic = Modifier.isStatic(method.getModifiers());
        Throwable primaryFailure;
        try
        {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                    method.getDeclaringClass(), MethodHandles.lookup());
            // LambdaMetafactory needs the direct method handle: a bound or asType'd
            // handle is not direct, so revealDirect fails and every registration would
            // silently fall back to the slower invokeExact wrapper. The receiver is
            // captured through the factory type, and the instantiated type inserts the
            // cast from Event to the listener's parameter type.
            MethodHandle direct = lookup.unreflect(method);
            MethodType samType = MethodType.methodType(void.class, Event.class);
            MethodType instantiated = MethodType.methodType(void.class, method.getParameterTypes()[0]);
            MethodType factoryType = isStatic
                    ? MethodType.methodType(EventListener.class)
                    : MethodType.methodType(EventListener.class, method.getDeclaringClass());
            CallSite callSite = LambdaMetafactory.metafactory(lookup, "invoke", factoryType,
                    samType, direct, instantiated);
            Object created = isStatic
                    ? callSite.getTarget().invoke()
                    : callSite.getTarget().invoke(Objects.requireNonNull(target, "target"));
            @SuppressWarnings("unchecked")
            EventListener<Event> listener = (EventListener<Event>) created;
            return listener;
        }
        catch (Throwable failure)
        {
            primaryFailure = failure;
        }
        try
        {
            MethodHandle handle = MethodHandles.lookup().unreflect(method);
            if (!isStatic)
            {
                handle = handle.bindTo(Objects.requireNonNull(target, "target"));
            }
            MethodType samType = MethodType.methodType(void.class, Event.class);
            return handleInvoker(handle.asType(samType));
        }
        catch (Throwable failure)
        {
            IllegalArgumentException reported = new IllegalArgumentException(
                    "Cannot create an event invoker for " + method, primaryFailure);
            reported.addSuppressed(failure);
            throw reported;
        }
    }

    private static EventListener<Event> handleInvoker(MethodHandle handle)
    {
        return event ->
        {
            try
            {
                invokeExact(handle, event);
            }
            catch (RuntimeException | Error failure)
            {
                throw failure;
            }
            catch (Throwable failure)
            {
                throw new IllegalStateException("Event listener failed", failure);
            }
        };
    }

    private static void invokeExact(MethodHandle handle, Event event) throws Throwable
    {
        handle.invokeExact(event);
    }

    private record PreparedSubscriber(Class<Event> eventType, EventListener<Event> listener,
                                      EventPriority priority, boolean receiveCanceled)
    {
    }

    private record DispatchSnapshot(Registration[] registrations)
    {
    }

    private static final class Registration
    {
        private final Class<? extends Event> eventType;
        private final Object owner;
        private final EventListener<Event> listener;
        private final EventPriority priority;
        private final boolean receiveCanceled;
        private final EventFilter filter;
        private final long sequence;
        private ListenerHandle handle;

        @SuppressWarnings("unchecked")
        private Registration(Class<? extends Event> eventType, Object owner,
                             EventListener<?> listener, EventPriority priority,
                             boolean receiveCanceled, EventFilter filter, long sequence)
        {
            this.eventType = eventType;
            this.owner = owner;
            this.listener = (EventListener<Event>) listener;
            this.priority = priority;
            this.receiveCanceled = receiveCanceled;
            this.filter = filter;
            this.sequence = sequence;
        }
    }

    private static final class Stats
    {
        private final AtomicLong posts = new AtomicLong();
        private final AtomicLong listenerInvocations = new AtomicLong();
        private final AtomicLong canceledPosts = new AtomicLong();
        private final AtomicLong exceptions = new AtomicLong();
        private final AtomicLong totalNanos = new AtomicLong();
        private final AtomicLong maxNanos = new AtomicLong();

        private void recordPost(long nanos, boolean canceled)
        {
            posts.incrementAndGet();
            totalNanos.addAndGet(nanos);
            if (canceled)
            {
                canceledPosts.incrementAndGet();
            }
            long current = maxNanos.get();
            while (nanos > current && !maxNanos.compareAndSet(current, nanos))
            {
                current = maxNanos.get();
            }
        }

        private void recordInvocation()
        {
            listenerInvocations.incrementAndGet();
        }

        private void recordException()
        {
            exceptions.incrementAndGet();
        }

        private EventBusStats snapshot()
        {
            return new EventBusStats(posts.get(), listenerInvocations.get(), canceledPosts.get(),
                    exceptions.get(), totalNanos.get(), maxNanos.get());
        }

        private void reset()
        {
            posts.set(0);
            listenerInvocations.set(0);
            canceledPosts.set(0);
            exceptions.set(0);
            totalNanos.set(0);
            maxNanos.set(0);
        }
    }
}
