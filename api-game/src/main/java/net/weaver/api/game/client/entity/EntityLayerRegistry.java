package net.weaver.api.game.client.entity;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.weaver.api.client.ClientRegistration;
import net.weaver.api.game.client.util.ClientRegistrationHandle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Registry for render layers added to living entity renderers.
 *
 * <p>The built-in {@code EntityLayerMixin} injects at the return of
 * {@code LivingEntityRenderer#<init>} and calls {@link #attach} with the
 * renderer, the provider context, and a sink backed by the protected final
 * {@code addLayer} method. This is the safest verified path: every concrete
 * living renderer must call the base constructor, the model field is already
 * assigned, and the provider context is still available. The alternative
 * {@code addLayer} injection does not run for renderers that never add a
 * vanilla layer.
 *
 * <p>Because the injection runs before the concrete subclass constructor body,
 * factories must not depend on subclass initialization. Layers are also added
 * before the subclass's own vanilla layers, so a registered layer renders below
 * those vanilla layers.
 *
 * <p>Removal stops future attachments only. Vanilla exposes no layer removal,
 * so a layer already added to a live renderer stays until the renderer is
 * recreated (normally the next client resource reload).
 *
 * <p>The registry is thread-safe. One failing factory is isolated and reported
 * once per registration id.
 */
public final class EntityLayerRegistry
{
    private static final CopyOnWriteArrayList<Entry> ENTRIES = new CopyOnWriteArrayList<>();
    private static final Set<String> FAILED = ConcurrentHashMap.newKeySet();
    private static final AtomicLong SEQUENCE = new AtomicLong();

    private EntityLayerRegistry()
    {
    }

    /**
     * Registers a layer factory for a renderer type.
     *
     * <p>Several factories may target the same renderer type; they run in
     * registration order.
     *
     * @param rendererType the renderer class to match, never {@code null}
     * @param factory      the layer factory, never {@code null}
     * @return a registration handle, never {@code null}
     * @throws NullPointerException when an argument is {@code null}
     */
    public static ClientRegistration register(
            Class<? extends LivingEntityRenderer<?, ?, ?>> rendererType,
            EntityLayerFactory factory)
    {
        Objects.requireNonNull(rendererType, "rendererType");
        Objects.requireNonNull(factory, "factory");
        long sequence = SEQUENCE.incrementAndGet();
        Entry entry = new Entry(rendererType, factory, sequence);
        ClientRegistrationHandle handle = ClientRegistrationHandle.create(
                "entity-layer:" + sequence, () -> ENTRIES.remove(entry));
        entry.registration = handle;
        ENTRIES.add(entry);
        return handle;
    }

    /**
     * Creates and adds every matching layer for a renderer.
     *
     * <p>Integration entry point for the built-in mixin. Mod code must not call
     * this method directly. The method never throws; a failing factory is logged
     * once per registration id and does not stop later factories.
     *
     * @param renderer the renderer under construction, never {@code null}
     * @param context  the renderer provider context, normally non-null
     * @param sink     the layer sink, never {@code null}
     */
    @net.weaver.api.annotation.Internal
    public static void attach(LivingEntityRenderer<?, ?, ?> renderer,
            EntityRendererProvider.Context context, LayerSink sink)
    {
        Objects.requireNonNull(renderer, "renderer");
        Objects.requireNonNull(sink, "sink");
        if (ENTRIES.isEmpty())
        {
            return;
        }
        List<Entry> entries = new ArrayList<>(ENTRIES);
        entries.sort(Comparator.comparingLong(entry -> entry.sequence));
        for (Entry entry : entries)
        {
            if (!entry.registration.isActive() || !entry.rendererType.isInstance(renderer))
            {
                continue;
            }
            RenderLayer<?, ?> layer;
            try
            {
                layer = entry.factory.createLayer(renderer, context);
            }
            catch (Throwable failure)
            {
                if (FAILED.add(entry.registration.id()))
                {
                    System.err.println("[weaver] ENTITY LAYER FAILED id="
                            + entry.registration.id() + " reason=" + failure);
                }
                continue;
            }
            if (layer == null)
            {
                continue;
            }
            try
            {
                sink.add(layer);
            }
            catch (Throwable failure)
            {
                if (FAILED.add(entry.registration.id()))
                {
                    System.err.println("[weaver] ENTITY LAYER ADD FAILED id="
                            + entry.registration.id() + " reason=" + failure);
                }
            }
        }
    }

    /**
     * Returns the number of registered factories.
     *
     * @return the registration count
     */
    public static int registrationCount()
    {
        return ENTRIES.size();
    }

    /**
     * Removes every registration and clears failure bookkeeping.
     *
     * <p>Intended for tests and loader shutdown. Layers already attached to
     * live renderers are not removed.
     */
    public static void reset()
    {
        for (Entry entry : ENTRIES)
        {
            entry.registration.deactivate();
        }
        ENTRIES.clear();
        FAILED.clear();
        SEQUENCE.set(0L);
    }

    /**
     * Receives a layer that should be added to the renderer.
     */
    @FunctionalInterface
    public interface LayerSink
    {
        /**
         * Adds one layer.
         *
         * @param layer the layer, never {@code null}
         * @return the target's own add result
         */
        boolean add(RenderLayer<?, ?> layer);
    }

    private static final class Entry
    {
        private final Class<? extends LivingEntityRenderer<?, ?, ?>> rendererType;
        private final EntityLayerFactory factory;
        private final long sequence;
        private ClientRegistrationHandle registration;

        private Entry(Class<? extends LivingEntityRenderer<?, ?, ?>> rendererType,
                EntityLayerFactory factory, long sequence)
        {
            this.rendererType = rendererType;
            this.factory = factory;
            this.sequence = sequence;
        }
    }
}
