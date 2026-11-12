package net.weaver.gradle.jij;

import net.weaver.gradle.dependency.ModDescriptor;
import org.gradle.api.GradleException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Stages resolved mod jars as nested jars and writes the jar-in-jar manifest.
 *
 * <p>Jars are sorted by id, then version, then file name, so the output is
 * independent of dependency-resolution order. Each mod id may appear at most
 * once; a duplicate id fails the build instead of silently choosing a winner.
 * The nested file name is {@code <id>-<version>.jar} restricted to
 * {@code [a-z0-9._-]}.
 */
public final class JijPackager {

    private JijPackager() {
    }

    /**
     * Copies {@code jars} below {@code targetDir/META-INF/weaver/jars/} and
     * writes {@code targetDir/META-INF/weaver/jij.json}.
     *
     * @param targetDir the staging directory
     * @param jars the resolved mod jars to embed
     * @param log receives one line per staged jar and a final summary; may be
     *        {@code null}
     * @return the number of staged jars
     * @throws IOException if a jar or the manifest cannot be read or written
     * @throws GradleException if a jar has no readable descriptor, two jars
     *         declare the same id, or two sanitized names collide
     */
    public static int stage(File targetDir, List<File> jars, Consumer<String> log) throws IOException {
        Consumer<String> sink = log == null ? line -> {
        } : log;
        Path root = targetDir.toPath().resolve(JijLayout.ROOT);
        Files.createDirectories(root);

        List<Staged> staged = new ArrayList<>();
        for (File jar : jars) {
            if (jar == null || !jar.isFile()) {
                throw new GradleException("weaverJij: not a readable jar: " + jar);
            }
            ModDescriptor descriptor;
            try {
                descriptor = ModDescriptor.read(jar);
            } catch (IOException | RuntimeException e) {
                throw new GradleException("weaverJij: cannot read weaver.mod.json from "
                        + jar.getAbsolutePath() + ": " + e.getMessage(), e);
            }
            String id = descriptor.id();
            String version = descriptor.version();
            if (id == null || id.isBlank()) {
                throw new GradleException("weaverJij: " + jar.getAbsolutePath()
                        + " has no mod id in weaver.mod.json");
            }
            if (version == null || version.isBlank()) {
                throw new GradleException("weaverJij: " + jar.getAbsolutePath()
                        + " has no version in weaver.mod.json");
            }
            staged.add(new Staged(jar, id, version, JijLayout.entryName(id, version)));
        }

        staged.sort(Comparator.comparing(Staged::id)
                .thenComparing(Staged::version)
                .thenComparing(entry -> entry.file().getName()));

        Map<String, Staged> byId = new LinkedHashMap<>();
        Map<String, Staged> byEntry = new LinkedHashMap<>();
        for (Staged entry : staged) {
            Staged duplicateId = byId.putIfAbsent(entry.id(), entry);
            if (duplicateId != null) {
                throw new GradleException("weaverJij: duplicate mod id '" + entry.id()
                        + "' declared by " + duplicateId.file().getAbsolutePath()
                        + " and " + entry.file().getAbsolutePath()
                        + "; remove one of the mods or pin a single version");
            }
            Staged duplicateEntry = byEntry.putIfAbsent(entry.entryName(), entry);
            if (duplicateEntry != null) {
                throw new GradleException("weaverJij: nested jar name collision for "
                        + entry.entryName() + " between "
                        + duplicateEntry.file().getAbsolutePath() + " and "
                        + entry.file().getAbsolutePath());
            }
        }

        List<JijJar> manifest = new ArrayList<>(staged.size());
        for (Staged entry : staged) {
            Files.copy(entry.file().toPath(), root.resolve(entry.fileName()),
                    StandardCopyOption.REPLACE_EXISTING);
            manifest.add(new JijJar(entry.entryName(), entry.id(), entry.version(), false));
            sink.accept("WEAVER JIJ id=" + entry.id() + " version=" + entry.version()
                    + " entry=" + entry.entryName());
        }
        Files.writeString(targetDir.toPath().resolve(JijLayout.MANIFEST),
                JijManifest.write(manifest), StandardCharsets.UTF_8);
        sink.accept("WEAVER JIJ ok=true nested=" + manifest.size());
        return manifest.size();
    }

    private record Staged(File file, String id, String version, String entryName) {

        private String fileName() {
            int slash = entryName.lastIndexOf('/');
            return slash < 0 ? entryName : entryName.substring(slash + 1);
        }
    }
}
