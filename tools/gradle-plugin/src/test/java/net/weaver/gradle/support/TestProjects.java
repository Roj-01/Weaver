package net.weaver.gradle.support;

import org.gradle.testkit.runner.GradleRunner;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/**
 * Self-contained filesystem fixtures for the Weaver Gradle plugin tests.
 *
 * <p>The helper never touches the repository fixtures: every temp project,
 * local Maven repository, and fake game directory lives under the caller's
 * JUnit {@code @TempDir}.
 */
public final class TestProjects {

    /** The dependency report written by {@code weaverResolveMods}. */
    public static final String DEPENDENCIES_REPORT = "build/weaver/dependencies.json";

    /** The jar-in-jar manifest entry. */
    public static final String JIJ_MANIFEST = "META-INF/weaver/jij.json";

    /** The jar-in-jar nested jar directory entry prefix. */
    public static final String JIJ_ROOT = "META-INF/weaver/jars/";

    private static final byte[] MANIFEST =
            "Manifest-Version: 1.0\r\n\r\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] DUMMY_CLASS =
            {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE};

    private final Path root;
    private final Path repository;

    /**
     * Creates a fixture rooted at {@code root}.
     *
     * @param root the temp project directory
     */
    public TestProjects(Path root) {
        this.root = root.toAbsolutePath();
        this.repository = this.root.resolve("repo");
    }

    /** Returns the fixture root. */
    public Path root() {
        return root;
    }

    /** Returns the local Maven repository directory ({@code root/repo}). */
    public Path repository() {
        return repository;
    }

    /** Resolves a path relative to the fixture root. */
    public Path path(String relative) {
        return root.resolve(relative);
    }

    /** Writes a UTF-8 text file, creating parent directories. */
    public Path write(String relative, String content) throws IOException {
        return writeBytes(relative, content.getBytes(StandardCharsets.UTF_8));
    }

    /** Writes a byte file, creating parent directories. */
    public Path writeBytes(String relative, byte[] content) throws IOException {
        Path file = path(relative);
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(file, content);
        return file;
    }

    /** Writes the minimal Gradle settings file. */
    public Path settings() throws IOException {
        return write("settings.gradle", "rootProject.name = 'test'\n");
    }

    /** Writes {@code settings.gradle} and {@code build.gradle}. */
    public Path buildGradle(String content) throws IOException {
        settings();
        return write("build.gradle", content);
    }

