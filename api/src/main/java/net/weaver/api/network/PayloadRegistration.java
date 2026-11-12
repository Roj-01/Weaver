package net.weaver.api.network;

import java.util.Objects;

/**
 * An immutable registration of one payload type for a direction and phase.
 *
 * <p>Registrations are created by {@link NetworkRegistry} and expose the
 * metadata consumers need for policy and handshake decisions. The stable
 * diagnostic key combines direction, phase, and channel, for example
 * {@code SERVERBOUND/PLAY/example:telemetry/v1}. Equality deliberately ignores
 * the codec and handler and compares only direction, phase, and channel.</p>
 *
 * @param <T> the payload type
 */
public final class PayloadRegistration<T>
{
    private final PayloadType<T> type;
    private final PayloadDirection direction;
    private final NetworkPhase phase;
    private final boolean optional;
    private final PayloadHandler<T> handler;

    PayloadRegistration(PayloadType<T> type, PayloadDirection direction, NetworkPhase phase,
                        boolean optional, PayloadHandler<T> handler)
    {
        this.type = Objects.requireNonNull(type, "type");
        this.direction = Objects.requireNonNull(direction, "direction");
        this.phase = Objects.requireNonNull(phase, "phase");
        this.optional = optional;
        this.handler = Objects.requireNonNull(handler, "handler");
    }

    /**
     * The id of the mod that owns the channel.
     *
     * @return the mod id, never {@code null}
     */
    public String modId()
    {
        return type.modId();
    }

    /**
     * The versioned channel id.
     *
     * @return the channel id, never {@code null}
     */
    public String channel()
    {
        return type.channel();
    }

    /**
     * The path portion of the channel id.
     *
     * @return the path, never {@code null}
     */
    public String path()
    {
        return type.path();
    }

    /**
     * The channel version.
     *
     * @return the version, always at least {@code 1}
     */
    public int version()
    {
        return type.version();
    }

    /**
     * The direction in which this registration accepts or sends payloads.
     *
     * @return the direction, never {@code null}
     */
    public PayloadDirection direction()
    {
        return direction;
    }

    /**
     * The connection phase in which this registration is active.
     *
     * @return the phase, never {@code null}
     */
    public NetworkPhase phase()
    {
        return phase;
    }

    /**
     * Whether the registration is optional metadata for policy consumers.
     *
     * <p>Optional registrations still deliver payloads normally. The flag only
     * tells handshake and policy code that peers are not required to advertise
     * or support the channel.</p>
     *
     * @return {@code true} when the channel is optional
     */
    public boolean optional()
    {
        return optional;
    }

    /**
     * The registered payload type.
     *
     * @return the type, never {@code null}
     */
    public PayloadType<T> type()
    {
        return type;
    }

    /**
     * The handler invoked for inbound payloads.
     *
     * @return the handler, never {@code null}
     */
    public PayloadHandler<T> handler()
    {
        return handler;
    }

    /**
     * A stable diagnostic key combining direction, phase, and channel.
     *
     * @return the key in the form
     *         {@code <DIRECTION>/<PHASE>/<channel>}, never {@code null}
     */
    public String key()
    {
        return direction.name() + "/" + phase.name() + "/" + channel();
    }

    /**
     * Compares registrations by direction, phase, and channel.
     *
     * @param other the object to compare with
     * @return {@code true} when all three identifying fields match
     */
    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }
        if (!(other instanceof PayloadRegistration<?> registration))
        {
            return false;
        }
        return direction == registration.direction
                && phase == registration.phase
                && channel().equals(registration.channel());
    }

    /**
     * Returns a hash code derived from direction, phase, and channel.
     *
     * @return the hash code
     */
    @Override
    public int hashCode()
    {
        return Objects.hash(direction, phase, channel());
    }

    /**
     * Returns a diagnostic description of this registration.
     *
     * @return a description containing the key and optional flag
     */
    @Override
    public String toString()
    {
        return "PayloadRegistration[" + key() + ", optional=" + optional + "]";
    }
}
