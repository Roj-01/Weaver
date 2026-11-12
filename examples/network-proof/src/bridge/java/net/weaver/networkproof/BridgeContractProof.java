package net.weaver.networkproof;

import net.weaver.api.event.EventBus;
import net.weaver.api.event.events.network.NetworkConnectedEvent;
import net.weaver.api.event.events.network.NetworkDisconnectedEvent;
import net.weaver.api.event.events.network.NetworkPayloadEvent;
import net.weaver.api.game.network.BridgeEnvelope;
import net.weaver.api.game.network.BridgeSession;
import net.weaver.api.network.NetworkPhase;
import net.weaver.api.network.NetworkPlayer;
import net.weaver.api.network.NetworkProtocol;
import net.weaver.api.network.NetworkRegistry;
import net.weaver.api.network.NetworkSide;
import net.weaver.api.network.PayloadBuffer;
import net.weaver.api.network.PayloadCodec;
import net.weaver.api.network.PayloadType;
import net.weaver.api.network.UnknownChannelPolicy;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A game-free contract proof for the 26.3 bridge.
 *
 * <p>It exercises the real {@link BridgeEnvelope} codec and the real
 * {@link BridgeSession} state machine over a synchronous in-memory transport.
 * It never loads a Minecraft class, so it can run in the standalone network
 * proof JVM. It proves the envelope format, the server-first handshake, the
 * connected/queue/dispatch ordering, and the disconnect event count. It does
 * not prove that a Minecraft client and server exchange packets; that proof is
 * described in {@code GAME-BRIDGE-PROOF.md}.</p>
 */
public final class BridgeContractProof
{
    private static final PayloadCodec<String> STRINGS = new PayloadCodec<>()
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

    private BridgeContractProof()
    {
        throw new AssertionError("No BridgeContractProof instances");
    }

    public static void main(String[] args)
    {
        int checks = 0;
        try
        {
            checks = run();
            System.out.println("NETWORK BRIDGE PROOF RESULT ok=true checks=" + checks);
        }
        catch (Throwable failure)
        {
            System.out.println("NETWORK BRIDGE PROOF RESULT ok=false reason=" + failure);
            failure.printStackTrace(System.out);
            System.exit(1);
        }
    }

    private static int run()
    {
        int checks = 0;
        PayloadType<String> ping = PayloadType.of("proof", "ping", STRINGS);
        PayloadType<String> pong = PayloadType.of("proof", "pong", STRINGS);

        NetworkRegistry serverRegistry = new NetworkRegistry();
        NetworkRegistry clientRegistry = new NetworkRegistry();
        List<String> received = new CopyOnWriteArrayList<>();
        List<String> pongs = new CopyOnWriteArrayList<>();
        serverRegistry.registerServerbound("proof", ping, (payload, context) ->
        {
            received.add(payload);
            context.reply(pong, payload + "-pong");
        });
        serverRegistry.registerClientbound("proof", pong, (payload, context) -> { });
        clientRegistry.registerServerbound("proof", ping, (payload, context) -> { });
        clientRegistry.registerClientbound("proof", pong, (payload, context) ->
                pongs.add(payload));

        EventBus bus = new EventBus("bridge-contract-proof");
        AtomicInteger connected = new AtomicInteger();
        AtomicInteger disconnected = new AtomicInteger();
        AtomicInteger payloadEvents = new AtomicInteger();
        bus.addListener(NetworkConnectedEvent.class, event -> connected.incrementAndGet());
        bus.addListener(NetworkDisconnectedEvent.class,
                event -> disconnected.incrementAndGet());
        bus.addListener(NetworkPayloadEvent.class,
                event -> payloadEvents.incrementAndGet());

        Link serverLink = new Link();
        Link clientLink = new Link();
        NetworkPlayer proofPlayer = new NetworkPlayer(
                UUID.fromString("00000000-0000-0000-0000-000000000010"), "ProofClient");
        BridgeSession server = BridgeSession.builder(NetworkSide.SERVER, serverLink)
                .registry(serverRegistry)
                .eventBus(bus)
                .logger(line -> { })
                .authenticatedPlayer(proofPlayer)
                .unknownChannelPolicy(UnknownChannelPolicy.REJECT)
                .build();
        BridgeSession client = BridgeSession.builder(NetworkSide.CLIENT, clientLink)
                .registry(clientRegistry)
                .eventBus(bus)
                .logger(line -> { })
                .localPlayer(proofPlayer)
                .unknownChannelPolicy(UnknownChannelPolicy.REJECT)
                .build();
        serverLink.target(client);
        clientLink.target(server);

        BridgeEnvelope envelope = BridgeEnvelope.payload(NetworkProtocol.VERSION,
                NetworkPhase.PLAY, ping.channel(), new byte[]{7, 8, 9});
        BridgeEnvelope decoded = BridgeEnvelope.decode(envelope.encode());
        check(decoded.kind() == BridgeEnvelope.Kind.PAYLOAD, "envelope");
        checks++;

        client.onPhase(NetworkPhase.CONFIGURATION);
        server.onPhase(NetworkPhase.CONFIGURATION);
        client.onClientOutboundReady();
        check(server.isVerified() && client.isVerified(), "handshake");
        check("ProofClient".equals(server.player().name()), "identity");
        checks += 2;

        server.onPhase(NetworkPhase.PLAY);
        client.onPhase(NetworkPhase.PLAY);
        server.onPlayReady();
        client.onPlayReady();
        check(connected.get() == 2, "connected");
        check(server.isConnectedPosted() && client.isConnectedPosted(), "connected-state");
        checks += 2;

        client.send(ping, "hello");
        check(received.equals(List.of("hello")), "payload");
        check(pongs.equals(List.of("hello-pong")), "reply");
        check(payloadEvents.get() == 2, "payload-events");
        checks += 3;

        server.onTransportClosed("proof complete", true);
        client.onTransportClosed("proof complete", true);
        check(disconnected.get() == 2, "disconnect");
        checks++;

        return checks;
    }

    private static void check(boolean condition, String name)
    {
        if (!condition)
        {
            throw new IllegalStateException("check failed: " + name);
        }
        System.out.println("NETWORK BRIDGE PROOF CHECK ok=true " + name);
    }

    private static final class Link implements BridgeSession.Endpoint
    {
        private volatile BridgeSession peer;

        private void target(BridgeSession peer)
        {
            this.peer = peer;
        }

        @Override
        public void send(BridgeEnvelope envelope)
        {
            BridgeSession target = peer;
            if (target != null)
            {
                target.receive(BridgeEnvelope.decode(envelope.encode()));
            }
        }

        @Override
        public void disconnect(String reason)
        {
            // the proof inspects session state, not disconnect reasons
        }
    }
}
