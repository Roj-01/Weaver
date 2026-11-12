package net.weaver.gradle.jij;

import org.gradle.api.GradleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests for jar-in-jar staging, manifest persistence, and extraction. */
class JijPackagerTest {

    @TempDir
    Path temp;

    @Test
    void stageSortsCopiesAndWritesManifest() throws IOException {
        File zeta = modJar("zeta.jar", "zeta", "2.0.0");
        File alpha = modJar("alpha.jar", "alpha", "1.5.0");
        List<String> log = new ArrayList<>();

        int count = JijPackager.stage(temp.resolve("out").toFile(), List.of(zeta, alpha), log::add);

        assertEquals(2, count);
        Path root = temp.resolve("out").resolve(JijLayout.ROOT);
        assertTrue(Files.isRegularFile(root.resolve("alpha-1.5.0.jar")));
        assertTrue(Files.isRegularFile(root.resolve("zeta-2.0.0.jar")));
        assertTrue(Files.isRegularFile(temp.resolve("out").resolve(JijLayout.MANIFEST)));
        assertEquals(List.of(
                new JijJar(JijLayout.ROOT + "alpha-1.5.0.jar", "alpha", "1.5.0", false),
                new JijJar(JijLayout.ROOT + "zeta-2.0.0.jar", "zeta", "2.0.0", false)),
                JijManifest.parse(Files.readAllBytes(temp.resolve("out").resolve(JijLayout.MANIFEST))));
        assertEquals(List.of(
                "WEAVER JIJ id=alpha version=1.5.0 entry=META-INF/weaver/jars/alpha-1.5.0.jar",
                "WEAVER JIJ id=zeta version=2.0.0 entry=META-INF/weaver/jars/zeta-2.0.0.jar",
                "WEAVER JIJ ok=true nested=2"), log);
        assertArrayEquals(Files.readAllBytes(alpha.toPath()), Files.readAllBytes(root.resolve("alpha-1.5.0.jar")));
    }

    @Test
    void stageSanitizesFileNames() throws IOException {
        File jar = modJar("raw.jar", "example", "1.0.0-RC1+build7");

        JijPackager.stage(temp.resolve("out").toFile(), List.of(jar), null);

        assertTrue(Files.isRegularFile(temp.resolve("out").resolve(JijLayout.ROOT)
                .resolve("example-1.0.0-rc1-build7.jar")));
    }

    @Test
    void stageFailsOnDuplicateIds() throws IOException {
        File first = modJar("first.jar", "duplicate", "1.0.0");
        File second = modJar("second.jar", "duplicate", "2.0.0");

        GradleException error = assertThrows(GradleException.class, () -> JijPackager.stage(
                temp.resolve("out").toFile(), List.of(first, second), null));

        assertTrue(error.getMessage().contains("duplicate mod id 'duplicate'"));
        assertTrue(error.getMessage().contains("first.jar"));
        assertTrue(error.getMessage().contains("second.jar"));
    }

    @Test
    void stageFailsOnMissingDescriptor() throws IOException {
        Path jar = temp.resolve("plain.jar");
        try (OutputStream raw = Files.newOutputStream(jar);
             JarOutputStream out = new JarOutputStream(raw)) {
            put(out, "example/Plain.class", new byte[]{0});
        }

        GradleException error = assertThrows(GradleException.class, () -> JijPackager.stage(
                temp.resolve("out").toFile(), List.of(jar.toFile()), null));

        assertTrue(error.getMessage().contains("weaver.mod.json"));
    }

    @Test
    void stageIgnoresResolutionOrder() throws IOException {
        File alpha = modJar("a.jar", "alpha", "1.0.0");
        File beta = modJar("b.jar", "beta", "1.0.0");
        File alphaNew = modJarFolder();

        JijPackager.stage(temp.resolve("one").toFile(), List.of(beta, alpha), null);
        JijPackager.stage(temp.resolve("two").toFile(), List.of(alphaNew, beta), null);

        assertEquals(Files.readString(temp.resolve("one").resolve(JijLayout.MANIFEST)),
                Files.readString(temp.resolve("two").resolve(JijLayout.MANIFEST)));
    }

