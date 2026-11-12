package net.weaver.core;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** One mod jar, read once and kept open for the system classloader. */
public final class ModJar implements Closeable {

    private final Path path;
    private final JarFile jarFile;
    private final ModMetadata metadata;
    private final Map<String, byte[]> mixinConfigs;
    private final String accessTransformers;
    private final List<String> classEntries;
    private final byte[] enumExtensions;

    /** Where a mod lists the constants it adds to enums. */
    public static final String ENUM_EXTENSIONS = "META-INF/enumextensions.json";

    private ModJar(Path path, JarFile jarFile, ModMetadata metadata,
                   Map<String, byte[]> mixinConfigs, String accessTransformers,
                   List<String> classEntries, byte[] enumExtensions) {
        this.path = path;
        this.jarFile = jarFile;
        this.metadata = metadata;
        this.mixinConfigs = Map.copyOf(mixinConfigs);
        this.accessTransformers = accessTransformers;
        this.classEntries = List.copyOf(classEntries);
        this.enumExtensions = enumExtensions;
    }

    /** Largest descriptor, mixin config, or access transformer read from a mod jar. */
    static final int MAX_TEXT_ENTRY_BYTES = 4 * 1024 * 1024;

    public static ModJar read(Path path) throws IOException {
        JarFile jar = new JarFile(path.toFile());
        boolean done = false;
        try {
            JarEntry descriptor = jar.getJarEntry(ModMetadata.FILE);
            if (descriptor == null) {
                throw new IOException("missing " + ModMetadata.FILE);
            }
            ModMetadata metadata = ModMetadata.parse(path.getFileName().toString(), read(jar, descriptor));
            Map<String, byte[]> configs = new LinkedHashMap<>();
            for (String name : metadata.mixins()) {
                byte[] bytes = readEntry(jar, name);
                if (bytes == null) {
                    bytes = readEntry(jar, "mixins/" + name);
                }
                if (bytes == null) {
                    throw new IOException("missing mixin config: " + name);
                }
                configs.put(name, bytes);
            }
            StringBuilder transformers = new StringBuilder();
            for (String name : metadata.accessTransformers()) {
                byte[] bytes = readEntry(jar, name);
                if (bytes == null) {
                    throw new IOException("missing access transformer: " + name);
                }
                transformers.append(new String(bytes, StandardCharsets.UTF_8)).append('\n');
            }
            if (transformers.length() == 0) {
                byte[] defaultFile = readEntry(jar, "META-INF/accesstransformer.cfg");
                if (defaultFile != null) {
                    transformers.append(new String(defaultFile, StandardCharsets.UTF_8));
                }
            }
            ModJar mod = new ModJar(path, jar, metadata, configs, transformers.toString(),
                    scanClassEntries(jar), readEntry(jar, ENUM_EXTENSIONS));
            done = true;
            return mod;
        } finally {
            if (!done) {
                try {
                    jar.close();
                } catch (IOException ignored) {
                    // the read failure is the one that matters
                }
            }
        }
    }

    private static List<String> scanClassEntries(JarFile jar) {
        Set<String> entries = new LinkedHashSet<>();
        Enumeration<JarEntry> enumeration = jar.entries();
        while (enumeration.hasMoreElements()) {
            JarEntry entry = enumeration.nextElement();
            if (entry.isDirectory()) {
                continue;
            }
            String name = entry.getName();
            if (!name.endsWith(".class") || name.startsWith("META-INF/")) {
                continue;
            }
            if (name.equals("module-info.class") || name.endsWith("/module-info.class")) {
                continue;
            }
            entries.add(name);
        }
        return new ArrayList<>(entries);
    }

    private static byte[] readEntry(JarFile jar, String name) throws IOException {
        JarEntry entry = jar.getJarEntry(name);
        return entry == null ? null : read(jar, entry);
    }

    private static byte[] read(JarFile jar, JarEntry entry) throws IOException {
        try (InputStream in = jar.getInputStream(entry)) {
            return ModJarLimits.read(in, MAX_TEXT_ENTRY_BYTES, entry.getName());
        }
    }

    public Path path() {
        return path;
    }

    public JarFile jarFile() {
        return jarFile;
    }

    public ModMetadata metadata() {
        return metadata;
    }

    public Map<String, byte[]> mixinConfigs() {
        return mixinConfigs;
    }

    public String accessTransformers() {
        return accessTransformers;
    }

    /** The mod's {@code META-INF/enumextensions.json}, or null when it has none. */
    public byte[] enumExtensions() {
        return enumExtensions == null ? null : enumExtensions.clone();
    }

    /** Regular {@code .class} entries visible to the shared class space, in jar order. */
    public List<String> classEntries() {
        return classEntries;
    }

    @Override
    public void close() throws IOException {
        jarFile.close();
    }
}
