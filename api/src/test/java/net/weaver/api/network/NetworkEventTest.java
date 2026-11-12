package net.weaver.api.network;

import net.weaver.api.event.LogicalSide;
import net.weaver.api.event.events.network.NetworkConnectedEvent;
import net.weaver.api.event.events.network.NetworkDisconnectedEvent;
import net.weaver.api.event.events.network.NetworkHandshakeEvent;
import net.weaver.api.event.events.network.NetworkPayloadEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkEventTest
{
    private static final UUID PLAYER_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final NetworkPlayer PLAYER = new NetworkPlayer(PLAYER_ID, "EventPlayer");
    private static final List<NetworkModInfo> MODS = List.of(NetworkModInfo.of("alpha", "1.0.0"));
    private static final HandshakeRequest REQUEST = new HandshakeRequest(1, "2.1.0", "26.3",
            PLAYER, MODS, List.of("alpha:telemetry/v1"));
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
    private static final PayloadType<String> TYPE =
            PayloadType.of("alpha", "telemetry", STRINGS);

    @Test
    void handshakeEventFirstRejectionWinsAndLogicalSideIsServer()
    {
        NetworkHandshakeEvent event = new NetworkHandshakeEvent(REQUEST, MODS);

        assertSame(REQUEST, event.request());
        assertEquals(MODS, event.localMods());
        assertEquals(LogicalSide.SERVER, event.logicalSide());
        assertFalse(event.isRejected());
        assertEquals("", event.rejectionReason());

        event.reject("first reason");
        event.reject("second reason");

        assertTrue(event.isRejected());
        assertEquals("first reason", event.rejectionReason());
    }

    @Test
    void handshakeEventRejectsBlankAndNullReasons()
    {
        NetworkHandshakeEvent event = new NetworkHandshakeEvent(REQUEST, MODS);

        assertThrows(IllegalArgumentException.class, () -> event.reject("   "));
        assertThrows(IllegalArgumentException.class, () -> event.reject(""));
        assertThrows(NullPointerException.class, () -> event.reject(null));
        assertFalse(event.isRejected());

        assertThrows(IllegalArgumentException.class,
                () -> event.reject("a".repeat(NetworkProtocol.MAX_REASON_CHARS + 1)));
        assertFalse(event.isRejected());
    }

    @Test
    void handshakeEventCopiesLocalMods()
    {
        List<NetworkModInfo> mutable = new ArrayList<>(MODS);
        NetworkHandshakeEvent event = new NetworkHandshakeEvent(REQUEST, mutable);
        mutable.clear();

        assertEquals(MODS, event.localMods());
        assertThrows(UnsupportedOperationException.class, () -> event.localMods().clear());
    }

    @Test
    void payloadEventIsCancelableAndExposesChannelSideAndPlayer()
    {
        FakeConnection connection = new FakeConnection(NetworkSide.SERVER, NetworkPhase.PLAY,
                PLAYER);
        NetworkPayloadEvent event = new NetworkPayloadEvent(PayloadDirection.SERVERBOUND,
                connection, TYPE, "hello");

        assertTrue(event.isCancelable());
        assertFalse(event.isCanceled());
        assertSame(connection, event.connection());
        assertSame(TYPE, event.type());
        assertEquals(TYPE.channel(), event.channel());
        assertEquals("hello", event.payload());
        assertEquals(PayloadDirection.SERVERBOUND, event.direction());
        assertEquals(NetworkSide.SERVER, event.side());
        assertEquals(NetworkPhase.PLAY, event.phase());
        assertEquals(PLAYER, event.player());
        assertEquals(LogicalSide.SERVER, event.logicalSide());

        event.setCanceled(true);

        assertTrue(event.isCanceled());
    }

    @Test
    void payloadEventLogicalSideFollowsReceivingConnection()
    {
        NetworkPlayer local = new NetworkPlayer(UUID.randomUUID(), "Local");
        FakeConnection client = new FakeConnection(NetworkSide.CLIENT, NetworkPhase.PLAY, local);
        NetworkPayloadEvent event = new NetworkPayloadEvent(PayloadDirection.CLIENTBOUND,
                client, TYPE, "data");

        assertEquals(NetworkSide.CLIENT, event.side());
        assertEquals(LogicalSide.CLIENT, event.logicalSide());

        assertThrows(NullPointerException.class,
                () -> new NetworkPayloadEvent(null, client, TYPE, "data"));
        assertThrows(NullPointerException.class,
                () -> new NetworkPayloadEvent(PayloadDirection.CLIENTBOUND, null, TYPE, "data"));
        assertThrows(NullPointerException.class,
                () -> new NetworkPayloadEvent(PayloadDirection.CLIENTBOUND, client, null, "data"));
        assertThrows(NullPointerException.class,
                () -> new NetworkPayloadEvent(PayloadDirection.CLIENTBOUND, client, TYPE, null));
    }

    @Test
    void connectedEventExposesConnectionSideAndRemoteMods()
    {
        FakeConnection connection = new FakeConnection(NetworkSide.CLIENT, NetworkPhase.PLAY,
                PLAYER);
        NetworkConnectedEvent event = new NetworkConnectedEvent(connection, MODS);

        assertSame(connection, event.connection());
        assertEquals(NetworkSide.CLIENT, event.side());
        assertEquals(NetworkPhase.PLAY, event.phase());
        assertEquals(PLAYER, event.player());
        assertEquals(MODS, event.remoteMods());
        assertEquals(LogicalSide.CLIENT, event.logicalSide());
        assertFalse(event.isCancelable());
        assertThrows(UnsupportedOperationException.class, () -> event.remoteMods().clear());
        assertThrows(NullPointerException.class, () -> new NetworkConnectedEvent(null, MODS));
        assertThrows(NullPointerException.class,
                () -> new NetworkConnectedEvent(connection, null));
    }

    @Test
    void disconnectedEventExposesConnectionReasonAndGracefulness()
    {
        FakeConnection connection = new FakeConnection(NetworkSide.SERVER, NetworkPhase.PLAY,
                PLAYER);
        NetworkDisconnectedEvent event = new NetworkDisconnectedEvent(connection,
                "connection closed", true);

        assertSame(connection, event.connection());
        assertEquals("connection closed", event.reason());
        assertTrue(event.graceful());
        assertEquals(NetworkSide.SERVER, event.side());
        assertEquals(PLAYER, event.player());
        assertEquals(LogicalSide.SERVER, event.logicalSide());
        assertFalse(event.isCancelable());

        NetworkDisconnectedEvent failure = new NetworkDisconnectedEvent(connection,
                "connection error", false);
        assertFalse(failure.graceful());

        assertThrows(NullPointerException.class,
                () -> new NetworkDisconnectedEvent(null, "reason", true));
        assertThrows(NullPointerException.class,
                () -> new NetworkDisconnectedEvent(connection, null, true));
        assertThrows(IllegalArgumentException.class,
                () -> new NetworkDisconnectedEvent(connection,
                        "a".repeat(NetworkProtocol.MAX_REASON_CHARS + 1), true));
    }

    private static final class FakeConnection implements NetworkConnection
    {
        private final NetworkSide side;
        private final NetworkPhase phase;
        private final NetworkPlayer player;
        private boolean open = true;

        private FakeConnection(NetworkSide side, NetworkPhase phase, NetworkPlayer player)
        {
            this.side = side;
            this.phase = phase;
            this.player = player;
        }

        @Override
        public NetworkSide side()
        {
            return side;
        }

        @Override
        public NetworkPhase phase()
        {
            return phase;
        }

        @Override
        public NetworkPlayer player()
        {
            return player;
        }

        @Override
        public boolean isOpen()
        {
            return open;
        }

        @Override
        public <T> void send(PayloadType<T> type, T payload)
        {
            throw new UnsupportedOperationException("not needed in this test");
        }

        @Override
        public void disconnect(String reason)
        {
            open = false;
        }

        @Override
        public void close()
        {
            open = false;
        }
    }
}
