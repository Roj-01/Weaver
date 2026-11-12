package net.weaver.api.game;

import net.weaver.api.event.events.RegisterEvent;
import net.weaver.api.event.events.RegistryFreezeEvent;
import net.weaver.api.game.registry.GameRegistries;
import net.weaver.api.registry.RegistryException;
import net.weaver.api.registry.RegistryKey;
import net.weaver.api.registry.RegistryManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistryTypedAccessTest {

    @BeforeEach
    void setUp() {
        RegistryManager.reset();
    }

    @AfterEach
    void tearDown() {
        RegistryManager.reset();
    }

    @Test
    void registerEventRejectsNullKey() {
        RegisterEvent event = new RegisterEvent(GameRegistries.ITEM);

        assertThrows(NullPointerException.class,
                () -> event.registry((RegistryKey<Object>) null));
    }

    @Test
    void registerEventRejectsMismatchedKey() {
        RegisterEvent event = new RegisterEvent(GameRegistries.ITEM);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> event.registry(GameRegistries.BLOCK));

        assertTrue(failure.getMessage().contains("minecraft:block"));
        assertTrue(failure.getMessage().contains("minecraft:item"));
    }

    @Test
    void registerEventMatchingKeyReachesTheBackend() {
        RegisterEvent event = new RegisterEvent(GameRegistries.ITEM);

        assertThrows(RegistryException.class, () -> event.registry(GameRegistries.ITEM));
    }

    @Test
    void freezeEventRejectsNullKey() {
        RegistryFreezeEvent event = new RegistryFreezeEvent(GameRegistries.ITEM);

        assertThrows(NullPointerException.class,
                () -> event.registry((RegistryKey<Object>) null));
    }

    @Test
    void freezeEventRejectsMismatchedKey() {
        RegistryFreezeEvent event = new RegistryFreezeEvent(GameRegistries.ITEM);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> event.registry(GameRegistries.BLOCK));

        assertTrue(failure.getMessage().contains("minecraft:block"));
        assertTrue(failure.getMessage().contains("minecraft:item"));
    }

    @Test
    void freezeEventMatchingKeyReachesTheBackend() {
        RegistryFreezeEvent event = new RegistryFreezeEvent(GameRegistries.ITEM);

        assertThrows(RegistryException.class, () -> event.registry(GameRegistries.ITEM));
    }
}
