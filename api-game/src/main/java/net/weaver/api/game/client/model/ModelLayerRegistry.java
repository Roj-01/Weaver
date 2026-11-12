package net.weaver.api.game.client.model;

import net.minecraft.client.model.geom.LayerDefinitions;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.weaver.api.client.ClientException;
import net.weaver.api.client.ClientRegistration;
import net.weaver.api.game.client.util.ClientRegistrationHandle;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Registry for model layer definitions.
 *
 * <p>The built-in {@code ModelLayerMixin} injects at the return of
 * {@code LayerDefinitions#createRoots}. The vanilla result is an immutable map,
 * so the registry copies it into a new {@link LinkedHashMap} and adds the
 * registered definitions. The vanilla map is never mutated.
 *
 * <p><strong>Bake-order implication:</strong> {@code createRoots} is evaluated
 * while the client builds its {@code EntityModelSet}. A layer must be
 * registered before that point for the current model set. A registration made
 * later, for example after the entity renderer table has been created, is not
 * baked until the next client resource reload. Calling
 * {@code EntityModelSet#bakeLayer} for a missing location throws, so register
 * model layers during client setup, before the first reload finishes.
 *
 * <p>Registering a location that vanilla already defines overrides it for the
 * next {@code createRoots} call. Duplicate registrations inside Weaver are
 * rejected so two mods cannot silently clobber each other through this
 * registry.
 *
 * <p>The registry is thread-safe. All methods may be called from any thread.
 */
public final class ModelLayerRegistry
{
    private static final Map<ModelLayerLocation, Entry> ENTRIES = new ConcurrentHashMap<>();
    private static final AtomicLong SEQUENCE = new AtomicLong();

    private ModelLayerRegistry()
    {
    }

    /**
     * Registers a model layer definition.
     *
     * @param location   the model layer location, never {@code null}
     * @param definition the layer definition, never {@code null}
     * @return a registration handle, never {@code null}
     * @throws NullPointerException when an argument is {@code null}
     * @throws ClientException      when the location is already registered
     */
    public static ClientRegistration register(ModelLayerLocation location,
            LayerDefinition definition)
    {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(definition, "definition");
        long sequence = SEQUENCE.incrementAndGet();
        Entry entry = new Entry(location, definition, sequence);
        ClientRegistrationHandle handle = ClientRegistrationHandle.create(
                "model-layer:" + sequence, () -> ENTRIES.remove(location, entry));
        entry.registration = handle;
        Entry previous = ENTRIES.putIfAbsent(location, entry);
        if (previous != null)
        {
            handle.deactivate();
            throw new ClientException("duplicate model layer registration: " + location);
        }
        return handle;
    }

    /**
     * Merges the registered definitions into a newly created root map.
     *
     * <p>Integration entry point for the built-in mixin. The vanilla map is
     * never mutated. The method never throws.
     *
     * @param vanilla the immutable map created by vanilla, never {@code null}
     * @return the original map when no registrations exist, otherwise a new
     *         merged map
     */
    @net.weaver.api.annotation.Internal
    public static Map<ModelLayerLocation, LayerDefinition> merge(
            Map<ModelLayerLocation, LayerDefinition> vanilla)
    {
        Objects.requireNonNull(vanilla, "vanilla");
        if (ENTRIES.isEmpty())
        {
            return vanilla;
        }
        Map<ModelLayerLocation, LayerDefinition> merged = new LinkedHashMap<>(vanilla);
        for (Entry entry : ENTRIES.values())
        {
            merged.put(entry.location, entry.definition);
        }
        return merged;
    }

    /**
     * Returns the number of registered definitions.
     *
     * @return the registration count
     */
    public static int registrationCount()
    {
        return ENTRIES.size();
    }

    /**
     * Removes every registration.
     *
     * <p>Intended for tests and loader shutdown. An {@code EntityModelSet} that
     * was already built is not modified.
     */
    public static void reset()
    {
        for (Entry entry : ENTRIES.values())
        {
            entry.registration.deactivate();
        }
        ENTRIES.clear();
        SEQUENCE.set(0L);
    }

    private static final class Entry
    {
        private final ModelLayerLocation location;
        private final LayerDefinition definition;
        private final long sequence;
        private ClientRegistrationHandle registration;

        private Entry(ModelLayerLocation location, LayerDefinition definition, long sequence)
        {
            this.location = location;
            this.definition = definition;
            this.sequence = sequence;
        }
    }
}
