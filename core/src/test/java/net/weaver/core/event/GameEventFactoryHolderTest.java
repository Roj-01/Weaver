package net.weaver.core.event;

import net.weaver.api.event.events.PlayerJoinEvent;
import net.weaver.api.event.events.PlayerLoginEvent;
import net.weaver.api.spi.DefaultGameEventFactory;
import net.weaver.core.hook.GameEventFactoryHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GameEventFactoryHolderTest {

    @AfterEach
    void tearDown() {
        GameEventFactoryHolder.resetForTests();
    }

    @Test
    void resetRestoresUsableBaseFactory() {
        GameEventFactoryHolder.resetForTests();

        assertFalse(GameEventFactoryHolder.isTyped());
        assertEquals("base", GameEventFactoryHolder.description());
        assertNotNull(GameEventFactoryHolder.events());
        assertInstanceOf(PlayerLoginEvent.class,
                GameEventFactoryHolder.events().playerLogin(new Object(), new Object()));
    }

    @Test
    void installsExplicitBaseFactory() {
        GameEventFactoryHolder.install(new DefaultGameEventFactory());

        assertNotNull(GameEventFactoryHolder.events());
        assertEquals(DefaultGameEventFactory.class.getName(),
                GameEventFactoryHolder.description());
        assertInstanceOf(PlayerJoinEvent.class,
                GameEventFactoryHolder.events().playerJoin(new Object(), new Object()));
    }
}
