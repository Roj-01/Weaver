package net.weaver.core.capability;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import net.weaver.api.capability.AttachmentRegistry;

/**
 * Installs the core attachment stores on an {@link AttachmentRegistry}.
 *
 * <p>An {@code AttachmentRegistry} constructor calls
 * {@link #install(AttachmentRegistry)} reflectively, so the item, entity, block
 * entity, level, and chunk stores are available without shared wiring as soon
 * as the core artifact is on the class path. Installation is idempotent per
 * registry identity and {@link #uninstall(AttachmentRegistry)} reverses it.</p>
 *
 * <p>The installation map keeps registries strongly while they are installed.
 * That is required for identity-based idempotence and diagnostics; entries last
 * for the process lifetime unless {@link #uninstall(AttachmentRegistry)} is
 * called.</p>
 */
public final class CapabilityStores
{
    private static final Map<AttachmentRegistry, Installed> INSTALLED =
            new IdentityHashMap<>();

    private CapabilityStores()
    {
    }

    /**
     * Installs the core stores on a registry.
     *
     * @param registry the non-null registry
     * @return {@code true} when the stores were installed by this call,
     *         {@code false} when the registry already had them
     * @throws NullPointerException when {@code registry} is {@code null}
     */
    public static boolean install(AttachmentRegistry registry)
    {
        Objects.requireNonNull(registry, "registry");
        synchronized (INSTALLED)
        {
            if (INSTALLED.containsKey(registry))
            {
                return false;
            }
            Installed stores = new Installed(
                    new ItemStackAttachmentStore(),
                    new EntityAttachmentStore(registry),
                    new BlockEntityAttachmentStore(registry),
                    new LevelAttachmentStore(registry),
                    new ChunkAttachmentStore(registry));
            registry.registerStore(stores.itemStack());
            registry.registerStore(stores.entity());
            registry.registerStore(stores.blockEntity());
            registry.registerStore(stores.level());
            registry.registerStore(stores.chunk());
            INSTALLED.put(registry, stores);
            return true;
        }
    }

    /**
     * Removes the stores installed by {@link #install(AttachmentRegistry)}.
     *
     * @param registry the non-null registry
     * @return {@code true} when the installed stores were removed
     * @throws NullPointerException when {@code registry} is {@code null}
     */
    public static boolean uninstall(AttachmentRegistry registry)
    {
        Objects.requireNonNull(registry, "registry");
        synchronized (INSTALLED)
        {
            Installed stores = INSTALLED.remove(registry);
            if (stores == null)
            {
                return false;
            }
            boolean removed = registry.removeStore(stores.itemStack());
            removed |= registry.removeStore(stores.entity());
            removed |= registry.removeStore(stores.blockEntity());
            removed |= registry.removeStore(stores.level());
            removed |= registry.removeStore(stores.chunk());
            return removed;
        }
    }

    /**
     * Tests whether this class installed stores on a registry.
     *
     * @param registry the non-null registry
     * @return {@code true} when installed stores are recorded
     * @throws NullPointerException when {@code registry} is {@code null}
     */
    public static boolean isInstalled(AttachmentRegistry registry)
    {
        Objects.requireNonNull(registry, "registry");
        synchronized (INSTALLED)
        {
            return INSTALLED.containsKey(registry);
        }
    }

    /**
     * Installs the core stores on the process-wide registry.
     *
     * <p>This is safe to call from mods and from environments where the core
     * artifact is not fully available: every failure is swallowed.
     */
    public static void installIfPossible()
    {
        try
        {
            install(AttachmentRegistry.global());
        }
        catch (Throwable ignored)
        {
        }
    }

    static Installed installed(AttachmentRegistry registry)
    {
        Objects.requireNonNull(registry, "registry");
        synchronized (INSTALLED)
        {
            return INSTALLED.get(registry);
        }
    }

    /**
     * The stores installed on one registry.
     *
     * @param itemStack   the item stack store
     * @param entity      the entity store
     * @param blockEntity the block entity store
     * @param level       the level store
     * @param chunk       the chunk store
     */
    record Installed(ItemStackAttachmentStore itemStack, EntityAttachmentStore entity,
                     BlockEntityAttachmentStore blockEntity,
                     LevelAttachmentStore level, ChunkAttachmentStore chunk)
    {
        Installed
        {
            Objects.requireNonNull(itemStack, "itemStack");
            Objects.requireNonNull(entity, "entity");
            Objects.requireNonNull(blockEntity, "blockEntity");
            Objects.requireNonNull(level, "level");
            Objects.requireNonNull(chunk, "chunk");
        }
    }
}
