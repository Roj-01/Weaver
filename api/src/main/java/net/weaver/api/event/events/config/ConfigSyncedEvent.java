package net.weaver.api.event.events.config;

import net.weaver.api.config.ConfigProblem;
import net.weaver.api.config.ConfigSpec;
import net.weaver.api.config.ConfigSyncResult;
import net.weaver.api.event.Event;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.event.ModScopedEvent;
import net.weaver.api.event.Side;

import java.util.List;
import java.util.Objects;

/**
 * Posted after a client applies a server-authoritative configuration snapshot.
 *
 * <p>The event is produced by
 * {@link net.weaver.api.config.ConfigManager#applySyncedValues(java.util.Map, int)}
 * after the values have been validated and published in memory. It describes
 * the client's read-only view: the manager never writes the client file as part
 * of the transfer. The event is not cancelable and has no result, and its
 * logical side is always {@link LogicalSide#CLIENT}.</p>
 *
 * <p>{@link #side()} remains the configuration's declared side, which may be
 * {@link Side#CLIENT} or {@link Side#BOTH}; use {@link #logicalSide()} to
 * observe that this was a client-side application.</p>
 */
public final class ConfigSyncedEvent extends Event implements ModScopedEvent
{
    private final ConfigSpec spec;
    private final ConfigSyncResult result;

    /**
     * Creates the event for one applied snapshot.
     *
     * @param spec   the configuration specification, not {@code null}
     * @param result the completed sync result, not {@code null}
     * @throws NullPointerException when {@code spec} or {@code result} is
     *                              {@code null}
     */
    public ConfigSyncedEvent(ConfigSpec spec, ConfigSyncResult result)
    {
        this.spec = Objects.requireNonNull(spec, "spec");
        this.result = Objects.requireNonNull(result, "result");
        setLogicalSide(LogicalSide.CLIENT);
    }

    /**
     * The configuration specification that received the snapshot.
     *
     * @return the spec, never {@code null}
     */
    public ConfigSpec spec()
    {
        return spec;
    }

    /**
     * The completed sync result.
     *
     * @return the result, never {@code null}
     */
    public ConfigSyncResult result()
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
     * The side the configuration declares.
     *
     * @return {@link Side#CLIENT}, {@link Side#SERVER}, or {@link Side#BOTH}
     */
    public Side side()
    {
        return spec.side();
    }

    /**
     * The schema version of the applied snapshot.
     *
     * @return the schema version, at least {@code 1}
     */
    public int schemaVersion()
    {
        return result.schemaVersion();
    }

    /**
     * How many declared values came from the snapshot.
     *
     * @return the applied value count
     */
    public int appliedValues()
    {
        return result.appliedValues();
    }

    /**
     * How many declared values used their default.
     *
     * @return the defaulted value count
     */
    public int defaultedValues()
    {
        return result.defaultedValues();
    }

    /**
     * How many undeclared keys were ignored.
     *
     * @return the ignored key count
     */
    public int ignoredKeys()
    {
        return result.ignoredKeys();
    }

    /**
     * The invalid-value problems recorded while applying the snapshot.
     *
     * @return an immutable problem list, never {@code null}
     */
    public List<ConfigProblem> problems()
    {
        return result.problems();
    }
}
