package net.weaver.api.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The client's opening message to a Weaver server.
 *
 * <p>The request advertises the client's protocol version, Weaver version, game
 * version, player identity, installed mods, and registered channel ids. Servers
 * validate it during {@link NetworkPhase#LOGIN} before any configuration or
 * gameplay payload is accepted. Lists are defensively copied and immutable, and
 * mod ids and channel ids must be unique within the request.</p>
 *
 * @param protocolVersion the positive Weaver protocol version
 * @param weaverVersion   the non-blank Weaver version, at most
 *                        {@link NetworkProtocol#MAX_STRING_CHARS} chars
 * @param gameVersion     the non-blank game version, at most
 *                        {@link NetworkProtocol#MAX_STRING_CHARS} chars
 * @param player          the connecting player, not {@code null}
 * @param mods            the installed mod identities, at most
 *                        {@link NetworkProtocol#MAX_MODS} unique entries
 * @param channels        the advertised channel ids, at most
 *                        {@link NetworkProtocol#MAX_CHANNELS} unique entries
 */
public record HandshakeRequest(int protocolVersion, String weaverVersion, String gameVersion,
                               NetworkPlayer player, List<NetworkModInfo> mods,
                               List<String> channels)
{
    /**
     * The stable wire codec for handshake requests.
     *
     * <p>The encoding starts with the protocol version and ends with the channel
     * list. Every count and byte length is a varint, and decoding validates the
     * counts before allocating collections.</p>
     */
    public static final PayloadCodec<HandshakeRequest> CODEC = new PayloadCodec<>()
    {
        @Override
        public void encode(HandshakeRequest value, PayloadBuffer out)
        {
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(out, "out");
            out.writeVarInt(value.protocolVersion());
            out.writeString(value.weaverVersion(), NetworkProtocol.MAX_STRING_CHARS);
            out.writeString(value.gameVersion(), NetworkProtocol.MAX_STRING_CHARS);
            out.writeUuid(value.player().id());
            out.writeString(value.player().name(), NetworkProtocol.MAX_PLAYER_NAME_CHARS);
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
        public HandshakeRequest decode(PayloadBuffer in)
        {
            Objects.requireNonNull(in, "in");
            try
            {
                int protocolVersion = in.readVarInt();
                String weaverVersion = in.readString(NetworkProtocol.MAX_STRING_CHARS);
                String gameVersion = in.readString(NetworkProtocol.MAX_STRING_CHARS);
                UUID playerId = in.readUuid();
                String playerName = in.readString(NetworkProtocol.MAX_PLAYER_NAME_CHARS);
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
                HandshakeRequest request = new HandshakeRequest(protocolVersion, weaverVersion,
                        gameVersion, new NetworkPlayer(playerId, playerName), mods, channels);
                in.ensureFullyRead();
                return request;
            }
            catch (IllegalArgumentException failure)
            {
                throw new NetworkException("invalid handshake request: " + failure.getMessage(),
                        failure);
            }
        }
    };

    /**
     * Validates and defensively copies the components.
     *
     * @throws NullPointerException     when {@code player}, {@code mods}, or
     *                                  {@code channels} is {@code null}, or when
     *                                  a list contains {@code null}
     * @throws IllegalArgumentException when the protocol version is not
     *                                  positive, a version string is blank or
     *                                  too long, a mod or channel entry is
     *                                  invalid, or a list contains duplicates
     */
    public HandshakeRequest
    {
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
        Objects.requireNonNull(player, "player");
        mods = NetworkValidation.copyMods(mods);
        channels = NetworkValidation.copyChannels(channels);
    }
}
