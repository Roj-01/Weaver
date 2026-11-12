package net.weaver.core.command;

import net.weaver.api.command.PermissionService;
import net.weaver.core.LifecycleHooks;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Loader-facing installer for the persisted permission pair.
 *
 * <p>Installs {@link MinecraftPermissionResolver} and a {@link JsonPermissionStore}
 * together, so a player subject persists as {@code player:<uuid>}. The
 * installer is idempotent and failure-isolated: a bad path or an installation
 * failure is logged and never thrown to the loader.
 *
 * <p>The intended loader wiring is one line after the data directory is known:
 *
 * <pre>{@code
 * PermissionStoreSupport.install(dataDir.resolve("permissions.json"), log);
 * }</pre>
 *
 * <p>Calling it before or after the command backend installs is safe. The
 * backend checks {@link PermissionService#hasStore()} and keeps the configured
 * resolver when the pair is already installed.
 */
public final class PermissionStoreSupport
{
    private static final Object LOCK = new Object();
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private PermissionStoreSupport()
    {
    }

    /**
     * Installs the persisted permission resolver and JSON store.
     *
     * @param file the permission store file, not {@code null}
     * @param log  the log sink, or {@code null} for the loader log
     */
    public static void install(Path file, Consumer<String> log)
    {
        Consumer<String> logger = log != null ? log : PermissionStoreSupport::loaderLog;
        if (INSTALLED.get())
        {
            return;
        }
        synchronized (LOCK)
        {
            if (INSTALLED.get())
            {
                return;
            }
            try
            {
                JsonPermissionStore store = new JsonPermissionStore(file, logger);
                PermissionService.install(new MinecraftPermissionResolver(), store);
                INSTALLED.set(true);
                logger.accept("[weaver] PERMISSION STORE ready file=" + file);
            }
            catch (Throwable failure)
            {
                logger.accept("[weaver] PERMISSION STORE INSTALL FAILED reason=" + failure);
            }
        }
    }

    /**
     * Clears the installer's idempotency state.
     *
     * <p>Test and embedding helper. It does not uninstall or reset the service.
     */
    static void reset()
    {
        synchronized (LOCK)
        {
            INSTALLED.set(false);
        }
    }

    private static void loaderLog(String message)
    {
        try
        {
            LifecycleHooks.logLine(message);
        }
        catch (Throwable ignored)
        {
        }
    }
}
