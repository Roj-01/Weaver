package net.weaver.api.network;

import java.util.List;

/**
 * Decides whether a client handshake is compatible with the local mod set.
 *
 * <p>The policy is evaluated on the game thread during {@link NetworkPhase#LOGIN},
 * after the transport's default protocol and channel compatibility checks. It
 * must be pure and thread-safe with respect to game state: inspect the request
 * and the local mod list, but do not mutate the world, registries, or the
 * connection. Returning a rejection causes the transport to send a precise
 * handshake rejection message derived from
 * {@link HandshakeDecision#reason()}.</p>
 */
@FunctionalInterface
public interface HandshakePolicy
{
    /**
     * Evaluates one handshake request.
     *
     * @param request   the client's request, not {@code null}
     * @param localMods the server's local mod identities, not {@code null};
     *                  the list is immutable
     * @return the decision, never {@code null}
     */
    HandshakeDecision evaluate(HandshakeRequest request, List<NetworkModInfo> localMods);
}
