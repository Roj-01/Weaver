package net.weaver.api.game.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The single Minecraft custom payload that carries every Weaver game-bridge
 * envelope.
 *
 * <p>Minecraft 26.3 keeps only the payload id for an unknown custom payload; it
 * discards the body before a listener can see it. The bridge therefore
 * registers one known payload type, {@code weaver:bridge}, in the packet
 * class initialisers before the protocol codecs capture their type lists. The
 * body of this payload is a {@link BridgeEnvelope}, which carries the Weaver
 * handshake, channel payloads, and disconnects.</p>
 *
 * <p>The record copies its body on construction and on every access, so a
 * caller cannot mutate bytes retained by the network stack. The maximum body
 * is {@link #MAX_BRIDGE_BODY}, one kilobyte below the 26.3 clientbound custom
 * payload limit, which leaves room for the payload id, the byte-array length,
 * and packet framing.</p>
 */
public record GamePayload(byte[] body) implements CustomPacketPayload
{
    /**
     * The maximum bridge body accepted on either side.
     *
     * <p>This is {@code 1_048_576 - 1024}. The remaining space is reserved for
     * the {@code weaver:bridge} identifier, the encoded byte-array length, and
     * container framing. The limit is enforced on construction so an outbound
     * payload fails before it reaches the game connection.</p>
     */
    public static final int MAX_BRIDGE_BODY = 1_047_552;

    /**
     * The fixed payload id {@code weaver:bridge}.
     */
    public static final Identifier ID = Identifier.fromNamespaceAndPath("weaver", "bridge");

    /**
     * The fixed payload type used by both directions.
     */
    public static final Type<GamePayload> TYPE = new Type<>(ID);

    /**
     * The stream codec that writes and reads the bridge body.
     *
     * <p>The codec uses {@link FriendlyByteBuf#writeByteArray(byte[])} and
     * {@link FriendlyByteBuf#readByteArray(int)} with
     * {@link #MAX_BRIDGE_BODY} as the read bound.</p>
     */
    public static final StreamCodec<FriendlyByteBuf, GamePayload> STREAM_CODEC =
            CustomPacketPayload.codec(GamePayload::write, GamePayload::new);

    /**
     * Validates and copies the body.
     *
     * @param body the encoded bridge envelope, not {@code null}
     * @throws NullPointerException     when {@code body} is {@code null}
     * @throws IllegalArgumentException when the body exceeds
     *                                  {@link #MAX_BRIDGE_BODY}
     */
    public GamePayload
    {
        Objects.requireNonNull(body, "body");
        if (body.length > MAX_BRIDGE_BODY)
        {
            throw new IllegalArgumentException("bridge body of " + body.length
                    + " bytes exceeds the maximum of " + MAX_BRIDGE_BODY);
        }
        body = body.clone();
    }

    /**
     * Returns a copy of the retained bridge body.
     *
     * @return a new byte array, never {@code null}
     */
    @Override
    public byte[] body()
    {
        return body.clone();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Type<GamePayload> type()
    {
        return TYPE;
    }

    /**
     * Appends the bridge type to a packet codec list without mutating the
     * supplied list.
     *
     * <p>This is the callback used by the clientbound and serverbound packet
     * class-initialiser mixins. Both packet classes build their dispatch codec
     * during class initialisation, so the entry must be present when
     * {@code CustomPacketPayload.codec(FallbackProvider, List)} is invoked. The
     * configuration list in 26.3 is immutable, which is why the input is copied
     * rather than appended in place.</p>
     *
     * @param codecs the original type list, not {@code null}
     * @return a new mutable list containing the original entries and the bridge
     *         entry, never {@code null}
     * @throws NullPointerException when {@code codecs} is {@code null}
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static List<CustomPacketPayload.TypeAndCodec<?, ?>> registerIn(List<?> codecs)
    {
        Objects.requireNonNull(codecs, "codecs");
        List<CustomPacketPayload.TypeAndCodec<?, ?>> copy = new ArrayList<>(codecs.size() + 1);
        for (Object entry : codecs)
        {
            if (entry instanceof CustomPacketPayload.TypeAndCodec<?, ?> typed)
            {
                if (!TYPE.id().equals(typed.type().id()))
                {
                    copy.add(typed);
                }
            }
        }
        copy.add(new CustomPacketPayload.TypeAndCodec(GamePayload.TYPE, GamePayload.STREAM_CODEC));
        return copy;
    }

    private void write(FriendlyByteBuf buffer)
    {
        buffer.writeByteArray(body);
    }

    private GamePayload(FriendlyByteBuf buffer)
    {
        this(buffer.readByteArray(MAX_BRIDGE_BODY));
    }
}
