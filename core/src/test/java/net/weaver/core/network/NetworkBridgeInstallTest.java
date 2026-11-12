package net.weaver.core.network;

import net.weaver.core.BootPhaseOrder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkBridgeInstallTest
{
    @BeforeEach
    void markTransformerPhase()
    {
        BootPhaseOrder.markTransformersInstalled();
    }

    @AfterEach
    void clearInstallState()
    {
        NetworkFeature.uninstall();
        BootPhaseOrder.resetForTests();
    }

    @Test
    void armReadsTheBridgeResourceWhenPresentAndFailsCleanlyOtherwise()
    {
        List<String> lines = new ArrayList<>();
        List<String> names = new ArrayList<>();
        boolean resourcePresent = getClass().getClassLoader()
                .getResource(NetworkBridgeInstall.NETWORK_MIXIN_CONFIG) != null;

        boolean armed = NetworkBridgeInstall.arm(getClass().getClassLoader(),
                (name, bytes) -> names.add(name), lines::add);

        if (resourcePresent)
        {
            assertTrue(armed);
            assertTrue(names.contains(NetworkBridgeInstall.NETWORK_MIXIN_CONFIG), names.toString());
        }
        else
        {
            assertFalse(armed);
            assertTrue(names.isEmpty());
            assertTrue(lines.stream().anyMatch(line -> line.contains("mixin config missing")),
                    String.join("\n", lines));
        }
    }

    @Test
    void activateWithoutAGameLoaderLeavesTheFeatureUninstalled()
    {
        List<String> lines = new ArrayList<>();

        boolean installed = NetworkBridgeInstall.activate(null, true, lines::add);

        assertFalse(installed);
        assertFalse(NetworkFeature.isInstalled());
        assertTrue(lines.stream().anyMatch(line -> line.contains("no-game-loader")),
                String.join("\n", lines));
    }

    @Test
    void activateWithoutTheBridgeArtifactIsFailureIsolated()
    {
        List<String> lines = new ArrayList<>();

        boolean installed = NetworkBridgeInstall.activate(getClass().getClassLoader(), true,
                lines::add);

        assertFalse(installed);
        assertFalse(NetworkFeature.isInstalled());
        assertTrue(lines.stream().anyMatch(line -> line.contains("NETWORK BRIDGE")),
                String.join("\n", lines));
    }
}
