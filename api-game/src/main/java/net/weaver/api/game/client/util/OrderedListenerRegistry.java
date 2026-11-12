package net.weaver.api.game.client.util;

import net.weaver.api.client.ClientException;
import net.weaver.api.client.ClientRegistration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Thread-safe ordered storage for {@link OrderedClientListener} instances.
 *
 * <p>Registration validates the id, rejects duplicates, and rebuilds an
 * immutable snapshot for lock-free dispatch. Dispatch runs in ascending
 * {@link OrderedClientListener#order()} with registration order as the
 * tie-break. A callback failure is isolated, reported once per listener id with
 * the supplied failure label, and does not stop later listeners.
 *
 * <p>The registry never calls into the game. Game-typed registries wrap it and
 * build their own contexts inside the callbacks. It is public because the
 * registries live in separate packages; mod code should use the concrete
 * registry for its feature rather than this support type.
 *
 * @param <L> the listener type
 */
public final class OrderedListenerRegistry<L extends OrderedClientListener>
{
    private final String type;
    private final Object lock = new Object();
    private final List<Entry<L>> entries = new ArrayList<>();
    private final Set<String> failed = ConcurrentHashMap.newKeySet();
    @SuppressWarnings("unchecked")
    private volatile Entry<L>[] snapshot = new Entry[0];
    private long sequence;

    /**
     * Creates an empty registry.
     *
     * @param type the listener type name used in duplicate messages, never
     *             {@code null} or blank
     */
    public OrderedListenerRegistry(String type)
    {
        if (type == null || type.isBlank())
        {
            throw new IllegalArgumentException("listener type must not be null or blank");
        }
        this.type = type;
    }

    /**
     * Registers a listener.
     *
     * @param listener the listener, never {@code null}
     * @return a registration handle, never {@code null}
     * @throws ClientException          when the listener id is blank or a
     *                                  listener with the same id is active
     * @throws NullPointerException     when {@code listener} is {@code null}
     */
    public ClientRegistration register(L listener)
    {
        Objects.requireNonNull(listener, "listener");
        String id = listener.id();
        if (id == null || id.isBlank())
        {
            throw new ClientException(type + " id must not be blank");
        }
        synchronized (lock)
        {
            for (Entry<L> entry : entries)
            {
                if (entry.id.equals(id))
                {
                    throw new ClientException("duplicate " + type + " id: " + id);
                }
            }
            Entry<L> entry = new Entry<>(listener, id, listener.order(), sequence++);
            ClientRegistrationHandle handle = ClientRegistrationHandle.create(id,
                    () -> remove(entry));
            entry.registration = handle;
            entries.add(entry);
            rebuild();
            return handle;
        }
    }

    /**
     * Dispatches one callback to every active listener in order.
     *
     * @param failureLabel the log label used when a listener fails, never
     *                     {@code null} or blank
     * @param action       the callback, never {@code null}
     */
    public void forEach(String failureLabel, Consumer<? super L> action)
    {
        Objects.requireNonNull(action, "action");
        for (Entry<L> entry : snapshot)
        {
            if (!entry.registration.isActive())
            {
                continue;
            }
            try
            {
                action.accept(entry.listener);
            }
            catch (Throwable failure)
            {
                report(failureLabel, entry.id, failure);
            }
        }
    }

    /**
     * Dispatches one callback to every active listener in order and stops at the
     * first listener that returns {@code true}.
     *
     * <p>A failed listener is treated as {@code false} so one broken mod cannot
     * cancel or block an operation on behalf of another mod.
     *
     * @param failureLabel the log label used when a listener fails, never
     *                     {@code null} or blank
     * @param action       the predicate, never {@code null}
     * @return {@code true} when a listener returned {@code true}
     */
    public boolean anyMatch(String failureLabel, Predicate<? super L> action)
    {
        Objects.requireNonNull(action, "action");
        for (Entry<L> entry : snapshot)
        {
            if (!entry.registration.isActive())
            {
                continue;
            }
            try
            {
                if (action.test(entry.listener))
                {
                    return true;
                }
            }
            catch (Throwable failure)
            {
                report(failureLabel, entry.id, failure);
            }
        }
        return false;
    }

    /**
     * Returns the number of active listeners.
     *
     * @return the listener count
     */
    public int listenerCount()
    {
        return snapshot.length;
    }

    /**
     * Removes every listener, deactivates their handles, and clears failure
     * bookkeeping.
     *
     * <p>Intended for tests and loader shutdown. It may be called from any
     * thread.
     */
    public void reset()
    {
        synchronized (lock)
        {
            for (Entry<L> entry : entries)
            {
                entry.registration.deactivate();
            }
            entries.clear();
            failed.clear();
            sequence = 0L;
            rebuild();
        }
    }

    private void remove(Entry<L> entry)
    {
        synchronized (lock)
        {
            entries.remove(entry);
            rebuild();
        }
    }

    @SuppressWarnings("unchecked")
    private void rebuild()
    {
        Entry<L>[] next = entries.toArray(new Entry[0]);
        Arrays.sort(next, Comparator.comparingInt((Entry<L> entry) -> entry.order)
                .thenComparingLong(entry -> entry.sequence));
        snapshot = next;
    }

    private void report(String failureLabel, String id, Throwable failure)
    {
        if (failed.add(id))
        {
            System.err.println("[weaver] " + failureLabel + " FAILED id=" + id
                    + " reason=" + failure);
        }
    }

    private static final class Entry<L extends OrderedClientListener>
    {
        private final L listener;
        private final String id;
        private final int order;
        private final long sequence;
        private ClientRegistrationHandle registration;

        private Entry(L listener, String id, int order, long sequence)
        {
            this.listener = listener;
            this.id = id;
            this.order = order;
            this.sequence = sequence;
        }
    }
}
