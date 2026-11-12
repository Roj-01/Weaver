package net.weaver.api.event.events.config;

import net.weaver.api.config.ConfigLoadResult;
import net.weaver.api.config.ConfigProblem;
import net.weaver.api.config.ConfigSpec;
import net.weaver.api.event.Event;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.event.ModScopedEvent;
import net.weaver.api.event.Side;

import java.util.List;
import java.util.Objects;

/**
 * Posted after a mod configuration is reloaded.
 *
 * <p>{@link net.weaver.api.config.ConfigManager#reload()} posts this event after
 * it has re-read the store, applied defaults, migrations, and file values, and
 * published the new load result. A watcher-driven reload posts the same event.
 * A listener can inspect the immutable {@link ConfigSpec} and the complete
 * {@link ConfigLoadResult}, and can call {@code ConfigManager.get(modId())} for
 * the manager that reloaded. Problems do not prevent the event: a corrupt,
 * missing, newer, or partially invalid file still produces exactly one event
 * per completed reload so listeners can react to the fallback state.
 *
 * <p>The initial load uses {@link ConfigLoadedEvent}. This event is only for
 * subsequent load passes. Its {@link #generation()} is {@code 1} for an initial
 * load performed through {@code reload()} before any load completed, {@code 2}
 * for the first reload after the normal initial load, and one higher after each
 * further reload.
 *
 * <p>The event is not cancelable and exposes no result. Its logical side
 * metadata is derived from {@link ConfigSpec#side()}: {@code CLIENT} maps to
 * {@link LogicalSide#CLIENT}, {@code SERVER} to {@link LogicalSide#SERVER}, and
 * {@code BOTH} to {@link LogicalSide#BOTH}.
 */
public class ConfigReloadedEvent extends Event implements ModScopedEvent
{
    private final ConfigSpec spec;
    private final ConfigLoadResult result;
    private final int generation;

    /**
     * Creates the event for a completed reload.
     *
     * @param spec       the configuration specification, not {@code null}
     * @param result     the completed reload result, not {@code null}
     * @param generation the load generation, at least {@code 1}
     * @throws NullPointerException     when {@code spec} or {@code result} is
     *                                  {@code null}
     * @throws IllegalArgumentException when {@code generation} is less than
     *                                  {@code 1}
     */
    public ConfigReloadedEvent(ConfigSpec spec, ConfigLoadResult result, int generation)
    {
        this.spec = Objects.requireNonNull(spec, "spec");
        this.result = Objects.requireNonNull(result, "result");
        if (generation < 1)
        {
            throw new IllegalArgumentException("generation must be at least 1 but was "
                    + generation);
        }
        this.generation = generation;
        setLogicalSide(toLogicalSide(spec.side()));
    }

    /**
     * The configuration specification that was reloaded.
     *
     * @return the spec, never {@code null}
     */
    public ConfigSpec spec()
    {
        return spec;
    }

    /**
     * The result of the reload attempt.
     *
     * @return the reload result, never {@code null}
     */
    public ConfigLoadResult result()
    {
        return result;
    }

    /**
     * The load generation now applied.
     *
     * <p>The initial load is generation {@code 1}; the first reload is
     * generation {@code 2}; every completed reload increments the value.
     *
     * @return the generation, at least {@code 1}
     */
    public int generation()
    {
        return generation;
    }

    /**
     * The number of completed reloads.
     *
     * <p>This is {@link #generation()} minus one: {@code 0} for a reload that
     * performed the first load, {@code 1} for the first reload after a normal
     * open, and so on. Use {@link #generation()} when the initial load should
     * count as one.
     *
     * @return the number of completed reloads, at least {@code 0}
     */
    public int reloadCount()
    {
        return generation - 1;
    }

    /**
     * The id of the mod that owns the configuration.
     *
     * @return the mod id, never {@code null}
     */
    @Override
    public String modId()
    {
        return spec.modId();
    }

    /**
     * The side the configuration belongs to.
     *
     * @return the side, never {@code null}
     */
    public Side side()
    {
        return spec.side();
    }

    /**
     * The schema version now applied in memory.
     *
     * @return the applied schema version
     */
    public int schemaVersion()
    {
        return result.appliedVersion();
    }

    /**
     * The problems recorded while reloading.
     *
     * @return an immutable problem list, never {@code null}
     */
    public List<ConfigProblem> problems()
    {
        return result.problems();
    }

    private static LogicalSide toLogicalSide(Side side)
    {
        return switch (side)
        {
            case CLIENT -> LogicalSide.CLIENT;
            case SERVER -> LogicalSide.SERVER;
            case BOTH -> LogicalSide.BOTH;
        };
    }
}
