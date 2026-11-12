package net.weaver.networkproof;

import net.weaver.api.network.NetworkModInfo;
import net.weaver.api.network.PayloadBuffer;
import net.weaver.api.network.PayloadCodec;
import net.weaver.api.network.PayloadType;

import java.util.List;

/**
 * Shared channel definitions for the standalone two-process TCP proof.
 *
 * <p>The proof exercises the Weaver core transport over real loopback sockets.
 * It is deliberately independent of Minecraft: this is a transport/API proof,
 * not a game-bridge proof.</p>
 */
public final class ProofProtocol
{
    public static final String MOD_ID = "networkproof";
    public static final String MOD_VERSION = "1.0.0";

    public static final PayloadCodec<String> STRING_CODEC = new PayloadCodec<>()
    {
        @Override
        public void encode(String value, PayloadBuffer out)
        {
            out.writeString(value);
        }

        @Override
        public String decode(PayloadBuffer in)
        {
            return in.readString();
        }
    };

    public static final PayloadType<String> PING =
            PayloadType.of(MOD_ID, "ping", STRING_CODEC);
    public static final PayloadType<String> PONG =
            PayloadType.of(MOD_ID, "pong", STRING_CODEC);

    private ProofProtocol()
    {
        throw new AssertionError("No ProofProtocol instances");
    }

    public static List<NetworkModInfo> mods()
    {
        return List.of(NetworkModInfo.of(MOD_ID, MOD_VERSION));
    }
}
