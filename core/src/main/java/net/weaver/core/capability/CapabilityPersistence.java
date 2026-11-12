package net.weaver.core.capability;

import net.weaver.api.capability.AttachmentRegistry;
import net.weaver.api.capability.PersistentAttachmentStore;
import net.weaver.core.data.HolderDataLimits;

/**
 * Entry points used by the game hooks to persist capability values on runtime
 * holders.
 *
 * <p>Every entry point resolves the stores installed on the process-wide
 * registry and delegates to the matching
 * {@link PersistentAttachmentStore}. Failure of a carrier or of a single
 * capability is isolated and logged with a {@code [weaver]} line; a save or
 * load never fails because of Weaver data. Entity and block entity persistence
 * are wired to core game hooks; level and chunk persistence are driven by the
 * {@code api-game} mixins through {@code AttachmentRegistry#saveHolder} and
 * {@code #loadHolder}, which reach the same stores.</p>
 */
public final class CapabilityPersistence
{
    /** Key of the Weaver child written into every holder carrier. */
    public static final String ROOT_KEY = HolderDataLimits.ROOT_KEY;

    private CapabilityPersistence()
    {
    }

    /**
     * Writes entity capability values into a game output carrier.
     *
     * @param entity the entity whose values are written
     * @param output the game {@code ValueOutput}
     */
    public static void saveEntity(Object entity, Object output)
    {
        CapabilityStores.Installed installed = stores();
        save("entity", entity, output, installed == null ? null : installed.entity());
    }

    /**
     * Reads entity capability values from a game input carrier.
     *
     * @param entity the entity that receives the values
     * @param input  the game {@code ValueInput}
     */
    public static void loadEntity(Object entity, Object input)
    {
        CapabilityStores.Installed installed = stores();
        load("entity", entity, input, installed == null ? null : installed.entity());
    }

    /**
     * Writes block entity capability values into a game output carrier.
     *
     * @param blockEntity the block entity whose values are written
     * @param output      the game {@code ValueOutput}
     */
    public static void saveBlockEntity(Object blockEntity, Object output)
    {
        CapabilityStores.Installed installed = stores();
        save("block_entity", blockEntity, output,
                installed == null ? null : installed.blockEntity());
    }

    /**
     * Reads block entity capability values from a game input carrier.
     *
     * @param blockEntity the block entity that receives the values
     * @param input       the game {@code ValueInput}
     */
    public static void loadBlockEntity(Object blockEntity, Object input)
    {
        CapabilityStores.Installed installed = stores();
        load("block_entity", blockEntity, input,
                installed == null ? null : installed.blockEntity());
    }

    /**
     * Writes level capability values into a game output carrier.
     *
     * @param level  the level whose values are written
     * @param output the game {@code ValueOutput}
     */
    public static void saveLevel(Object level, Object output)
    {
        CapabilityStores.Installed installed = stores();
        save("level", level, output, installed == null ? null : installed.level());
    }

    /**
     * Reads level capability values from a game input carrier.
     *
     * @param level the level that receives the values
     * @param input the game {@code ValueInput}
     */
    public static void loadLevel(Object level, Object input)
    {
        CapabilityStores.Installed installed = stores();
        load("level", level, input, installed == null ? null : installed.level());
    }

    /**
     * Writes chunk capability values into a game output carrier.
     *
     * @param chunk  the chunk whose values are written
     * @param output the game {@code ValueOutput}
     */
    public static void saveChunk(Object chunk, Object output)
    {
        CapabilityStores.Installed installed = stores();
        save("chunk", chunk, output, installed == null ? null : installed.chunk());
    }

    /**
     * Reads chunk capability values from a game input carrier.
     *
     * @param chunk the chunk that receives the values
     * @param input the game {@code ValueInput}
     */
    public static void loadChunk(Object chunk, Object input)
    {
        CapabilityStores.Installed installed = stores();
        load("chunk", chunk, input, installed == null ? null : installed.chunk());
    }

    private static void save(String kind, Object holder, Object output,
            PersistentAttachmentStore store)
    {
        if (holder == null || output == null)
        {
            return;
        }
        if (store == null || !store.supportsHolder(holder))
        {
            return;
        }
        try
        {
            store.saveTo(holder, output);
        }
        catch (Throwable failure)
        {
            log("SAVE", kind, holder, failure);
        }
    }

    private static void load(String kind, Object holder, Object input,
            PersistentAttachmentStore store)
    {
        if (holder == null || input == null)
        {
            return;
        }
        if (store == null || !store.supportsHolder(holder))
        {
            return;
        }
        try
        {
            store.loadFrom(holder, input);
        }
        catch (Throwable failure)
        {
            log("LOAD", kind, holder, failure);
        }
    }

    private static CapabilityStores.Installed stores()
    {
        AttachmentRegistry registry = AttachmentRegistry.global();
        CapabilityStores.Installed installed = CapabilityStores.installed(registry);
        if (installed != null)
        {
            return installed;
        }
        try
        {
            CapabilityStores.install(registry);
        }
        catch (Throwable ignored)
        {
        }
        return CapabilityStores.installed(registry);
    }

    private static void log(String phase, String kind, Object holder, Throwable failure)
    {
        String message = failure.getMessage();
        CapabilityLog.log("[weaver] CAPABILITY " + phase + " FAILED kind=" + kind
                + " holder=" + holder.getClass().getName() + " reason="
                + (message != null ? message : failure.getClass().getName()));
    }
}
