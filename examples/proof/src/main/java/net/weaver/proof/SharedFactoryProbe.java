package net.weaver.proof;

import net.weaver.api.event.EventBus;
import net.weaver.api.spi.GameEventFactory;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Proves that the typed factory and the base API resolve through a parent-first
 * loader built from the same entries a shared class space would use.
 *
 * <p>The probe is deliberately independent of the game launch. It builds a
 * {@link URLClassLoader} over {@code java.class.path} plus any extra entries
 * passed as arguments, with the system loader as parent, then loads the typed
 * factory through it. It proves factory linkability and base-class identity; it
 * does not prove that game classes are hosted by the shared loader.
 */
public final class SharedFactoryProbe {

    private SharedFactoryProbe() {
    }

    /**
     * Runs the probe.
     *
     * @param args extra class path entries, typically the proof mod jar
     */
    public static void main(String[] args) {
        try {
            List<URL> urls = new ArrayList<>();
            String classPath = System.getProperty("java.class.path", "");
            for (String entry : classPath.split(File.pathSeparator)) {
                if (!entry.isBlank()) {
                    urls.add(Path.of(entry).toUri().toURL());
                }
            }
            for (String entry : args) {
                if (!entry.isBlank()) {
                    urls.add(Path.of(entry).toUri().toURL());
                }
            }
            ClassLoader parent = SharedFactoryProbe.class.getClassLoader();
            try (URLClassLoader loader = new URLClassLoader("weaver-shared-probe",
                    urls.toArray(URL[]::new), parent)) {
                Class<?> typed = Class.forName("net.weaver.api.game.TypedGameEventFactory",
                        true, loader);
                if (!GameEventFactory.class.isAssignableFrom(typed)) {
                    throw new IllegalStateException("typed factory is not a GameEventFactory");
                }
                Object instance = typed.getDeclaredConstructor().newInstance();
                Object event = typed.getMethod("entityHurt", Object.class, Object.class,
                        Object.class).invoke(instance, new Object(), new Object(), new Object());
                if (event == null) {
                    throw new IllegalStateException("typed factory returned a null event");
                }
                Class<?> bus = loader.loadClass("net.weaver.api.event.EventBus");
                if (bus != EventBus.class) {
                    throw new IllegalStateException("EventBus identity mismatch");
                }
                System.out.println("SHARED FACTORY PROBE ok=true factory=" + typed.getName()
                        + " event=" + event.getClass().getName());
            }
        } catch (Throwable failure) {
            System.out.println("SHARED FACTORY PROBE ok=false reason=" + failure);
            failure.printStackTrace(System.out);
            System.exit(1);
        }
    }
}
