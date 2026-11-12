package net.weaver.api.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The server's answer to a {@link HandshakeRequest}.
 *
 * <p>An accepted response carries the server's protocol, Weaver, and game
 * versions plus the server's mods and channels so the client can finish its own
 * compatibility pass. A rejected response carries a non-blank human-readable
 * reason and disconnects the client. Lists are defensively copied and immutable,
 * and mod ids and channel ids must be unique.</p>
 *
 * @param accepted        whether the connection was accepted
 * @param reason          the rejection reason; empty when accepted
 * @param protocolVersion the positive Weaver protocol version
 * @param weaverVersion   the non-blank Weaver version, at most
 *                        {@link NetworkProtocol#MAX_STRING_CHARS} chars
 * @param gameVersion     the non-blank game version, at most
 *                        {@link NetworkProtocol#MAX_STRING_CHARS} chars
 * @param mods            the server's mod identities, at most
 *                        {@link NetworkProtocol#MAX_MODS} unique entries
 * @param channels        the server's advertised channel ids, at most
 *                        {@link NetworkProtocol#MAX_CHANNELS} unique entries
 */
public record HandshakeResponse(boolean accepted, String reason, int protocolVersion,
                                String weaverVersion, String gameVersion,
                                List<NetworkModInfo> mods, List<String> channels)
{
    /**
     * The stable wire codec for handshake responses.
     *
     * <p>The accepted flag comes first, followed by the reason and the version
     * and list data. Counts and lengths are varints and are validated before
     * allocation on decode.</p>
     */
    public static final PayloadCodec<HandshakeResponse> CODEC = new PayloadCodec<>()
    {
        @Override
        public void encode(HandshakeResponse value, PayloadBuffer out)
        {
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(out, "out");
            out.writeBoolean(value.accepted());
            out.writeString(value.reason(), NetworkProtocol.MAX_REASON_CHARS);
            out.writeVarInt(value.protocolVersion());
            out.writeString(value.weaverVersion(), NetworkProtocol.MAX_STRING_CHARS);
            out.writeString(value.gameVersion(), NetworkProtocol.MAX_STRING_CHARS);
            out.writeVarInt(value.mods().size());
            for (NetworkModInfo mod : value.mods())
            {
                out.writeString(mod.id(), NetworkProtocol.MAX_MOD_ID_CHARS);
                out.writeString(mod.version(), NetworkProtocol.MAX_MOD_VERSION_CHARS);
            }
            out.writeVarInt(value.channels().size());
            for (String channel : value.channels())
            {
                out.writeString(channel, NetworkProtocol.MAX_CHANNEL_CHARS);
            }
        }

        @Override
        public HandshakeResponse decode(PayloadBuffer in)
        {
            Objects.requireNonNull(in, "in");
            try
            {
                boolean accepted = in.readBoolean();
                String reason = in.readString(NetworkProtocol.MAX_REASON_CHARS);
                int protocolVersion = in.readVarInt();
                String weaverVersion = in.readString(NetworkProtocol.MAX_STRING_CHARS);
                String gameVersion = in.readString(NetworkProtocol.MAX_STRING_CHARS);
                int modCount = in.readVarInt();
                if (modCount < 0 || modCount > NetworkProtocol.MAX_MODS)
                {
                    throw new NetworkException("handshake mod count " + modCount
                            + " is outside 0.." + NetworkProtocol.MAX_MODS);
                }
                List<NetworkModInfo> mods = new ArrayList<>(modCount);
                for (int index = 0; index < modCount; index++)
                {
                    String id = in.readString(NetworkProtocol.MAX_MOD_ID_CHARS);
                    String version = in.readString(NetworkProtocol.MAX_MOD_VERSION_CHARS);
                    mods.add(new NetworkModInfo(id, version));
                }
                int channelCount = in.readVarInt();
                if (channelCount < 0 || channelCount > NetworkProtocol.MAX_CHANNELS)
                {
                    throw new NetworkException("handshake channel count " + channelCount
                            + " is outside 0.." + NetworkProtocol.MAX_CHANNELS);
                }
                List<String> channels = new ArrayList<>(channelCount);
                for (int index = 0; index < channelCount; index++)
                {
                    channels.add(in.readString(NetworkProtocol.MAX_CHANNEL_CHARS));
                }
                HandshakeResponse response = new HandshakeResponse(accepted, reason,
                        protocolVersion, weaverVersion, gameVersion, mods, channels);
                in.ensureFullyRead();
                return response;
            }
            catch (IllegalArgumentException failure)
            {
                throw new NetworkException("invalid handshake response: "
                        + failure.getMessage(), failure);
            }
        }
    };

    /**
     * Validates and defensively copies the components.
     *
     * @throws NullPointerException     when {@code reason}, {@code mods}, or
     *                                  {@code channels} is {@code null}, or when
     *                                  a list contains {@code null}
     * @throws IllegalArgumentException when accepted is false and the reason is
     *                                  blank, the reason or a version exceeds
     *                                  its limit, the protocol version is not
     *                                  positive, or a mod or channel entry is
     *                                  invalid or duplicated
     */
    public HandshakeResponse
    {
        reason = Objects.requireNonNull(reason, "reason");
        NetworkValidation.requireMaxChars(reason, "reason",
                NetworkProtocol.MAX_REASON_CHARS);
        if (!accepted && reason.isBlank())
        {
            throw new IllegalArgumentException("reason must not be blank when the handshake "
                    + "is rejected");
        }
        if (protocolVersion < 1)
        {
            throw new IllegalArgumentException("protocolVersion must be >= 1 but was "
                    + protocolVersion);
        }
        NetworkValidation.requireNonBlank(weaverVersion, "weaverVersion");
        NetworkValidation.requireMaxChars(weaverVersion, "weaverVersion",
                NetworkProtocol.MAX_STRING_CHARS);
        NetworkValidation.requireNonBlank(gameVersion, "gameVersion");
        NetworkValidation.requireMaxChars(gameVersion, "gameVersion",
                NetworkProtocol.MAX_STRING_CHARS);
        mods = NetworkValidation.copyMods(mods);
        channels = NetworkValidation.copyChannels(channels);
    }

    /**
     * Creates an accepted response with an empty reason.
     *
     * @param protocolVersion the positive Weaver protocol version
     * @param weaverVersion   the non-blank Weaver version, not {@code null}
     * @param gameVersion     the non-blank game version, not {@code null}
     * @param mods            the server's mod identities, not {@code null}
     * @param channels        the server's channel ids, not {@code null}
     * @return an accepted response, never {@code null}
     * @throws NullPointerException     when a list or string is {@code null}
     * @throws IllegalArgumentException when an argument is invalid
     */
    public static HandshakeResponse accept(int protocolVersion, String weaverVersion,
                                           String gameVersion, List<NetworkModInfo> mods,
                                           List<String> channels)
    {
        return new HandshakeResponse(true, "", protocolVersion, weaverVersion, gameVersion,
                mods, channels);
    }

    /**
     * Creates a rejected response with a precise human-readable reason.
     *
     * @param reason          the non-blank rejection reason, not {@code null}
     * @param protocolVersion the positive Weaver protocol version
     * @param weaverVersion   the non-blank Weaver version, not {@code null}
     * @param gameVersion     the non-blank game version, not {@code null}
     * @param mods            the server's mod identities, not {@code null}
     * @param channels        the server's channel ids, not {@code null}
     * @return a rejected response, never {@code null}
     * @throws NullPointerException     when a string or list is {@code null}
     * @throws IllegalArgumentException when the reason is blank or an argument is
     *                                  invalid
     */
    public static HandshakeResponse reject(String reason, int protocolVersion,
                                           String weaverVersion, String gameVersion,
                                           List<NetworkModInfo> mods, List<String> channels)
    {
        return new HandshakeResponse(false, reason, protocolVersion, weaverVersion,
                gameVersion, mods, channels);
    }
}
