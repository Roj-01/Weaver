package net.weaver.gradle.jij;

import java.io.File;
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
 * Extracts the nested mod jars of a built mod jar for local runs.
 *
 * <p>The extraction is deterministic: entry one becomes
 * {@code jij-0001-<id>-<version>.jar}, entry two becomes
 * {@code jij-0002-...}, and so on. The extractor never deletes files it did
 * not write.
 */
public final class JijExtractor {

    private JijExtractor() {
    }

    /**
     * Extracts every nested jar declared by {@link JijLayout#MANIFEST} into
     * {@code targetDir}.
     *
     * @param jar the parent mod jar
     * @param targetDir the output directory, created when missing
     * @param log receives one line per extracted jar and a final summary; may
     *        be {@code null}
     * @return the number of extracted jars
     * @throws IOException if the jar or a nested entry cannot be read
     */
    public static int extract(File jar, Path targetDir, Consumer<String> log) throws IOException {
        Consumer<String> sink = log == null ? line -> {
        } : log;
        Files.createDirectories(targetDir);
        int count = 0;
        try (JarFile file = new JarFile(jar)) {
            JarEntry manifestEntry = file.getJarEntry(JijLayout.MANIFEST);
            if (manifestEntry == null) {
                sink.accept("WEAVER JIJ EXPORT ok=true jars=0");
                return 0;
            }
            if (manifestEntry.isDirectory()) {
                throw new IOException(JijLayout.MANIFEST + " is a directory");
            }
            List<JijJar> nested;
            try (InputStream in = file.getInputStream(manifestEntry)) {
                nested = JijManifest.parse(in.readAllBytes());
            } catch (IllegalArgumentException e) {
                throw new IOException("invalid " + JijLayout.MANIFEST + ": " + e.getMessage(), e);
            }
            int index = 0;
            List<Path> written = new ArrayList<>();
            for (JijJar entry : nested) {
                index++;
                JijLayout.validateEntryName(entry.entryName());
                JarEntry source = file.getJarEntry(entry.entryName());
                if (source == null || source.isDirectory()) {
                    throw new IOException("nested jar entry is missing: " + entry.entryName());
                }
                String name = "jij-" + String.format(Locale.ROOT, "%04d", index)
                        + "-" + JijLayout.sanitize(entry.id())
                        + "-" + JijLayout.sanitize(entry.version()) + ".jar";
                Path target = targetDir.resolve(name);
                try (InputStream in = file.getInputStream(source)) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
                written.add(target);
                sink.accept("WEAVER JIJ EXPORT id=" + entry.id() + " version=" + entry.version()
                        + " file=" + name);
            }
            count = written.size();
        }
        sink.accept("WEAVER JIJ EXPORT ok=true jars=" + count);
        return count;
    }
}
