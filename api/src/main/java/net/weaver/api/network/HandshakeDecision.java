package net.weaver.api.network;

import java.util.Objects;

/**
 * The outcome of a {@link HandshakePolicy} evaluation.
 *
 * <p>An accepted decision may carry an empty reason. A rejected decision must
 * carry a non-blank reason that can be shown to the connecting player.</p>
 *
 * @param accepted whether the connection is accepted
 * @param reason   the rejection reason; empty when accepted, non-blank when
 *                 rejected
 */
public record HandshakeDecision(boolean accepted, String reason)
{
    /**
     * Validates the components.
     *
     * @throws NullPointerException     when {@code reason} is {@code null}
     * @throws IllegalArgumentException when the reason is too long, or when the
     *                                  decision is rejected and the reason is
     *                                  blank
     */
    public HandshakeDecision
    {
        reason = Objects.requireNonNull(reason, "reason");
        NetworkValidation.requireMaxChars(reason, "reason",
                NetworkProtocol.MAX_REASON_CHARS);
        if (!accepted && reason.isBlank())
        {
            throw new IllegalArgumentException("reason must not be blank when the handshake "
                    + "is rejected");
        }
    }

    /**
     * Creates an acceptance with an empty reason.
     *
     * @return an accepted decision, never {@code null}
     */
    public static HandshakeDecision accept()
    {
        return new HandshakeDecision(true, "");
    }

    /**
     * Creates a rejection with the supplied reason.
     *
     * @param reason the non-blank rejection reason, not {@code null}
     * @return a rejection, never {@code null}
     * @throws NullPointerException     when {@code reason} is {@code null}
     * @throws IllegalArgumentException when the reason is blank or too long
     */
    public static HandshakeDecision reject(String reason)
    {
        return new HandshakeDecision(false, reason);
    }
}
