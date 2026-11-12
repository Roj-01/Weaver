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
 * Posted once per mod configuration after the manager's first load attempt.
 *
 * <p>The manager posts this event after it has applied defaults, migrations,
 * and file values, and after it has published the load result. A listener can
 * therefore inspect both the immutable {@link ConfigSpec} and the complete
 * {@link ConfigLoadResult}, and can call {@code ConfigManager.get(modId())} for
 * the manager that is loading. Problems do not prevent the event: a corrupt,
 * missing, newer, or partially invalid file still produces exactly one event so
 * listeners can react to the fallback state.
 *
 * <p>The event is not cancelable and exposes no result. Its logical side
 * metadata is derived from {@link ConfigSpec#side()}: {@code CLIENT} maps to
 * {@link LogicalSide#CLIENT}, {@code SERVER} to {@link LogicalSide#SERVER}, and
 * {@code BOTH} to {@link LogicalSide#BOTH}.
 */
public final class ConfigLoadedEvent extends Event implements ModScopedEvent
{
    private final ConfigSpec spec;
    private final ConfigLoadResult result;

    /**
     * Creates the event for a completed load attempt.
     *
     * @param spec   the configuration specification, not {@code null}
     * @param result the completed load result, not {@code null}
     * @throws NullPointerException when {@code spec} or {@code result} is
     *                              {@code null}
     */
    public ConfigLoadedEvent(ConfigSpec spec, ConfigLoadResult result)
    {
        this.spec = Objects.requireNonNull(spec, "spec");
        this.result = Objects.requireNonNull(result, "result");
        setLogicalSide(toLogicalSide(spec.side()));
    }

    /**
     * The configuration specification that was loaded.
     *
     * @return the spec, never {@code null}
     */
    public ConfigSpec spec()
    {
        return spec;
    }

    /**
     * The result of the load attempt.
     *
     * @return the load result, never {@code null}
     */
    public ConfigLoadResult result()
    {
        return result;
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
     * The problems recorded while loading.
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
