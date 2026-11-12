package net.weaver.core.jij;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Extracts nested mod jars from a Weaver mod jar.
 *
 * <p>Extraction is deterministic. The {@code index} entry becomes
 * {@code jij-0001-<id>-<version>.jar}, the second becomes
 * {@code jij-0002-...}, and so on, where {@code index} starts at one and is
 * zero-padded to four digits. There is no classloader isolation and no
 * bytecode rewriting: this is a debugging and run-directory helper.
 */
public final class JijExtractor {

    private JijExtractor() {
    }

    /**
     * Extracts every nested jar into {@code targetDir}.
     *
     * @param jar the parent mod jar
     * @param targetDir the directory that receives the extracted jars
     * @param log receives one line per extracted jar and a final summary; may
     *        be {@code null}
     * @return the extracted files in manifest or discovery order
     * @throws IOException if the parent jar or a nested entry cannot be read
     */
    public static List<Path> extract(Path jar, Path targetDir, Consumer<String> log) throws IOException {
        Consumer<String> sink = log == null ? line -> {
        } : log;
        Files.createDirectories(targetDir);
        List<Path> extracted = new ArrayList<>();
        try (JarFile file = new JarFile(jar.toFile())) {
            List<NestedJar> nested = JijScanner.scan(file, sink);
            int index = 0;
            for (NestedJar entry : nested) {
                index++;
                String name = "jij-" + String.format(Locale.ROOT, "%04d", index)
                        + "-" + sanitize(entry.id()) + "-" + sanitize(entry.version()) + ".jar";
                JarEntry nestedEntry = file.getJarEntry(entry.entryName());
                if (nestedEntry == null || nestedEntry.isDirectory()) {
                    throw new IOException("nested jar entry is missing: " + entry.entryName());
                }
                Path target = targetDir.resolve(name);
                try (InputStream in = file.getInputStream(nestedEntry)) {
                    target = place(in, targetDir, target);
                }
                extracted.add(target);
                sink.accept("WEAVER JIJ EXPORT id=" + entry.id() + " version=" + entry.version()
                        + " file=" + name);
            }
        }
        sink.accept("WEAVER JIJ EXPORT ok=true jars=" + extracted.size());
        return List.copyOf(extracted);
    }

    /**
     * Writes a nested jar to {@code target} without ever leaving a half-written
     * file there. Another game process that uses the same data folder may have
     * the same file open: an identical file is reused, and a locked different one
     * makes this process use its own name instead of failing.
     */
    private static Path place(InputStream in, Path directory, Path target) throws IOException {
        Path temporary = Files.createTempFile(directory, "jij-", ".tmp");
        try {
            Files.copy(in, temporary, StandardCopyOption.REPLACE_EXISTING);
            if (Files.isRegularFile(target) && Files.mismatch(temporary, target) == -1L) {
                return target;
            }
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                return target;
            } catch (java.nio.file.FileSystemException locked) {
                String file = target.getFileName().toString();
                Path own = target.resolveSibling(file.substring(0, file.length() - 4)
                        + "-p" + ProcessHandle.current().pid() + ".jar");
                Files.move(temporary, own, StandardCopyOption.REPLACE_EXISTING);
                return own;
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String sanitize(String value) {
        String safe = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "-");
        safe = safe.replaceAll("^-+|-+$", "");
        return safe.isEmpty() ? "unknown" : safe;
    }
}
