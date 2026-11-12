package net.weaver.core;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Bootstrap main class for the opt-in shared class space.
 *
 * <p>The JVM starts this class through the system loader after the Weaver agent
 * has created the {@link ClassSpace}. The game main class named by
 * {@code -Dweaver.shared.main} is then loaded from the shared game loader, so
 * every game and mod class is defined by that loader and the loader-gated
 * transforms apply. The thread context classloader is set to the shared loader
 * for the duration of the game main call.
 */
public final class SharedMain {

    private SharedMain() {
    }

    /**
     * Loads and invokes the game main class from the shared class space.
     *
     * @param args the game arguments passed through unchanged
     * @throws Exception when the agent did not create a class space, the main
     *                   class property is blank, or the game main fails
     */
    public static void main(String[] args) throws Exception {
        ClassSpace space = LoaderAgent.classSpace();
        if (space == null) {
            System.out.println("[weaver] SHARED MAIN FAILED reason=class-space-not-initialized"
                    + " action=check--javaagent");
            System.exit(2);
            return;
        }
        String mainClass = System.getProperty("weaver.shared.main", "").trim();
        if (mainClass.isEmpty()) {
            System.out.println("[weaver] SHARED MAIN FAILED reason=weaver.shared.main-not-set");
            System.exit(2);
            return;
        }
        ClassLoader loader = space.gameLoader();
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        thread.setContextClassLoader(loader);
        System.out.println("[weaver] SHARED MAIN class=" + mainClass
                + " loader=" + loader.getName());
        try {
            Class<?> type = Class.forName(mainClass, true, loader);
            Method main = type.getMethod("main", String[].class);
            main.invoke(null, (Object) args);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Error error) {
                throw error;
            }
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw failure;
        } finally {
            thread.setContextClassLoader(previous);
        }
    }
}
