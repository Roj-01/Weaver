package net.weaver.api.network;

import java.util.Locale;
import java.util.Objects;

/**
 * The lifecycle phase in which a network channel is available.
 *
 * <p>Phases are ordered from the earliest connection stage to the latest:
 * {@link #LOGIN}, then {@link #CONFIGURATION}, then {@link #PLAY}. Login covers
 * version and mod-list negotiation, configuration covers server data that must
 * be synchronized before the player enters the world, and play covers all
 * ordinary gameplay traffic. A channel registered for a later phase is never
 * sent during an earlier one.</p>
 */
public enum NetworkPhase
{
    /** Version and mod compatibility negotiation before play begins. */
    LOGIN,

    /** Pre-play synchronization of configuration and registry data. */
    CONFIGURATION,

    /** Ordinary gameplay traffic after the connection has completed setup. */
    PLAY;

    /**
     * Returns whether this phase happens strictly before {@code other}.
     *
     * @param other the phase to compare with, not {@code null}
     * @return {@code true} when this phase is earlier than {@code other}
     * @throws NullPointerException when {@code other} is {@code null}
     */
    public boolean isBefore(NetworkPhase other)
    {
        Objects.requireNonNull(other, "other");
        return ordinal() < other.ordinal();
    }

    /**
     * Returns whether this phase happens at or after {@code other}.
     *
     * @param other the phase to compare with, not {@code null}
     * @return {@code true} when this phase is the same as or later than
     *         {@code other}
     * @throws NullPointerException when {@code other} is {@code null}
     */
    public boolean isAtLeast(NetworkPhase other)
    {
        Objects.requireNonNull(other, "other");
        return ordinal() >= other.ordinal();
    }

    /**
     * Returns whether this is the final phase of a connection.
     *
     * @return {@code true} only for {@link #PLAY}
     */
    public boolean isTerminal()
    {
        return this == PLAY;
    }

    /**
     * Parses a phase name without regard to case.
     *
     * <p>Leading and trailing whitespace is ignored. Both the enum constant
     * names and lower-case spellings are accepted, so {@code "play"},
     * {@code "PLAY"}, and {@code " Play "} all resolve to {@link #PLAY}.
     *
     * @param value the phase name, not {@code null}
     * @return the matching phase, never {@code null}
     * @throws IllegalArgumentException when {@code value} is {@code null} or
     *                                  does not name a phase; the message lists
     *                                  the accepted names
     */
    public static NetworkPhase parse(String value)
    {
        if (value == null)
        {
            throw new IllegalArgumentException("Network phase must not be null; expected one of "
                    + namesForMessage());
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        try
        {
            return valueOf(normalized);
        }
        catch (IllegalArgumentException failure)
        {
            throw new IllegalArgumentException("Unknown network phase '" + value
                    + "'; expected one of " + namesForMessage(), failure);
        }
    }

    private static String namesForMessage()
    {
        return "LOGIN, CONFIGURATION, PLAY";
    }
}
