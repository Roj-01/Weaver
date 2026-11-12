package net.weaver.api.game.client.render;

import net.weaver.api.client.ClientException;
import net.weaver.api.client.ClientRegistration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Registry for world render listeners.
 *
 * <p>The built-in {@code LevelRenderer#addMainThreadGizmos} hook calls
 * {@link #render(WorldRenderContext)} once per rendered frame on the render
 * thread. Listeners run in ascending {@link WorldRenderListener#order()}, with
 * registration order as the tie-break. Registration must happen on the client
 * thread; rendering is driven by the hook and must not be called manually from
 * game code.
 *
 * <p>A listener exception is isolated and reported once per listener id. An
 * unregister removes the listener from future frames immediately.
 */
public final class WorldRenderRegistry
{
    private static final Object LOCK = new Object();
    private static final List<Entry> ENTRIES = new ArrayList<>();
    private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();
    private static volatile Entry[] snapshot = new Entry[0];
    private static long sequence;

    private WorldRenderRegistry()
    {
    }

    /**
     * Registers a world render listener.
     *
     * @param listener the listener, never {@code null}
     * @return a registration handle, never {@code null}
     * @throws ClientException when the listener id is blank or already
     *                         registered
     */
    public static ClientRegistration register(WorldRenderListener listener)
    {
        Objects.requireNonNull(listener, "listener");
        String id = listener.id();
        if (id == null || id.isBlank())
        {
            throw new ClientException("world render listener id must not be blank");
        }
        synchronized (LOCK)
        {
            for (Entry entry : ENTRIES)
            {
                if (entry.id.equals(id))
                {
                    throw new ClientException("duplicate world render listener id: " + id);
                }
            }
            Entry entry = new Entry(listener, id, listener.order(), sequence++);
            Registration registration = new Registration(entry);
            entry.registration = registration;
            ENTRIES.add(entry);
            rebuild();
            return registration;
        }
    }

    /**
     * Renders all active listeners.
     *
     * <p>Called by the built-in world render hook on the render thread. A
     * failure from one listener is logged once and does not stop later
     * listeners.
     *
     * @param context the immutable render snapshot, never {@code null}
     */
    public static void render(WorldRenderContext context)
    {
        Objects.requireNonNull(context, "context");
        for (Entry entry : snapshot)
        {
            if (!entry.registration.isActive())
            {
                continue;
            }
            try
            {
                entry.listener.render(context);
            }
            catch (Throwable failure)
            {
                if (FAILED.add(entry.id))
                {
                    System.err.println("[weaver] WORLD RENDER LISTENER FAILED id=" + entry.id
                            + " reason=" + failure);
                }
            }
        }
    }

    /**
     * Returns the number of currently registered listeners.
     *
     * @return the active listener count
     */
    public static int listenerCount()
    {
        return snapshot.length;
    }

    /**
     * Removes every listener and clears failure bookkeeping.
     *
     * <p>Intended for tests and loader shutdown. It may be called from any
     * thread.
     */
    public static void reset()
    {
        synchronized (LOCK)
        {
            for (Entry entry : ENTRIES)
            {
                entry.registration.deactivate();
            }
            ENTRIES.clear();
            FAILED.clear();
            sequence = 0L;
            rebuild();
        }
    }

    private static void remove(Entry entry)
    {
        synchronized (LOCK)
        {
            ENTRIES.remove(entry);
            rebuild();
        }
    }

    private static void rebuild()
    {
        Entry[] next = ENTRIES.toArray(new Entry[0]);
        Arrays.sort(next, Comparator.comparingInt((Entry entry) -> entry.order)
                .thenComparingLong(entry -> entry.sequence));
        snapshot = next;
    }

    private static final class Entry
    {
        private final WorldRenderListener listener;
        private final String id;
        private final int order;
        private final long sequence;
        private Registration registration;

        private Entry(WorldRenderListener listener, String id, int order, long sequence)
        {
            this.listener = listener;
            this.id = id;
            this.order = order;
            this.sequence = sequence;
        }
    }

    private static final class Registration implements ClientRegistration
    {
        private final Entry entry;
        private final AtomicBoolean active = new AtomicBoolean(true);

        private Registration(Entry entry)
        {
            this.entry = entry;
        }

        @Override
        public String id()
        {
            return entry.id;
        }

        @Override
        public boolean isActive()
        {
            return active.get();
        }

        @Override
        public void unregister()
        {
            if (!active.compareAndSet(true, false))
            {
                return;
            }
            remove(entry);
        }

        private void deactivate()
        {
            active.set(false);
        }
    }
}