    /**
     * Creates a mod jar with a {@code weaver.mod.json} descriptor and a dummy
     * class entry.
     */
    public Path jar(String relative, String id, String version) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("weaver.mod.json", descriptor(id, version).getBytes(StandardCharsets.UTF_8));
        entries.put("example/" + id.replace('-', '_') + "/Dummy.class", DUMMY_CLASS);
        return jarWithEntries(relative, entries);
    }

    /** Creates a valid, empty jar with only a manifest entry. */
    public Path fakeJar(String relative) throws IOException {
        return jarWithEntries(relative, Map.of("META-INF/MANIFEST.MF", MANIFEST));
    }

    /** Creates a jar from raw entries. */
    public Path jarWithEntries(String relative, Map<String, byte[]> entries) throws IOException {
        Path jar = path(relative);
        Path parent = jar.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (OutputStream raw = Files.newOutputStream(jar);
             JarOutputStream out = new JarOutputStream(raw)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                out.putNextEntry(new JarEntry(entry.getKey()));
                out.write(entry.getValue());
                out.closeEntry();
            }
        }
        return jar;
    }

    /**
     * Publishes a synthetic Maven module under {@code root/repo}.
     *
     * @param group the Maven group id
     * @param artifact the Maven artifact id
     * @param version the module version
     * @param modId the {@code weaver.mod.json} id
     * @param modVersion the {@code weaver.mod.json} version
     * @param dependencies transitive dependencies keyed by {@code group:artifact}
     * @return the repository directory
     */
    public Path mavenModule(String group, String artifact, String version,
                            String modId, String modVersion,
                            Map<String, String> dependencies) throws IOException {
        String base = "repo/" + group.replace('.', '/') + "/" + artifact + "/" + version + "/";
        jar(base + artifact + "-" + version + ".jar", modId, modVersion);
        write(base + artifact + "-" + version + ".pom", pom(group, artifact, version, dependencies));
        return repository;
    }

    /**
     * Writes the artifact-level {@code maven-metadata.xml} used by dynamic
     * (range) version resolution.
     */
    public Path mavenMetadata(String group, String artifact, String... versions) throws IOException {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<metadata>\n");
        xml.append("  <groupId>").append(group).append("</groupId>\n");
        xml.append("  <artifactId>").append(artifact).append("</artifactId>\n");
        xml.append("  <versioning>\n");
        if (versions.length > 0) {
            xml.append("    <latest>").append(versions[versions.length - 1]).append("</latest>\n");
            xml.append("    <release>").append(versions[versions.length - 1]).append("</release>\n");
            xml.append("    <versions>\n");
            for (String version : versions) {
                xml.append("      <version>").append(version).append("</version>\n");
            }
            xml.append("    </versions>\n");
        }
        xml.append("    <lastUpdated>20260101000000</lastUpdated>\n");
        xml.append("  </versioning>\n");
        xml.append("</metadata>\n");
        return write("repo/" + group.replace('.', '/') + "/" + artifact + "/maven-metadata.xml",
                xml.toString());
    }

    /**
     * Creates a fake dedicated-server layout with a version jar and one
     * library jar.
     */
    public Path fakeServerDir(String relative) throws IOException {
        fakeJar(relative + "/versions/26.3/server-26.3.jar");
        fakeJar(relative + "/libraries/com/example/lib-1.0.0.jar");
        return path(relative);
    }

    /** Renders a path for single-quoted Groovy strings. */
    public static String groovyPath(Path path) {
        return path.toAbsolutePath().toString().replace('\\', '/').replace("'", "\\'");
    }

    /** Creates a Gradle TestKit runner with the plugin classpath injected. */
    public static GradleRunner runner(Path projectDir, String... arguments) {
        List<String> all = new ArrayList<>();
        // Gradle 9's Tooling API rejects --no-daemon as a build argument; the
        // TestKit executor already isolates its daemons from the build under test.
        all.add("--console=plain");
        all.addAll(List.of(arguments));
        return GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments(all);
    }

    /** Renders a v2 {@code weaver.mod.json} descriptor. */
    public static String descriptor(String id, String version) {
        return "{"
                + "\"schemaVersion\":2,"
                + "\"id\":\"" + id + "\","
                + "\"version\":\"" + version + "\","
                + "\"name\":\"" + id + " mod\","
                + "\"mainClass\":\"example.Mod\""
                + "}";
    }

    /** Renders a Maven POM with optional transitive dependencies. */
    public static String pom(String group, String artifact, String version,
                             Map<String, String> dependencies) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n");
        xml.append("  <modelVersion>4.0.0</modelVersion>\n");
        xml.append("  <groupId>").append(group).append("</groupId>\n");
        xml.append("  <artifactId>").append(artifact).append("</artifactId>\n");
        xml.append("  <version>").append(version).append("</version>\n");
        xml.append("  <packaging>jar</packaging>\n");
        if (dependencies != null && !dependencies.isEmpty()) {
            xml.append("  <dependencies>\n");
            for (Map.Entry<String, String> dependency : dependencies.entrySet()) {
                String[] coordinates = dependency.getKey().split(":", 2);
                xml.append("    <dependency>\n");
                xml.append("      <groupId>").append(coordinates[0]).append("</groupId>\n");
                xml.append("      <artifactId>").append(coordinates[1]).append("</artifactId>\n");
                xml.append("      <version>").append(dependency.getValue()).append("</version>\n");
                xml.append("    </dependency>\n");
            }
            xml.append("  </dependencies>\n");
        }
        xml.append("</project>\n");
        return xml.toString();
    }
}
