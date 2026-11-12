package net.example.agent;

import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.util.jar.JarFile;

/**
 * Agent for the classloading spike.
 *
 * <p>System mode appends the mod jar to the system classloader search and arms the
 * mixin config against that loader. Shared mode does nothing here; the main class
 * creates a shared URLClassLoader and arms the config itself.
 */
public final class ProbeAgent {

    private static JarFile appended;

    private ProbeAgent() {
    }

    public static void premain(String args, Instrumentation instrumentation) {
        try {
            String mode = value(args, "mode", "system");
            String modJar = value(args, "modJar", "");
            String configName = value(args, "config", "spike.mixins.json");
            System.out.println("SPIKE AGENT mode=" + mode + " modJar=" + modJar);

            if ("system".equals(mode)) {
                if (modJar.isBlank()) {
                    throw new IllegalStateException("modJar is required in system mode");
                }
                appended = new JarFile(modJar);
                instrumentation.appendToSystemClassLoaderSearch(appended);
                System.out.println("SPIKE AGENT appended=true");

                byte[] config = readEntry(modJar, configName);
                arm(configName, config, ClassLoader.getSystemClassLoader());
                System.out.println("SPIKE AGENT armed=true");
            } else {
                System.out.println("SPIKE AGENT append=skipped mode=" + mode);
            }
            System.setProperty("spike.agent.status", "ok");
        } catch (Throwable failure) {
            System.setProperty("spike.agent.status", "failed:" + failure);
            System.out.println("SPIKE AGENT FAILED " + failure);
            failure.printStackTrace(System.out);
        }
    }

    private static void arm(String configName, byte[] config, ClassLoader loader) throws Exception {
        Class<?> engineClass = Class.forName("net.weaver.mixin.core.MixinEngine", true,
                ClassLoader.getSystemClassLoader());
        Object engine = engineClass.getMethod("get").invoke(null);
        Method addConfig = engineClass.getMethod("addConfig", String.class, String.class,
                byte[].class, ClassLoader.class);
        addConfig.invoke(engine, "spike", configName, config, loader);
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

    private static String value(String args, String key, String fallback) {
        if (args == null || args.isBlank()) {
            return fallback;
        }
        for (String part : args.split(";")) {
            int equals = part.indexOf('=');
            if (equals > 0 && key.equals(part.substring(0, equals).trim())) {
                return part.substring(equals + 1).trim();
            }
        }
        return fallback;
    }
}
