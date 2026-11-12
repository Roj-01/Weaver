package net.weaver.core;

import net.weaver.api.event.events.EntityHurtEvent;
import net.weaver.api.spi.GameEventFactory;
import net.weaver.core.hook.GameEventFactoryHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Verifies the typed-factory install and fallback path through a class loader.
 *
 * <p>The success case loads the real {@code api-game} factory when that optional
 * artifact is on the test runtime class path; it is skipped on a tree built
 * without the game class path. The failure case uses a filtering loader that
 * reports the factory class as missing, which is the same condition the agent
 * sees when the optional artifact is absent.
 */
class LoaderAgentFactoryTest {

    private static final String TYPED_FACTORY = "net.weaver.api.game.TypedGameEventFactory";
    private static final String TYPED_HURT = "net.weaver.api.game.event.EntityHurtEvent";

    @AfterEach
    void tearDown() {
        GameEventFactoryHolder.resetForTests();
    }

    @Test
    void installsTypedFactoryWhenVisible() {
        assumeTrue(isOnClassPath(TYPED_FACTORY),
                "api-game is not on the test runtime; run with the game class path");
        List<String> log = new ArrayList<>();

        boolean installed = LoaderAgent.installGameEventFactory(
                LoaderAgentFactoryTest.class.getClassLoader(), log::add);

        assertTrue(installed);
        assertTrue(GameEventFactoryHolder.isTyped());
        assertTrue(log.stream().anyMatch(line -> line.contains("TYPED EVENTS enabled")),
                () -> "missing enabled log: " + log);
        Object event = GameEventFactoryHolder.events()
                .entityHurt(new Object(), new Object(), new Object());
        assertTrue(isInstance(TYPED_HURT, event),
                "typed factory must create the typed observation event");
    }

    @Test
    void keepsBaseFactoryWhenTypedClassIsMissing() {
        List<String> log = new ArrayList<>();
        ClassLoader missing = new ClassLoader(LoaderAgentFactoryTest.class.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve)
                    throws ClassNotFoundException {
                if (TYPED_FACTORY.equals(name)) {
                    throw new ClassNotFoundException(name);
                }
                return super.loadClass(name, resolve);
            }
        };

        boolean installed = LoaderAgent.installGameEventFactory(missing, log::add);

        assertFalse(installed);
        assertFalse(GameEventFactoryHolder.isTyped());
        assertEquals("base", GameEventFactoryHolder.description());
        GameEventFactory factory = GameEventFactoryHolder.events();
        assertNotNull(factory);
        Object event = factory.entityHurt(new Object(), new Object(), new Object());
        assertInstanceOf(EntityHurtEvent.class, event, "fallback must create the base event");
        if (isOnClassPath(TYPED_HURT)) {
            assertFalse(isInstance(TYPED_HURT, event),
                    "fallback must not create the typed event when the factory is missing");
        }
        assertEquals(1, log.stream()
                .filter(line -> line.startsWith("[weaver] TYPED EVENTS unavailable reason="))
                .count(), () -> "expected exactly one diagnostic: " + log);
    }

    private static boolean isInstance(String className, Object value) {
        try {
            return Class.forName(className, false, LoaderAgentFactoryTest.class.getClassLoader())
                    .isInstance(value);
        } catch (ClassNotFoundException missing) {
            throw new AssertionError("class not on the test class path: " + className, missing);
        }
    }

    private static boolean isOnClassPath(String className) {
        try {
            Class.forName(className, false, LoaderAgentFactoryTest.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError missing) {
            return false;
        }
    }
}
