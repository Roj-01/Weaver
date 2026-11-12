package net.example.probe;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.jar.JarFile;

/** Runs the system-loader and shared-loader cases of the classloading spike. */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        String mode = argument(args, "--mode=", "system");
        String gameJar = argument(args, "--game=", "");
        String modJar = argument(args, "--mod=", "");
        String agent = System.getProperty("spike.agent.status", "not-run");
        if (!"ok".equals(agent)) {
            throw new IllegalStateException("agent did not finish: " + agent);
        }
        if ("system".equals(mode)) {
            runSystem(gameJar, modJar);
        } else if ("shared".equals(mode)) {
            runShared(gameJar, modJar);
        } else {
            throw new IllegalArgumentException("unknown mode: " + mode);
        }
    }

    private static void runSystem(String gameJar, String modJar) throws Exception {
        ClassLoader loader = ClassLoader.getSystemClassLoader();
        Class<?> game = Class.forName("net.example.game.GameTarget", true, loader);
        Class<?> mod = Class.forName("net.example.mod.ModBlock", true, loader);
        check(mod.getSuperclass() == game, "system superclass identity");

        Class<?> hooks = Class.forName("net.example.mod.ModHooks", true, loader);
        Method reset = hooks.getMethod("reset");
        Method hits = hooks.getMethod("hits");
        reset.invoke(null);

        Object instance = game.getDeclaredConstructor().newInstance();
        game.getMethod("tick").invoke(instance);
        int count = (int) hits.invoke(null);
        check(count == 1, "system mixin called mod hook");
        System.out.println("CLASSLOADING SPIKE mode=system ok=true hits=" + count
                + " superclass=true game=" + gameJar + " mod=" + modJar);
    }

    private static void runShared(String gameJar, String modJar) throws Exception {
        URL[] urls = {
                Path.of(gameJar).toUri().toURL(),
                Path.of(modJar).toUri().toURL()
        };
        try (URLClassLoader loader = new URLClassLoader("weaver-shared-spike", urls,
                ClassLoader.getSystemClassLoader())) {
            byte[] config = readEntry(modJar, "spike.mixins.json");
            armShared(config, loader);

            Class<?> game = Class.forName("net.example.game.GameTarget", true, loader);
            Class<?> mod = Class.forName("net.example.mod.ModBlock", true, loader);
            check(mod.getSuperclass() == game, "shared superclass identity");
            check(game.getClassLoader() == loader, "game class loader is shared loader");

            Class<?> hooks = Class.forName("net.example.mod.ModHooks", true, loader);
            Method reset = hooks.getMethod("reset");
            Method hits = hooks.getMethod("hits");
            reset.invoke(null);

            Object instance = game.getDeclaredConstructor().newInstance();
            game.getMethod("tick").invoke(instance);
            int count = (int) hits.invoke(null);
            check(count == 1, "shared mixin called mod hook");
            System.out.println("CLASSLOADING SPIKE mode=shared ok=true hits=" + count
                    + " superclass=true loader=" + loader.getName());
        }
    }

    private static void armShared(byte[] config, ClassLoader loader) throws Exception {
        Class<?> engineClass = Class.forName("net.weaver.mixin.core.MixinEngine", true,
                ClassLoader.getSystemClassLoader());
        Object engine = engineClass.getMethod("get").invoke(null);
        Method addConfig = engineClass.getMethod("addConfig", String.class, String.class,
                byte[].class, ClassLoader.class);
        addConfig.invoke(engine, "spike-shared", "spike.mixins.json", config, loader);
    }

    private static byte[] readEntry(String jarPath, String entry) throws IOException {
        try (JarFile jar = new JarFile(jarPath)) {
            var source = jar.getJarEntry(entry);
            if (source == null) {
                throw new IOException("missing jar entry: " + entry);
            }
            try (InputStream in = jar.getInputStream(source)) {
                return in.readAllBytes();
            }
        }
    }

    private static String argument(String[] args, String prefix, String fallback) {
        for (String arg : args) {
            if (arg.startsWith(prefix)) {
                return arg.substring(prefix.length());
            }
        }
        return fallback;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("check failed: " + message);
        }
        System.out.println("SPIKE CHECK ok=true " + message);
    }
}
