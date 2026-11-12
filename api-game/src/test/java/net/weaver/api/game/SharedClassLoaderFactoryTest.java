package net.weaver.api.game;

import net.weaver.api.event.EventBus;
import net.weaver.api.spi.GameEventFactory;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the shared-class-space factory path without launching a game.
 *
 * <p>A shared-style {@link URLClassLoader} is built over the current Java class
 * path with the system/test loader as parent, mirroring
 * {@code SharedModClassLoader}. Because the loader is parent-first, the typed
 * factory links against the same base API classes as the rest of the process;
 * there is no duplicate {@link EventBus} identity.
 */
class SharedClassLoaderFactoryTest {

    @Test
    void sharedStyleLoaderLoadsTypedFactoryAndSharesBaseClasses() throws Exception {
        URLClassLoader loader = new URLClassLoader(classPathUrls(), getClass().getClassLoader());
        try {
            Class<?> typed = Class.forName("net.weaver.api.game.TypedGameEventFactory", true, loader);

            assertTrue(GameEventFactory.class.isAssignableFrom(typed),
                    "typed factory must implement the base SPI loaded from the parent");

            GameEventFactory factory = (GameEventFactory) typed.getDeclaredConstructor().newInstance();
            assertNotNull(factory.entityHurt(new Object(), new Object(), new Object()),
                    "the factory must create a base observation event");
            assertNotNull(factory.playerJoin(new Object(), new Object()),
                    "the factory must create a base player event");

            assertSame(EventBus.class, loader.loadClass("net.weaver.api.event.EventBus"),
                    "the base API must be shared with the parent, not duplicated");
        } finally {
            loader.close();
        }
    }

    private static URL[] classPathUrls() throws Exception {
        List<URL> urls = new ArrayList<>();
        for (String entry : System.getProperty("java.class.path", "").split(File.pathSeparator)) {
            if (!entry.isBlank()) {
                urls.add(new File(entry).toURI().toURL());
            }
        }
        return urls.toArray(URL[]::new);
    }
}
