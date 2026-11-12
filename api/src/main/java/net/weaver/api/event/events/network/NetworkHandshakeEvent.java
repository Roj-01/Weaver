package net.weaver.api.event.events.network;

import net.weaver.api.event.Event;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.network.HandshakeRequest;
import net.weaver.api.network.NetworkModInfo;
import net.weaver.api.network.NetworkProtocol;

import java.util.List;
import java.util.Objects;

/**
 * Posted on the server during login after the default compatibility check and
 * before the handshake response is sent.
 *
 * <p>Listeners can inspect the client's {@link HandshakeRequest} and the
 * server's local mod list, then veto the connection with {@link #reject(String)}
 * to add a server-specific policy on top of the protocol's built-in checks.
 * The event is not cancelable; rejection is expressed through the veto so the
 * first policy decision can be reported precisely to the client. The logical
 * side is always {@link LogicalSide#SERVER}.</p>
 */
public final class NetworkHandshakeEvent extends Event
{
    private final HandshakeRequest request;
    private final List<NetworkModInfo> localMods;
    private volatile String rejectionReason;

    /**
     * Creates the event for one incoming handshake.
     *
     * @param request   the client's handshake request, not {@code null}
     * @param localMods the server's local mod identities, not {@code null}
     * @throws NullPointerException when an argument is {@code null} or the list
     *                              contains {@code null}
     */
    public NetworkHandshakeEvent(HandshakeRequest request, List<NetworkModInfo> localMods)
    {
        this.request = Objects.requireNonNull(request, "request");
        this.localMods = List.copyOf(localMods);
        setLogicalSide(LogicalSide.SERVER);
    }

    /**
     * The client's handshake request.
     *
     * @return the request, never {@code null}
     */
    public HandshakeRequest request()
    {
        return request;
    }

    /**
     * The server's local mod identities, in registration order.
     *
     * @return an immutable list, never {@code null}
     */
    public List<NetworkModInfo> localMods()
    {
        return localMods;
    }

    /**
     * Vetoes the handshake with a human-readable reason.
     *
     * <p>The first valid rejection wins; later calls are ignored. A blank or
     * {@code null} reason is always rejected so a veto can never produce an
     * empty client-facing message.</p>
     *
     * @param reason the non-blank rejection reason, at most
     *               {@link NetworkProtocol#MAX_REASON_CHARS} chars
     * @throws NullPointerException     when {@code reason} is {@code null}
     * @throws IllegalArgumentException when {@code reason} is blank or too long
     */
    public void reject(String reason)
    {
        Objects.requireNonNull(reason, "reason");
        if (reason.isBlank())
        {
            throw new IllegalArgumentException("rejection reason must not be blank");
        }
        if (reason.length() > NetworkProtocol.MAX_REASON_CHARS)
        {
            throw new IllegalArgumentException("rejection reason must be at most "
                    + NetworkProtocol.MAX_REASON_CHARS + " chars but was "
                    + reason.length());
        }
        synchronized (this)
        {
            if (rejectionReason == null)
            {
                rejectionReason = reason;
            }
        }
    }

    /**
     * Whether a listener rejected this handshake.
     *
     * @return {@code true} when {@link #reject(String)} was called successfully
     */
    public boolean isRejected()
    {
        return rejectionReason != null;
    }

    /**
     * The first accepted rejection reason.
     *
     * @return the rejection reason, or an empty string when the handshake was
     *         not rejected
     */
    public String rejectionReason()
    {
        String reason = rejectionReason;
        return reason == null ? "" : reason;
    }
}
