package net.weaver.api.game.client.entity;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.weaver.api.client.ClientException;
import net.weaver.api.client.ClientRegistration;
import net.weaver.api.game.client.util.ClientRegistrationHandle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Registry for entity renderer providers.
 *
 * <p>The built-in {@code EntityRendererMixin} injects at the return of
 * {@code EntityRenderers#createEntityRenderers}. The vanilla result is an
 * immutable map, so the registry never mutates it: it copies the vanilla
 * entries into a new {@link LinkedHashMap}, creates each registered renderer
 * with the supplied {@link EntityRendererProvider.Context}, and returns the new
 * map.
 *
 * <p>Registering an entity type that already has a vanilla renderer overrides
 * that renderer for the next map creation. A provider failure or a {@code null}
 * renderer is isolated and logged; the vanilla entry is kept for that type.
 *
 * <p>Renderer creation happens during the client resource reload that builds
 * the entity renderer table. A registration made after that point becomes
 * visible on the next reload. The registry is thread-safe.
 */
public final class EntityRendererRegistry
{
    private static final Map<EntityType<?>, Entry> ENTRIES = new ConcurrentHashMap<>();
    private static final Set<EntityType<?>> FAILED = ConcurrentHashMap.newKeySet();
    private static final AtomicLong SEQUENCE = new AtomicLong();

    private EntityRendererRegistry()
    {
    }

    /**
     * Registers an entity renderer provider.
     *
     * @param type     the entity type, never {@code null}
     * @param provider the renderer provider, never {@code null}
     * @param <T>      the entity type
     * @return a registration handle, never {@code null}
     * @throws NullPointerException  when an argument is {@code null}
     * @throws ClientException       when the type is already registered
     */
    public static <T extends Entity> ClientRegistration register(EntityType<T> type,
            EntityRendererProvider<T> provider)
    {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(provider, "provider");
        long sequence = SEQUENCE.incrementAndGet();
        Entry entry = new Entry(type, provider, sequence);
        ClientRegistrationHandle handle = ClientRegistrationHandle.create(
                "entity-renderer:" + sequence, () -> ENTRIES.remove(type, entry));
        entry.registration = handle;
        Entry previous = ENTRIES.putIfAbsent(type, entry);
        if (previous != null)
        {
            handle.deactivate();
            throw new ClientException("duplicate entity renderer registration: " + type);
        }
        return handle;
    }

    /**
     * Merges the registered providers into a newly created renderer map.
     *
     * <p>Integration entry point for the built-in mixin. The vanilla map is
     * never mutated. The method never throws; a failed provider is logged once
     * per type and the vanilla entry is kept.
     *
     * @param vanilla the immutable map created by vanilla, never {@code null}
     * @param context the renderer provider context, normally non-null
     * @return the original map when no registrations exist, otherwise a new
     *         merged map
     */
    @net.weaver.api.annotation.Internal
    public static Map<EntityType<?>, EntityRenderer<?, ?>> merge(
            Map<EntityType<?>, EntityRenderer<?, ?>> vanilla,
            EntityRendererProvider.Context context)
    {
        Objects.requireNonNull(vanilla, "vanilla");
        if (ENTRIES.isEmpty())
        {
            return vanilla;
        }
        List<Entry> entries = new ArrayList<>(ENTRIES.values());
        entries.sort(Comparator.comparingLong(entry -> entry.sequence));
        Map<EntityType<?>, EntityRenderer<?, ?>> merged = new LinkedHashMap<>(vanilla);
        for (Entry entry : entries)
        {
            EntityRenderer<?, ?> renderer;
            try
            {
                renderer = create(entry, context);
            }
            catch (Throwable failure)
            {
                if (FAILED.add(entry.type))
                {
                    System.err.println("[weaver] ENTITY RENDERER FAILED type=" + entry.type
                            + " reason=" + failure);
                }
                continue;
            }
            if (renderer == null)
            {
                if (FAILED.add(entry.type))
                {
                    System.err.println("[weaver] ENTITY RENDERER NULL type=" + entry.type);
                }
                continue;
            }
            merged.put(entry.type, renderer);
        }
        return merged;
    }

    /**
     * Returns the number of registered providers.
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
     * <p>Intended for tests and loader shutdown. A renderer map that was
     * already built is not modified.
     */
    public static void reset()
    {
        for (Entry entry : ENTRIES.values())
        {
            entry.registration.deactivate();
        }
        ENTRIES.clear();
        FAILED.clear();
        SEQUENCE.set(0L);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static EntityRenderer<?, ?> create(Entry entry,
            EntityRendererProvider.Context context)
    {
        EntityRendererProvider provider = entry.provider;
        return provider.create(context);
    }

    private static final class Entry
    {
        private final EntityType<?> type;
        private final EntityRendererProvider<?> provider;
        private final long sequence;
        private ClientRegistrationHandle registration;

        private Entry(EntityType<?> type, EntityRendererProvider<?> provider, long sequence)
        {
            this.type = type;
            this.provider = provider;
            this.sequence = sequence;
        }
    }
}
