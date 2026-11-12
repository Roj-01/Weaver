package net.weaver.core.capability;

import net.weaver.api.capability.AttachmentRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapabilityStoresTest
{
    @Test
    void isolatedRegistryInstallsTheCoreStoreReflectively()
    {
        AttachmentRegistry registry = new AttachmentRegistry();

        assertTrue(CapabilityStores.isInstalled(registry));
    }

    @Test
    void installIsIdempotentForOneRegistry()
    {
        AttachmentRegistry registry = new AttachmentRegistry();
        CapabilityStores.uninstall(registry);

        assertFalse(CapabilityStores.isInstalled(registry));
        assertTrue(CapabilityStores.install(registry));
        assertFalse(CapabilityStores.install(registry));
        assertTrue(CapabilityStores.isInstalled(registry));
        assertTrue(CapabilityStores.uninstall(registry));
        assertFalse(CapabilityStores.uninstall(registry));
        assertFalse(CapabilityStores.isInstalled(registry));
    }

    @Test
    void installationIsTrackedPerRegistryIdentity()
    {
        AttachmentRegistry first = new AttachmentRegistry();
        AttachmentRegistry second = new AttachmentRegistry();
        CapabilityStores.uninstall(first);
        CapabilityStores.uninstall(second);

        assertTrue(CapabilityStores.install(first));
        assertTrue(CapabilityStores.install(second));
        assertTrue(CapabilityStores.isInstalled(first));
        assertTrue(CapabilityStores.isInstalled(second));

        assertTrue(CapabilityStores.uninstall(first));
        assertFalse(CapabilityStores.isInstalled(first));
        assertTrue(CapabilityStores.isInstalled(second));
    }

    @Test
    void installIfPossibleNeverThrowsAndTargetsTheGlobalRegistry()
    {
        assertDoesNotThrow(CapabilityStores::installIfPossible);
        assertTrue(CapabilityStores.isInstalled(AttachmentRegistry.global()));
    }

    @Test
    void nullRegistriesAreRejected()
    {
        assertThrows(NullPointerException.class, () -> CapabilityStores.install(null));
        assertThrows(NullPointerException.class, () -> CapabilityStores.uninstall(null));
        assertThrows(NullPointerException.class,
                () -> CapabilityStores.isInstalled(null));
    }
}
