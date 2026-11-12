package net.weaver.core.jij;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests for the jar-in-jar scanner, manifest parser, extractor, and CLI. */
class JijTest {

    @TempDir
    Path temp;

    @Test
    void manifestParsesEntriesInOrder() {
        String json = """
                {
                  "schemaVersion": 1,
                  "firstWins": true,
                  "jars": [
                    {"file": "META-INF/weaver/jars/zeta-2.0.0.jar",
                     "id": "zeta", "version": "2.0.0", "optional": true},
                    {"file": "META-INF/weaver/jars/alpha-1.0.0.jar",
                     "id": "alpha", "version": "1.0.0"}
                  ]
                }
                """;
        List<NestedJar> jars = JijManifest.parse(json.getBytes(StandardCharsets.UTF_8));
        assertEquals(2, jars.size());
        assertEquals(new NestedJar("META-INF/weaver/jars/zeta-2.0.0.jar", "zeta", "2.0.0", true),
                jars.get(0));
        assertEquals(new NestedJar("META-INF/weaver/jars/alpha-1.0.0.jar", "alpha", "1.0.0", false),
                jars.get(1));
    }

    @Test
    void manifestRejectsUnsupportedSchema() {
        String json = """
                {"schemaVersion": 2, "firstWins": true, "jars": []}
                """;
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> JijManifest.parse(json.getBytes(StandardCharsets.UTF_8)));
        assertTrue(error.getMessage().contains("schemaVersion"));
    }

    @Test
    void scanReadsDeclaredEntriesInManifestOrder() throws IOException {
        byte[] zeta = modJar("zeta", "2.0.0");
        byte[] alpha = modJar("alpha", "1.0.0");
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(JijLayout.ROOT + "zeta-2.0.0.jar", zeta);
        entries.put(JijLayout.ROOT + "alpha-1.0.0.jar", alpha);
        entries.put(JijLayout.MANIFEST, manifest(
                nested(JijLayout.ROOT + "zeta-2.0.0.jar", "zeta", "2.0.0", true),
                nested(JijLayout.ROOT + "alpha-1.0.0.jar", "alpha", "1.0.0", false)));
        Path jar = jar("declared.jar", entries);

        List<NestedJar> scanned = JijScanner.scan(jar);

        assertEquals(List.of(
                new NestedJar(JijLayout.ROOT + "zeta-2.0.0.jar", "zeta", "2.0.0", true),
                new NestedJar(JijLayout.ROOT + "alpha-1.0.0.jar", "alpha", "1.0.0", false)),
                scanned);
    }

    @Test
    void scanDiscoversEntriesWithoutManifest() throws IOException {
        byte[] zeta = modJar("zeta", "2.0.0");
        byte[] alpha = modJar("alpha", "1.0.0");
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(JijLayout.ROOT + "z-first.jar", zeta);
        entries.put(JijLayout.ROOT + "a-second.jar", alpha);
        Path jar = jar("discovered.jar", entries);

        List<NestedJar> scanned = JijScanner.scan(jar);

        assertEquals(List.of(
                new NestedJar(JijLayout.ROOT + "z-first.jar", "zeta", "2.0.0", false),
                new NestedJar(JijLayout.ROOT + "a-second.jar", "alpha", "1.0.0", false)),
                scanned);
    }

    @Test
    void scanDiscoversNothingWhenNoNestedEntriesExist() throws IOException {
        Path jar = jar("empty.jar", Map.of("weaver.mod.json", descriptor("parent", "1.0.0")));
        assertTrue(JijScanner.scan(jar).isEmpty());
    }

    @Test
    void scanReportsDuplicateIdsWithoutDeduping() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(JijLayout.ROOT + "first.jar", modJar("duplicate", "1.0.0"));
        entries.put(JijLayout.ROOT + "second.jar", modJar("duplicate", "2.0.0"));
        entries.put(JijLayout.MANIFEST, manifest(
                nested(JijLayout.ROOT + "first.jar", "duplicate", "1.0.0", false),
                nested(JijLayout.ROOT + "second.jar", "duplicate", "2.0.0", false)));
        Path jar = jar("duplicate.jar", entries);
        List<String> diagnostics = new ArrayList<>();

        List<NestedJar> scanned = JijScanner.scan(jar, diagnostics::add);

        assertEquals(2, scanned.size());
        assertEquals("first.jar", Path.of(scanned.get(0).entryName()).getFileName().toString());
        assertEquals("second.jar", Path.of(scanned.get(1).entryName()).getFileName().toString());
        assertEquals(1, diagnostics.size());
        assertTrue(diagnostics.get(0).contains("duplicate id 'duplicate'"));
        assertTrue(diagnostics.get(0).contains("first.jar"));
        assertTrue(diagnostics.get(0).contains("second.jar"));
    }

    @Test
    void scanRejectsPathTraversal() throws IOException {
        Path jar = jar("traversal.jar", Map.of(JijLayout.MANIFEST, manifest(
                nested(JijLayout.ROOT + "../evil.jar", "evil", "1.0.0", false))));

        IOException error = assertThrows(IOException.class, () -> JijScanner.scan(jar));

        assertTrue(error.getMessage().contains("invalid " + JijLayout.MANIFEST));
    }

    @Test
    void scanRejectsAbsolutePaths() throws IOException {
        Path jar = jar("absolute.jar", Map.of(JijLayout.MANIFEST, manifest(
                nested("C:/tmp/evil.jar", "evil", "1.0.0", false))));

        IOException error = assertThrows(IOException.class, () -> JijScanner.scan(jar));

        assertTrue(error.getMessage().contains("absolute"));
    }

    @Test
    void scanRejectsMissingDeclaredEntry() throws IOException {
        Path jar = jar("missing.jar", Map.of(JijLayout.MANIFEST, manifest(
                nested(JijLayout.ROOT + "missing.jar", "missing", "1.0.0", false))));

        IOException error = assertThrows(IOException.class, () -> JijScanner.scan(jar));

        assertTrue(error.getMessage().contains("missing.jar"));
    }

    @Test
    void scanSkipsDiscoveredJarWithoutDescriptorAndKeepsTheOthers() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(JijLayout.ROOT + "plain.jar", emptyJar());
        entries.put(JijLayout.ROOT + "alpha.jar", modJar("alpha", "1.0.0"));
        Path jar = jar("plain.jar", entries);

        List<NestedJar> nested = JijScanner.scan(jar);

        // A plain library next to a valid nested mod costs only itself.
        assertEquals(1, nested.size());
        assertEquals("alpha", nested.get(0).id());
    }

    @Test
    void extractWritesDeterministicNamesAndContent() throws IOException {
        byte[] alpha = modJar("alpha", "1.0.0");
        byte[] beta = modJar("beta", "2.0.0-rc1");
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(JijLayout.ROOT + "libs.jar", alpha);
        entries.put(JijLayout.ROOT + "more.jar", beta);
        entries.put(JijLayout.MANIFEST, manifest(
                nested(JijLayout.ROOT + "libs.jar", "alpha", "1.0.0", false),
                nested(JijLayout.ROOT + "more.jar", "beta", "2.0.0-rc1", true)));
        Path jar = jar("extract.jar", entries);
        Path out = temp.resolve("out");
        List<String> log = new ArrayList<>();

        List<Path> extracted = JijExtractor.extract(jar, out, log::add);

        assertEquals(List.of(out.resolve("jij-0001-alpha-1.0.0.jar"),
                out.resolve("jij-0002-beta-2.0.0-rc1.jar")), extracted);
        assertArrayEquals(alpha, Files.readAllBytes(extracted.get(0)));
        assertArrayEquals(beta, Files.readAllBytes(extracted.get(1)));
        assertTrue(log.contains("WEAVER JIJ EXPORT id=alpha version=1.0.0 file=jij-0001-alpha-1.0.0.jar"));
        assertTrue(log.contains("WEAVER JIJ EXPORT id=beta version=2.0.0-rc1 file=jij-0002-beta-2.0.0-rc1.jar"));
        assertEquals("WEAVER JIJ EXPORT ok=true jars=2", log.get(log.size() - 1));
    }

    @Test
    void extractReplacesExistingFiles() throws IOException {
        byte[] alpha = modJar("alpha", "1.0.0");
        Path jar = jar("replace.jar", Map.of(
                JijLayout.ROOT + "a.jar", alpha,
                JijLayout.MANIFEST, manifest(
                        nested(JijLayout.ROOT + "a.jar", "alpha", "1.0.0", false))));
        Path out = temp.resolve("out");
        Files.createDirectories(out);
        Path stale = out.resolve("jij-0001-alpha-1.0.0.jar");
        Files.writeString(stale, "stale");

        JijExtractor.extract(jar, out, null);

        assertArrayEquals(alpha, Files.readAllBytes(stale));
    }

    @Test
    void cliExtractsAndSummarizes() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(JijLayout.ROOT + "a.jar", modJar("alpha", "1.0.0"));
        entries.put(JijLayout.MANIFEST, manifest(
                nested(JijLayout.ROOT + "a.jar", "alpha", "1.0.0", false)));
        Path jar = jar("cli.jar", entries);
        Path out = temp.resolve("cli-out");
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int status = JijCli.run(new String[]{"--jar", jar.toString(), "--out", out.toString()},
                new java.io.PrintStream(stdout, true, StandardCharsets.UTF_8),
                new java.io.PrintStream(stderr, true, StandardCharsets.UTF_8));

        assertEquals(0, status, stderr.toString(StandardCharsets.UTF_8));
        String output = stdout.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("WEAVER JIJ jar="));
        assertTrue(output.contains("WEAVER JIJ ENTRY id=alpha version=1.0.0 optional=false"));
        assertTrue(output.contains("WEAVER JIJ EXPORT ok=true jars=1"));
        assertTrue(output.contains("WEAVER JIJ ok=true nested=1 extracted=1"));
        assertTrue(Files.isRegularFile(out.resolve("jij-0001-alpha-1.0.0.jar")));
        assertEquals("", stderr.toString(StandardCharsets.UTF_8));
    }

    @Test
    void cliReturnsNonZeroForMissingArguments() {
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int status = JijCli.run(new String[]{"--jar", "mod.jar"},
                new java.io.PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
                new java.io.PrintStream(stderr, true, StandardCharsets.UTF_8));
        assertTrue(status != 0);
        assertTrue(stderr.toString(StandardCharsets.UTF_8).contains("--out"));
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

    private static byte[] modJar(String id, String version) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (JarOutputStream out = new JarOutputStream(bytes)) {
            put(out, "weaver.mod.json", descriptor(id, version));
        }
        return bytes.toByteArray();
    }

    private static byte[] emptyJar() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (JarOutputStream ignored = new JarOutputStream(bytes)) {
            // empty archive
        }
        return bytes.toByteArray();
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
