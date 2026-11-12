package net.weaver.core;

import net.weaver.core.jij.JijLayout;
import net.weaver.core.modops.ModPolicies;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end jar-in-jar discovery through {@link ModLoader#discover}, the same
 * call {@code LoaderAgent} makes at boot with {@code <dataDir>/jij}.
 */
class ModLoaderNestedJarTest {

    @TempDir
    Path temp;

    @Test
    void discoverExtractsAndLoadsNestedMods() throws IOException {
        Path mods = Files.createDirectories(temp.resolve("mods"));
        Path jijDir = temp.resolve("data").resolve("jij");
        writeJar(mods.resolve("parent.jar"),
                entry("weaver.mod.json", descriptor("parent", "1.0.0")),
                entry(JijLayout.ROOT + "child-2.0.0.jar", modJar("child", "2.0.0")));
        List<String> log = new ArrayList<>();

        List<String> ids;
        try (ModLoader loader = ModLoader.discover(mods, "client", log::add,
                ModPolicies.warn(), jijDir)) {
            ids = loader.mods().stream().map(mod -> mod.metadata().id()).sorted().toList();
        }

        assertEquals(List.of("child", "parent"), ids);
        assertTrue(log.stream().anyMatch(line -> line.startsWith("[weaver] MOD JIJ parent=parent.jar")
                && line.contains("nested=1") && line.contains("extracted=1")), String.join("\n", log));
        try (var extracted = Files.walk(jijDir)) {
            assertEquals(1L, extracted.filter(path -> path.toString().endsWith(".jar")).count());
        }
    }

    @Test
    void discoverKeepsNestedJarsExternalWithoutJijDirectory() throws IOException {
        Path mods = Files.createDirectories(temp.resolve("mods"));
        writeJar(mods.resolve("parent.jar"),
                entry("weaver.mod.json", descriptor("parent", "1.0.0")),
                entry(JijLayout.ROOT + "child-2.0.0.jar", modJar("child", "2.0.0")));

        List<String> ids;
        try (ModLoader loader = ModLoader.discover(mods, "client", line -> { },
                ModPolicies.warn(), null)) {
            ids = loader.mods().stream().map(mod -> mod.metadata().id()).toList();
        }

        assertEquals(List.of("parent"), ids);
    }

    @Test
    void brokenNestedJarDoesNotBlockTheParent() throws IOException {
        Path mods = Files.createDirectories(temp.resolve("mods"));
        writeJar(mods.resolve("parent.jar"),
                entry("weaver.mod.json", descriptor("parent", "1.0.0")),
                entry(JijLayout.MANIFEST, ("{\"schemaVersion\":1,\"firstWins\":true,\"jars\":["
                        + "{\"file\":\"" + JijLayout.ROOT + "missing.jar\",\"id\":\"missing\","
                        + "\"version\":\"1.0.0\",\"optional\":false}]}")
                        .getBytes(StandardCharsets.UTF_8)));
        List<String> log = new ArrayList<>();

        List<String> ids;
        try (ModLoader loader = ModLoader.discover(mods, "client", log::add,
                ModPolicies.warn(), temp.resolve("jij"))) {
            ids = loader.mods().stream().map(mod -> mod.metadata().id()).toList();
        }

        assertEquals(List.of("parent"), ids);
        assertTrue(log.stream().anyMatch(line -> line.startsWith("[weaver] MOD JIJ FAILED")),
                String.join("\n", log));
    }

    private record Entry(String name, byte[] bytes) {
    }

    private static Entry entry(String name, byte[] bytes) {
        return new Entry(name, bytes);
    }

    private static byte[] descriptor(String id, String version) {
        return ("{\"schemaVersion\":2,\"id\":\"" + id + "\",\"version\":\"" + version
                + "\",\"mainClass\":\"example." + id + ".Mod\"}").getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] modJar(String id, String version) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        writeEntries(bytes, entry("weaver.mod.json", descriptor(id, version)));
        return bytes.toByteArray();
    }

    private static void writeJar(Path path, Entry... entries) throws IOException {
        try (OutputStream out = Files.newOutputStream(path)) {
            writeEntries(out, entries);
        }
    }

    private static void writeEntries(OutputStream target, Entry... entries) throws IOException {
        try (JarOutputStream out = new JarOutputStream(target)) {
            for (Entry entry : entries) {
                out.putNextEntry(new JarEntry(entry.name()));
                out.write(entry.bytes());
                out.closeEntry();
            }
        }
    }
}
