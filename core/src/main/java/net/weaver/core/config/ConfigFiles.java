package net.weaver.core.config;

import net.weaver.api.ModContext;
import net.weaver.api.config.ConfigException;
import net.weaver.api.config.ConfigStore;
import net.weaver.api.event.Side;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Resolves the per-side configuration file for a mod.
 *
 * <p>Every mod has one file per side in its own config directory:
 * {@code client.json} for {@link Side#CLIENT}, {@code server.json} for
 * {@link Side#SERVER}, and {@code common.json} for {@link Side#BOTH}. These
 * helpers only construct paths and stores; they never read or write a file.</p>
 */
public final class ConfigFiles
{
    private ConfigFiles()
    {
    }

    /**
     * Returns the configuration file name for a side.
     *
     * @param side the side to name, never {@code null}
     * @return {@code client.json}, {@code server.json}, or {@code common.json}
     * @throws NullPointerException when {@code side} is {@code null}
     */
    public static String fileName(Side side)
    {
        Objects.requireNonNull(side, "side");
        return switch (side)
        {
            case CLIENT -> "client.json";
            case SERVER -> "server.json";
            case BOTH -> "common.json";
        };
    }

    /**
     * Creates a file-backed store for one side under a mod config directory.
     *
     * @param configDir the mod's config directory, never {@code null}
     * @param side      the side whose file the store uses, never {@code null}
     * @return a store for the side's file inside {@code configDir}
     * @throws NullPointerException when {@code configDir} or {@code side} is
     *                              {@code null}
     */
    public static ConfigStore forMod(Path configDir, Side side)
    {
        Objects.requireNonNull(configDir, "configDir");
        return new JsonConfigStore(configDir.resolve(fileName(side)), side);
    }

    /**
     * Creates a file-backed store for the side reported by a mod context.
     *
     * <p>{@link ModContext#side()} is matched case-insensitively against
     * {@code client} and {@code server}. The returned store is created but not
     * read.</p>
     *
     * @param context the mod context supplying the config directory and side
     * @return a store for the context's per-side config file
     * @throws NullPointerException when {@code context} is {@code null}
     * @throws ConfigException      when the context side is not {@code client}
     *                              or {@code server}
     */
    public static ConfigStore forContext(ModContext context)
    {
        Objects.requireNonNull(context, "context");
        return forMod(context.configDir(), sideOf(context));
    }

    static Side sideOf(ModContext context)
    {
        Objects.requireNonNull(context, "context");
        String value = context.side();
        if ("client".equalsIgnoreCase(value))
        {
            return Side.CLIENT;
        }
        if ("server".equalsIgnoreCase(value))
        {
            return Side.SERVER;
        }
        throw new ConfigException("Unsupported config side '" + value + "' for mod '"
                + context.id() + "'; supported sides are 'client' and 'server'");
    }
}
