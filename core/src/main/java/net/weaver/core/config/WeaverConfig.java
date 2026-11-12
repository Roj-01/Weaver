package net.weaver.core.config;

import net.weaver.api.ModContext;
import net.weaver.api.config.ConfigException;
import net.weaver.api.config.ConfigManager;
import net.weaver.api.config.ConfigSpec;
import net.weaver.api.config.ConfigStore;
import net.weaver.api.event.EventBus;
import net.weaver.api.event.Side;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Mod-facing convenience entry point for file-backed configuration.
 *
 * <p>A mod normally calls {@link #open(ModContext, ConfigSpec)} once during
 * {@code onInitialize}. Opening a config reads the file once; saving is
 * explicit through the returned {@link ConfigManager}. Hot reload is available
 * through {@link ConfigManager#reload()} or, when
 * {@code -Dweaver.config.watch=true} is set, through the core
 * {@link ConfigWatchService} that {@code ConfigManager.open} registers
 * automatically. Each process side uses its own file, so a client and a server
 * never share configuration state through the same path.</p>
 */
public final class WeaverConfig
{
    private WeaverConfig()
    {
    }

    /**
     * Returns the file-backed store for a mod context's side.
     *
     * @param context the mod context, never {@code null}
     * @return a store for the context's per-side config file
     * @throws NullPointerException when {@code context} is {@code null}
     * @throws ConfigException      when the context side is not supported
     */
    public static ConfigStore store(ModContext context)
    {
        return ConfigFiles.forContext(context);
    }

    /**
     * Maps a mod context's process side to a configuration side.
     *
     * <p>{@link ModContext#side()} is matched case-insensitively against
     * {@code client} and {@code server}.</p>
     *
     * @param context the mod context, never {@code null}
     * @return {@link Side#CLIENT} or {@link Side#SERVER}
     * @throws NullPointerException when {@code context} is {@code null}
     * @throws ConfigException      when the context side is not supported
     */
    public static Side sideOf(ModContext context)
    {
        return ConfigFiles.sideOf(context);
    }

    /**
     * Opens a mod's configuration on its local mod event bus.
     *
     * <p>This is the normal call from {@code onInitialize}. It uses
     * {@link ModContext#modEvents()} for configuration events and
     * {@link ModContext#log(String)} for diagnostics.</p>
     *
     * @param context the mod context, never {@code null}
     * @param spec    the configuration spec, never {@code null}
     * @return the opened configuration manager
     * @throws ConfigException      when the spec side does not match the
     *                              context side, or the configuration cannot
     *                              be opened
     * @throws NullPointerException when an argument is {@code null}
     */
    public static ConfigManager open(ModContext context, ConfigSpec spec)
    {
        Objects.requireNonNull(context, "context");
        return open(context, spec, context.modEvents());
    }

    /**
     * Opens a mod's configuration on an explicit event bus.
     *
     * <p>The spec side must equal the context's process side. That check runs
     * before the configuration file is created or read. Configuration events
     * are delivered on {@code eventBus}.</p>
     *
     * @param context  the mod context, never {@code null}
     * @param spec     the configuration spec, never {@code null}
     * @param eventBus the bus for configuration events
     * @return the opened configuration manager
     * @throws ConfigException      when the spec side does not match the
     *                              context side, or the configuration cannot
     *                              be opened
     * @throws NullPointerException when {@code context} or {@code spec} is
     *                              {@code null}
     */
    public static ConfigManager open(ModContext context, ConfigSpec spec, EventBus eventBus)
    {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(spec, "spec");
        Side processSide = ConfigFiles.sideOf(context);
        Side specSide = spec.side();
        if (specSide != processSide)
        {
            Path configFile = context.configDir().resolve(ConfigFiles.fileName(processSide));
            throw new ConfigException("Config side mismatch for mod '" + spec.modId()
                    + "': spec side is " + specSide + ", process side is " + processSide
                    + ", config file is " + configFile);
        }
        return ConfigManager.open(spec, store(context), eventBus, context::log);
    }
}
