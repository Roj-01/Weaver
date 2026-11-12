package net.weaver.core.jij;

import net.weaver.core.MetadataException;
import net.weaver.core.ModMetadata;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.function.Consumer;

/**
 * Reads the nested mod jars of a Weaver mod jar.
 *
 * <p>If {@link JijLayout#MANIFEST} is present it is authoritative and entries
 * are returned in manifest order. If it is absent, every non-directory entry
 * below {@link JijLayout#ROOT} is discovered and its id and version are read
 * from the nested jar's {@code weaver.mod.json}.
 *
 * <p>Every entry is validated before it is returned: names must be relative,
 * may not contain {@code ..}, and must name an existing file in the parent
 * jar. Duplicate ids are preserved in order and reported through the optional
 * diagnostics consumer.
 */
public final class JijScanner {

    private JijScanner() {
    }

    /**
     * Scans a jar on disk. The jar is opened and closed by this method.
     *
     * @param jar the mod jar to scan
     * @return the nested jars in manifest or discovery order
     * @throws IOException if the jar cannot be read or its layout is invalid
     */
    public static List<NestedJar> scan(Path jar) throws IOException {
        return scan(jar, null);
    }

    /**
     * Scans a jar on disk and reports diagnostics.
     *
     * @param jar the mod jar to scan
     * @param diagnostics receives duplicate-id warnings; may be {@code null}
     * @return the nested jars in manifest or discovery order
     * @throws IOException if the jar cannot be read or its layout is invalid
     */
    public static List<NestedJar> scan(Path jar, Consumer<String> diagnostics) throws IOException {
        try (JarFile file = new JarFile(jar.toFile())) {
            return scan(file, diagnostics);
        }
    }

    /**
     * Scans an open jar. The caller keeps ownership of {@code jar}.
     *
     * @param jar the open mod jar
     * @return the nested jars in manifest or discovery order
     * @throws IOException if the layout is invalid
     */
    public static List<NestedJar> scan(JarFile jar) throws IOException {
        return scan(jar, null);
    }

    /**
     * Scans an open jar and reports diagnostics. The caller keeps ownership of
     * {@code jar}.
     *
     * @param jar the open mod jar
     * @param diagnostics receives duplicate-id warnings; may be {@code null}
     * @return the nested jars in manifest or discovery order
     * @throws IOException if the layout is invalid
     */
    public static List<NestedJar> scan(JarFile jar, Consumer<String> diagnostics) throws IOException {
        List<NestedJar> nested = read(jar);
        reportDuplicates(nested, diagnostics);
        return List.copyOf(nested);
    }

    private static List<NestedJar> read(JarFile jar) throws IOException {
        JarEntry manifestEntry = jar.getJarEntry(JijLayout.MANIFEST);
        List<NestedJar> nested;
        if (manifestEntry != null) {
            if (manifestEntry.isDirectory()) {
                throw new IOException(JijLayout.MANIFEST + " is a directory");
            }
            byte[] json = read(jar, manifestEntry);
            try {
                nested = new ArrayList<>(JijManifest.parse(json));
            } catch (IllegalArgumentException e) {
                throw new IOException("invalid " + JijLayout.MANIFEST + ": " + e.getMessage(), e);
            }
            for (NestedJar entry : nested) {
                requireExistingFile(jar, entry.entryName());
            }
        } else {
            nested = discover(jar);
        }
        return nested;
    }

    private static List<NestedJar> discover(JarFile jar) throws IOException {
        List<NestedJar> nested = new ArrayList<>();
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            if (entry.isDirectory() || !JijLayout.isNestedEntry(entry.getName())) {
                continue;
            }
            String name = entry.getName();
            try {
                requireSafeEntry(name);
            } catch (IllegalArgumentException e) {
                throw new IOException(e.getMessage(), e);
            }
            try {
                ModMetadata metadata = nestedMetadata(jar, entry);
                nested.add(new NestedJar(name, metadata.id(), metadata.version(), false));
            } catch (IOException | RuntimeException skipped) {
                // A plain library or a broken jar next to valid ones: skip it, keep the rest.
                System.out.println("[weaver] MOD JIJ SKIP entry=" + name + " reason=" + skipped.getMessage());
            }
        }
        return nested;
    }

    private static ModMetadata nestedMetadata(JarFile parent, JarEntry entry) throws IOException {
        byte[] bytes = read(parent, entry);
        try (JarInputStream nested = new JarInputStream(new ByteArrayInputStream(bytes))) {
            JarEntry nestedEntry;
            while ((nestedEntry = nested.getNextJarEntry()) != null) {
                if (ModMetadata.FILE.equals(nestedEntry.getName())) {
                    byte[] json = net.weaver.core.ModJarLimits.read(nested, 4 * 1024 * 1024, entry.getName());
                    try {
                        return ModMetadata.parse(entry.getName(), json);
                    } catch (MetadataException e) {
                        throw new IOException(entry.getName() + ": invalid " + ModMetadata.FILE
                                + ": " + e.getMessage(), e);
                    }
                }
            }
        }
        throw new IOException(entry.getName() + ": missing " + ModMetadata.FILE);
    }

    private static void requireExistingFile(JarFile jar, String entryName) throws IOException {
        try {
            requireSafeEntry(entryName);
        } catch (IllegalArgumentException e) {
            throw new IOException(e.getMessage(), e);
        }
        JarEntry entry = jar.getJarEntry(entryName);
        if (entry == null || entry.isDirectory()) {
            throw new IOException("nested jar entry is missing: " + entryName);
        }
    }

    /**
     * Validates that a nested entry name is relative and cannot escape the
     * parent jar or the extraction directory.
     *
     * @param entryName the entry name to validate
     * @throws IllegalArgumentException if the name is blank, absolute, contains
     *         a backslash, is empty path segments, or contains {@code .} or
     *         {@code ..} segments
     */
    static void requireSafeEntry(String entryName) {
        if (entryName == null || entryName.isBlank()) {
            throw new IllegalArgumentException("nested jar entry name is blank");
        }
        if (entryName.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("nested jar entry uses a backslash: " + entryName);
        }
        if (entryName.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("nested jar entry contains NUL: " + entryName);
        }
        if (entryName.startsWith("/") || entryName.matches("^[A-Za-z]:.*")) {
            throw new IllegalArgumentException("nested jar entry is absolute: " + entryName);
        }
        String[] segments = entryName.split("/", -1);
        for (String segment : segments) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException("nested jar entry is unsafe: " + entryName);
            }
        }
    }

    private static void reportDuplicates(List<NestedJar> nested, Consumer<String> diagnostics) {
        if (diagnostics == null) {
            return;
        }
        Map<String, String> firstSeen = new LinkedHashMap<>();
        for (NestedJar entry : nested) {
            String first = firstSeen.putIfAbsent(entry.id(), entry.entryName());
            if (first != null) {
                diagnostics.accept("jij: duplicate id '" + entry.id() + "' at "
                        + entry.entryName() + " (first at " + first + ")");
            }
        }
    }

    private static byte[] read(JarFile jar, JarEntry entry) throws IOException {
        try (InputStream in = jar.getInputStream(entry)) {
            return net.weaver.core.ModJarLimits.read(in, 512 * 1024 * 1024, entry.getName());
        }
    }
}
