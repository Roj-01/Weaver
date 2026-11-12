package net.weaver.api.network;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkRegistryTest
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

    private static final PayloadHandler<String> NO_OP = (payload, context) ->
    {
    };

    private static final PayloadType<String> ALPHA =
            PayloadType.of("alpha", "telemetry", STRINGS);
    private static final PayloadType<String> BETA =
            PayloadType.of("beta", "events", STRINGS);

    @Test
    void clientboundAndServerboundRegistrationAndLookup()
    {
        NetworkRegistry registry = new NetworkRegistry();
        PayloadRegistration<String> clientbound =
                registry.registerClientbound("alpha", ALPHA, NO_OP);
        PayloadRegistration<String> serverbound =
                registry.registerServerbound("beta", BETA, NO_OP);

        assertSame(clientbound, registry.find(PayloadDirection.CLIENTBOUND,
                NetworkPhase.PLAY, ALPHA.channel()).orElseThrow());
        assertSame(serverbound, registry.find(PayloadDirection.SERVERBOUND,
                NetworkPhase.PLAY, BETA.channel()).orElseThrow());
        assertTrue(registry.find(PayloadDirection.CLIENTBOUND, NetworkPhase.PLAY,
                BETA.channel()).isEmpty());
        assertTrue(registry.find(PayloadDirection.CLIENTBOUND, NetworkPhase.CONFIGURATION,
                ALPHA.channel()).isEmpty());

        assertEquals(2, registry.size());
        assertEquals(1, registry.registrations(PayloadDirection.CLIENTBOUND).size());
        assertEquals(1, registry.registrations(PayloadDirection.SERVERBOUND).size());
        assertEquals(2, registry.registrations().size());
        assertEquals(PayloadDirection.CLIENTBOUND, clientbound.direction());
        assertEquals(NetworkPhase.PLAY, clientbound.phase());
        assertEquals("alpha", clientbound.modId());
        assertEquals("telemetry", clientbound.path());
        assertEquals(1, clientbound.version());
        assertEquals("CLIENTBOUND/PLAY/alpha:telemetry/v1", clientbound.key());
        assertSame(ALPHA, clientbound.type());
        assertSame(NO_OP, clientbound.handler());
    }

    @Test
    void duplicateDirectionPhaseAndChannelIsRejectedWithUsefulMessage()
    {
        NetworkRegistry registry = new NetworkRegistry();
        registry.registerServerbound("alpha", ALPHA, NO_OP);

        NetworkRegistrationException failure = assertThrows(NetworkRegistrationException.class,
                () -> registry.registerServerbound("alpha", ALPHA, NO_OP));

        String message = failure.getMessage();
        assertTrue(message.contains("Duplicate network registration SERVERBOUND/PLAY/"
                + ALPHA.channel()), message);
        assertTrue(message.contains("owned by mod 'alpha'"), message);
        assertTrue(message.contains("existing registration"), message);
    }

    @Test
    void sameChannelInDifferentPhasesIsAllowed()
    {
        NetworkRegistry registry = new NetworkRegistry();
        PayloadRegistration<String> play =
                registry.registerServerbound("alpha", ALPHA, NetworkPhase.PLAY, NO_OP);
        PayloadRegistration<String> configuration =
                registry.registerServerbound("alpha", ALPHA, NetworkPhase.CONFIGURATION, NO_OP);

        assertEquals(2, registry.size());
        assertEquals(NetworkPhase.PLAY, play.phase());
        assertEquals(NetworkPhase.CONFIGURATION, configuration.phase());
        assertEquals(NetworkPhase.CONFIGURATION,
                registry.find(PayloadDirection.SERVERBOUND, NetworkPhase.CONFIGURATION,
                        ALPHA.channel()).orElseThrow().phase());
        assertEquals(List.of(ALPHA.channel()), registry.channels());
    }

    @Test
    void sameChannelInDifferentDirectionsIsAllowedAndDeduplicatedInChannels()
    {
        NetworkRegistry registry = new NetworkRegistry();
        registry.registerClientbound("alpha", ALPHA, NO_OP);
        registry.registerServerbound("alpha", ALPHA, NO_OP);

        assertEquals(2, registry.size());
        assertEquals(List.of(ALPHA.channel()), registry.channels());
        assertEquals(PayloadDirection.CLIENTBOUND, registry.find(PayloadDirection.CLIENTBOUND,
                NetworkPhase.PLAY, ALPHA.channel()).orElseThrow().direction());
        assertEquals(PayloadDirection.SERVERBOUND, registry.find(PayloadDirection.SERVERBOUND,
                NetworkPhase.PLAY, ALPHA.channel()).orElseThrow().direction());
    }

    @Test
    void modIdMustMatchPayloadTypeOwner()
    {
        NetworkRegistry registry = new NetworkRegistry();

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> registry.registerClientbound("beta", ALPHA, NO_OP));

        assertTrue(failure.getMessage().contains(
                "modId 'beta' does not match payload type owner 'alpha'"),
                failure.getMessage());
        assertEquals(0, registry.size());
    }

    @Test
    void optionalMetadataIsRetained()
    {
        NetworkRegistry registry = new NetworkRegistry();
        PayloadRegistration<String> optional = registry.registerServerbound("alpha", ALPHA,
                NetworkPhase.PLAY, true, NO_OP);
        PayloadRegistration<String> required = registry.registerServerbound("beta", BETA,
                NetworkPhase.PLAY, NO_OP);

        assertTrue(optional.optional());
        assertFalse(required.optional());
        assertTrue(registry.find(PayloadDirection.SERVERBOUND, NetworkPhase.PLAY,
                ALPHA.channel()).orElseThrow().optional());
    }

    @Test
    void snapshotsAreImmutableAndIndependentOfLaterRegistrations()
    {
        NetworkRegistry registry = new NetworkRegistry();
        registry.registerServerbound("alpha", ALPHA, NO_OP);
        List<PayloadRegistration<?>> snapshot = registry.registrations();
        List<String> channels = registry.channels();

        registry.registerServerbound("beta", BETA, NO_OP);

        assertEquals(1, snapshot.size());
        assertEquals(1, channels.size());
        assertEquals(2, registry.registrations().size());
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
        assertThrows(UnsupportedOperationException.class, channels::clear);
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.add(snapshot.get(0)));
    }

    @Test
    void channelsAreSortedAndUnique()
    {
        NetworkRegistry registry = new NetworkRegistry();
        registry.registerServerbound("beta", BETA, NO_OP);
        registry.registerClientbound("alpha", ALPHA, NO_OP);
        registry.registerServerbound("alpha", ALPHA, NetworkPhase.CONFIGURATION, NO_OP);

        assertEquals(List.of("alpha:telemetry/v1", "beta:events/v1"), registry.channels());
    }

    @Test
    void findAnyPhaseReturnsFirstRegisteredPhase()
    {
        NetworkRegistry registry = new NetworkRegistry();
        registry.registerServerbound("alpha", ALPHA, NetworkPhase.CONFIGURATION, NO_OP);
        registry.registerServerbound("alpha", ALPHA, NetworkPhase.PLAY, NO_OP);

        PayloadRegistration<?> found = registry.findAnyPhase(PayloadDirection.SERVERBOUND,
                ALPHA.channel()).orElseThrow();

        assertEquals(NetworkPhase.CONFIGURATION, found.phase());
        assertTrue(registry.findAnyPhase(PayloadDirection.CLIENTBOUND, ALPHA.channel())
                .isEmpty());
    }

    @Test
    void clearRemovesEverything()
    {
        NetworkRegistry registry = new NetworkRegistry();
        registry.registerServerbound("alpha", ALPHA, NO_OP);
        registry.registerClientbound("beta", BETA, NO_OP);

        registry.clear();

        assertEquals(0, registry.size());
        assertEquals(List.of(), registry.registrations());
        assertEquals(List.of(), registry.channels());
        assertTrue(registry.find(PayloadDirection.SERVERBOUND, NetworkPhase.PLAY,
                ALPHA.channel()).isEmpty());
    }

    @Test
    void nullArgumentsAreRejected()
    {
        NetworkRegistry registry = new NetworkRegistry();

        assertThrows(NullPointerException.class,
                () -> registry.registerServerbound(null, ALPHA, NO_OP));
        assertThrows(NullPointerException.class,
                () -> registry.registerServerbound("alpha", null, NO_OP));
        assertThrows(NullPointerException.class,
                () -> registry.registerServerbound("alpha", ALPHA, null));
        assertThrows(NullPointerException.class,
                () -> registry.registerServerbound("alpha", ALPHA, (NetworkPhase) null, NO_OP));
        assertThrows(NullPointerException.class,
                () -> registry.registerClientbound("alpha", ALPHA, null));
        assertThrows(NullPointerException.class,
                () -> registry.find(null, NetworkPhase.PLAY, ALPHA.channel()));
        assertThrows(NullPointerException.class,
                () -> registry.find(PayloadDirection.CLIENTBOUND, null, ALPHA.channel()));
        assertThrows(NullPointerException.class,
                () -> registry.find(PayloadDirection.CLIENTBOUND, NetworkPhase.PLAY, null));
        assertThrows(NullPointerException.class, () -> registry.registrations(null));
        assertThrows(NullPointerException.class, () -> registry.findAnyPhase(null, "x"));
        assertThrows(NullPointerException.class,
                () -> registry.findAnyPhase(PayloadDirection.CLIENTBOUND, null));
    }

    @Test
    void registrationEqualityAndToStringUseDirectionPhaseAndChannel()
    {
        NetworkRegistry first = new NetworkRegistry();
        NetworkRegistry second = new NetworkRegistry();
        PayloadRegistration<String> left = first.registerServerbound("alpha", ALPHA, NO_OP);
        PayloadRegistration<String> right = second.registerServerbound("alpha", ALPHA,
                (payload, context) ->
                {
                });
        PayloadRegistration<String> otherPhase = second.registerServerbound("alpha", ALPHA,
                NetworkPhase.CONFIGURATION, NO_OP);

        assertEquals(left, right);
        assertEquals(left.hashCode(), right.hashCode());
        assertFalse(left.equals(otherPhase));
        assertEquals("PayloadRegistration[SERVERBOUND/PLAY/alpha:telemetry/v1, optional=false]",
                left.toString());
    }
}
