package net.weaver.core.network;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkFeatureTest
{
    @AfterEach
    void clearInstallState()
    {
        NetworkFeature.uninstall();
    }

    @Test
    void targetsAreImmutableAndNonEmpty()
    {
        List<NetworkFeature.Target> targets = NetworkFeature.targets();

        assertFalse(targets.isEmpty());
        assertEquals(5, targets.size());
        assertThrows(UnsupportedOperationException.class, targets::clear);
        assertThrows(UnsupportedOperationException.class,
                () -> targets.add(targets.get(0)));
    }

    @Test
    void targetsIncludeVerifiedPayloadAndDisconnectDescriptors()
    {
        List<NetworkFeature.Target> targets = NetworkFeature.targets();

        assertTrue(hasTarget(targets, "CLIENT",
                "net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl",
                "handleCustomPayload",
                "(Lnet/minecraft/network/protocol/common/ClientboundCustomPayloadPacket;)V"));
        assertTrue(hasTarget(targets, "SERVER",
                "net.minecraft.server.network.ServerCommonPacketListenerImpl",
                "handleCustomPayload",
                "(Lnet/minecraft/network/protocol/common/ServerboundCustomPayloadPacket;)V"));
        assertTrue(hasTarget(targets, "SERVER",
                "net.minecraft.server.network.ServerGamePacketListenerImpl",
                "handleCustomPayload",
                "(Lnet/minecraft/network/protocol/common/ServerboundCustomPayloadPacket;)V"));
        assertTrue(hasTarget(targets, "CLIENT",
                "net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl",
                "onDisconnect",
                "(Lnet/minecraft/network/DisconnectionDetails;)V"));
        assertTrue(hasTarget(targets, "SERVER",
                "net.minecraft.server.network.ServerGamePacketListenerImpl",
                "onDisconnect",
                "(Lnet/minecraft/network/DisconnectionDetails;)V"));
    }

    @Test
    void everyTargetHasAUsefulPurpose()
    {
        for (NetworkFeature.Target target : NetworkFeature.targets())
        {
            assertFalse(target.side().isBlank());
            assertFalse(target.owner().isBlank());
            assertFalse(target.method().isBlank());
            assertFalse(target.descriptor().isBlank());
            assertFalse(target.purpose().isBlank());
        }
    }

    @Test
    void defaultBlockerMentionsDiscardedPayloadAndPreDecodeLimitation()
    {
        String blocker = NetworkFeature.blocker();

        assertTrue(blocker.contains("DiscardedPayload"), blocker);
        assertTrue(blocker.contains("Codec registration"), blocker);
        assertTrue(blocker.contains("pre-decode hook"), blocker);
        assertTrue(blocker.contains("keeps only the payload id"), blocker);
    }

    @Test
    void featureIsNotInstalledByDefault()
    {
        assertFalse(NetworkFeature.isInstalled());
        assertFalse(NetworkFeature.mixinsArmed());
        assertFalse(NetworkFeature.gameClassesPresent());
    }

    @Test
    void installRequiresBothMixinsAndGameClasses()
    {
        assertFalse(NetworkFeature.install(false, true));
        assertFalse(NetworkFeature.isInstalled());
        assertTrue(NetworkFeature.blocker().contains("was not armed"),
                NetworkFeature.blocker());

        assertFalse(NetworkFeature.install(true, false));
        assertFalse(NetworkFeature.isInstalled());
        assertTrue(NetworkFeature.blocker().contains("not present"),
                NetworkFeature.blocker());
    }

    @Test
    void installWithBothPrerequisitesIsVisible()
    {
        assertTrue(NetworkFeature.install(true, true));
        assertTrue(NetworkFeature.isInstalled());
        assertTrue(NetworkFeature.mixinsArmed());
        assertTrue(NetworkFeature.gameClassesPresent());
        assertEquals("", NetworkFeature.blocker());
    }

    @Test
    void uninstallRestoresTheDefaultState()
    {
        assertTrue(NetworkFeature.install(true, true));

        NetworkFeature.uninstall();

        assertFalse(NetworkFeature.isInstalled());
        assertTrue(NetworkFeature.blocker().contains("DiscardedPayload"),
                NetworkFeature.blocker());
    }

    private static boolean hasTarget(List<NetworkFeature.Target> targets, String side,
                                     String owner, String method, String descriptor)
    {
        return targets.stream().anyMatch(target ->
                target.side().equals(side)
                        && target.owner().equals(owner)
                        && target.method().equals(method)
                        && target.descriptor().equals(descriptor));
    }
}