    @Test
    void manifestRoundTrips() {
        List<JijJar> jars = List.of(
                new JijJar(JijLayout.ROOT + "b-2.0.0.jar", "b", "2.0.0", true),
                new JijJar(JijLayout.ROOT + "a-1.0.0.jar", "a", "1.0.0", false));

        String json = JijManifest.write(jars);

        assertTrue(json.contains("\"schemaVersion\": 1"));
        assertTrue(json.contains("\"firstWins\": true"));
        assertEquals(jars, JijManifest.parse(json.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void manifestRejectsTraversal() {
        String json = """
                {"schemaVersion":1,"firstWins":true,"jars":[
                  {"file":"META-INF/weaver/jars/../evil.jar","id":"evil","version":"1.0.0"}
                ]}
                """;

        assertThrows(IllegalArgumentException.class,
                () -> JijManifest.parse(json.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void manifestRejectsAbsoluteEntry() {
        String json = """
                {"schemaVersion":1,"firstWins":true,"jars":[
                  {"file":"C:/tmp/evil.jar","id":"evil","version":"1.0.0"}
                ]}
                """;

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> JijManifest.parse(json.getBytes(StandardCharsets.UTF_8)));
        assertTrue(error.getMessage().contains("absolute"));
    }

    @Test
    void extractWritesIndexedNamesAndContent() throws IOException {
        byte[] alpha = modJarBytes("alpha", "1.0.0");
        byte[] beta = modJarBytes("beta", "2.0.0");
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(JijLayout.ROOT + "beta.jar", beta);
        entries.put(JijLayout.ROOT + "alpha.jar", alpha);
        entries.put(JijLayout.MANIFEST, manifest(
                nested(JijLayout.ROOT + "beta.jar", "beta", "2.0.0", false),
                nested(JijLayout.ROOT + "alpha.jar", "alpha", "1.0.0", true)));
        Path parent = jar("parent.jar", entries);
        List<String> log = new ArrayList<>();

        int count = JijExtractor.extract(parent.toFile(), temp.resolve("export"), log::add);

        assertEquals(2, count);
        Path betaOut = temp.resolve("export").resolve("jij-0001-beta-2.0.0.jar");
        Path alphaOut = temp.resolve("export").resolve("jij-0002-alpha-1.0.0.jar");
        assertArrayEquals(beta, Files.readAllBytes(betaOut));
        assertArrayEquals(alpha, Files.readAllBytes(alphaOut));
        assertEquals(List.of(
                "WEAVER JIJ EXPORT id=beta version=2.0.0 file=jij-0001-beta-2.0.0.jar",
                "WEAVER JIJ EXPORT id=alpha version=1.0.0 file=jij-0002-alpha-1.0.0.jar",
                "WEAVER JIJ EXPORT ok=true jars=2"), log);
    }

    @Test
    void extractIsNoOpWithoutManifest() throws IOException {
        Path parent = jar("plain.jar", Map.of("weaver.mod.json",
                descriptor("plain", "1.0.0")));
        Path out = temp.resolve("export");
        List<String> log = new ArrayList<>();

        int count = JijExtractor.extract(parent.toFile(), out, log::add);

        assertEquals(0, count);
        assertTrue(Files.isDirectory(out));
        assertEquals(List.of("WEAVER JIJ EXPORT ok=true jars=0"), log);
        assertFalse(Files.exists(out.resolve("jij-0001-plain-1.0.0.jar")));
    }

    @Test
    void extractRejectsMissingDeclaredEntry() throws IOException {
        Path parent = jar("missing.jar", Map.of(JijLayout.MANIFEST, manifest(
                nested(JijLayout.ROOT + "missing.jar", "missing", "1.0.0", false))));

        IOException error = assertThrows(IOException.class,
                () -> JijExtractor.extract(parent.toFile(), temp.resolve("export"), null));

        assertTrue(error.getMessage().contains("missing.jar"));
    }

    private File modJarFolder() throws IOException {
        return modJar("alpha-copy.jar", "alpha", "1.0.0");
    }

    private File modJar(String name, String id, String version) throws IOException {
        return jar(name, Map.of("weaver.mod.json", descriptor(id, version))).toFile();
    }

    private static byte[] modJarBytes(String id, String version) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (JarOutputStream out = new JarOutputStream(bytes)) {
            put(out, "weaver.mod.json", descriptor(id, version));
        }
        return bytes.toByteArray();
    }

    private Path jar(String name, Map<String, byte[]> entries) throws IOException {
        Path path = temp.resolve(name);
        try (OutputStream raw = Files.newOutputStream(path);
             JarOutputStream out = new JarOutputStream(raw)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                put(out, entry.getKey(), entry.getValue());
            }
        }
        return path;
    }

    private static byte[] descriptor(String id, String version) {
        return ("{\"schemaVersion\":2,\"id\":\"" + id + "\",\"version\":\"" + version
                + "\",\"mainClass\":\"example.Mod\"}").getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] manifest(String... jars) {
        return ("{\"schemaVersion\":1,\"firstWins\":true,\"jars\":["
                + String.join(",", jars) + "]}").getBytes(StandardCharsets.UTF_8);
    }

    private static String nested(String file, String id, String version, boolean optional) {
        return "{\"file\":\"" + file + "\",\"id\":\"" + id + "\",\"version\":\""
                + version + "\",\"optional\":" + optional + "}";
    }

    private static void put(JarOutputStream out, String name, byte[] bytes) throws IOException {
        out.putNextEntry(new JarEntry(name));
        out.write(bytes);
        out.closeEntry();
    }
}
