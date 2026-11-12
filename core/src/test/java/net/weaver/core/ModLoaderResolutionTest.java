package net.weaver.core;

import net.weaver.core.jij.JijLayout;
import net.weaver.core.modops.ModDiagnostic;
import net.weaver.core.modops.ModListEntry;
import net.weaver.core.modops.ModPolicies;
import net.weaver.core.modops.ModStatus;
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
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Dependency propagation, duplicate-id resolution and jar-in-jar rules of {@link ModLoader}. */
class ModLoaderResolutionTest {

    @TempDir
    Path temp;

    @Test
    void aModThatRequiresARejectedModIsRejected() throws IOException {
        Path mods = Files.createDirectories(temp.resolve("mods"));
        // b requires a missing mod, a requires b, c requires a: all three must go.
        writeJar(mods.resolve("a.jar"), entry("weaver.mod.json",
                descriptor("a", "1.0.0", "\"dependencies\":{\"b\":\"*\"}")));
        writeJar(mods.resolve("b.jar"), entry("weaver.mod.json",
                descriptor("b", "1.0.0", "\"dependencies\":{\"absent\":\"*\"}")));
        writeJar(mods.resolve("c.jar"), entry("weaver.mod.json",
                descriptor("c", "1.0.0", "\"dependencies\":{\"a\":\">=1.0\"}")));
        writeJar(mods.resolve("d.jar"), entry("weaver.mod.json", descriptor("d", "1.0.0", null)));
        List<String> log = new ArrayList<>();

        try (ModLoader loader = ModLoader.discover(mods, "client", log::add, ModPolicies.warn())) {
            assertEquals(List.of("d"), ids(loader));
            Map<String, ModListEntry> entries = byId(loader);
            assertEquals(ModStatus.REJECTED_DEPENDENCY, entries.get("a").status());
            assertEquals(ModStatus.REJECTED_DEPENDENCY, entries.get("b").status());
            assertEquals(ModStatus.REJECTED_DEPENDENCY, entries.get("c").status());
            assertTrue(loader.report().diagnostics().stream().anyMatch(diagnostic ->
                    diagnostic.type() == ModDiagnostic.Type.MISSING_DEPENDENCY
                            && diagnostic.subject().equals("a")
                            && diagnostic.message().contains("dependency=b")),
                    loader.report().diagnostics().toString());
        }
        assertTrue(log.contains("[weaver] MOD DEPENDENCY MISSING id=a dependency=b range=*"
                + " reason=dependency-rejected"), String.join("\n", log));
    }

    @Test
    void anUnparseableDependencyRangeRejectsTheMod() throws IOException {
        Path mods = Files.createDirectories(temp.resolve("mods"));
        writeJar(mods.resolve("lib.jar"), entry("weaver.mod.json", descriptor("lib", "1.0.0", null)));
        writeJar(mods.resolve("user.jar"), entry("weaver.mod.json",
                descriptor("user", "1.0.0", "\"dependencies\":{\"lib\":\"=>1.0\"}")));
        List<String> log = new ArrayList<>();

        try (ModLoader loader = ModLoader.discover(mods, "client", log::add, ModPolicies.warn())) {
            assertEquals(List.of("lib"), ids(loader));
            assertEquals(ModStatus.REJECTED_VERSION, byId(loader).get("user").status());
        }
        assertTrue(log.stream().anyMatch(line -> line.startsWith(
                "[weaver] MOD DEPENDENCY RANGE INVALID id=user dependency=lib range==>1.0")),
                String.join("\n", log));
    }

    @Test
    void theHighestVersionWinsADuplicateIdEvenWhenNested() throws IOException {
        Path mods = Files.createDirectories(temp.resolve("mods"));
        // The nested copy is extracted as jij-0001-..., which sorts before "lib.jar".
        writeJar(mods.resolve("parent.jar"),
                entry("weaver.mod.json", descriptor("parent", "1.0.0", null)),
                entry(JijLayout.ROOT + "lib-1.0.0.jar", modJar("lib", "1.0.0")));
        writeJar(mods.resolve("lib.jar"), entry("weaver.mod.json", descriptor("lib", "2.0.0", null)));
        List<String> log = new ArrayList<>();

        try (ModLoader loader = ModLoader.discover(mods, "client", log::add, ModPolicies.warn(),
                temp.resolve("jij"))) {
            ModJar lib = loader.mods().stream()
                    .filter(mod -> mod.metadata().id().equals("lib"))
                    .findFirst()
                    .orElseThrow();
            assertEquals("2.0.0", lib.metadata().version());
            assertEquals("lib.jar", lib.path().getFileName().toString());
            assertTrue(loader.report().diagnostics().stream().anyMatch(diagnostic ->
                    diagnostic.type() == ModDiagnostic.Type.DUPLICATE_ID
                            && diagnostic.subject().equals("lib")
                            && diagnostic.message().contains("kept=lib.jar")),
                    loader.report().diagnostics().toString());
        }
    }

    @Test
    void aTopLevelJarWinsATieWithANestedJar() throws IOException {
        Path mods = Files.createDirectories(temp.resolve("mods"));
        writeJar(mods.resolve("parent.jar"),
                entry("weaver.mod.json", descriptor("parent", "1.0.0", null)),
                entry(JijLayout.ROOT + "lib-1.0.0.jar", modJar("lib", "1.0.0")));
        writeJar(mods.resolve("lib.jar"), entry("weaver.mod.json", descriptor("lib", "1.0.0", null)));

        try (ModLoader loader = ModLoader.discover(mods, "client", line -> { }, ModPolicies.warn(),
                temp.resolve("jij"))) {
            ModJar lib = loader.mods().stream()
                    .filter(mod -> mod.metadata().id().equals("lib"))
                    .findFirst()
                    .orElseThrow();
            assertEquals("lib.jar", lib.path().getFileName().toString());
        }
    }

    @Test
    void nestedJarsOfAWrongSideParentAreNotLoaded() throws IOException {
        Path mods = Files.createDirectories(temp.resolve("mods"));
        writeJar(mods.resolve("serveronly.jar"),
                entry("weaver.mod.json", descriptor("serveronly", "1.0.0", "\"side\":\"server\"")),
                entry(JijLayout.ROOT + "lib-1.0.0.jar", modJar("lib", "1.0.0")));
        List<String> log = new ArrayList<>();

        try (ModLoader loader = ModLoader.discover(mods, "client", log::add, ModPolicies.warn(),
                temp.resolve("jij"))) {
            assertEquals(List.of(), ids(loader));
        }
        assertTrue(log.stream().anyMatch(line -> line.startsWith(
                "[weaver] MOD JIJ SKIP parent=serveronly.jar")), String.join("\n", log));
    }

    @Test
    void nestedJarsOfARejectedParentAreDropped() throws IOException {
        Path mods = Files.createDirectories(temp.resolve("mods"));
        writeJar(mods.resolve("parent.jar"),
                entry("weaver.mod.json", descriptor("parent", "1.0.0",
                        "\"dependencies\":{\"absent\":\"*\"}")),
                entry(JijLayout.ROOT + "lib-1.0.0.jar", modJar("lib", "1.0.0")));
        List<String> log = new ArrayList<>();

        try (ModLoader loader = ModLoader.discover(mods, "client", log::add, ModPolicies.warn(),
                temp.resolve("jij"))) {
            assertEquals(List.of(), ids(loader));
            assertEquals(ModStatus.REJECTED_DEPENDENCY, byId(loader).get("lib").status());
        }
        assertTrue(log.stream().anyMatch(line -> line.startsWith(
                "[weaver] MOD SKIP id=lib reason=parent-rejected parent=parent")),
                String.join("\n", log));
    }

    @Test
    void aSharedNestedLibraryStaysForTheHealthyParentWhenTheOtherIsRejected() throws IOException {
        Path mods = Files.createDirectories(temp.resolve("mods"));
        // a.jar is broken (missing dependency) and is discovered first, so its copy of lib wins.
        writeJar(mods.resolve("a.jar"),
                entry("weaver.mod.json", descriptor("broken", "1.0.0",
                        "\"dependencies\":{\"absent\":\"*\",\"lib\":\"*\"}")),
                entry(JijLayout.ROOT + "lib-1.0.0.jar", modJar("lib", "1.0.0")));
        writeJar(mods.resolve("b.jar"),
                entry("weaver.mod.json", descriptor("healthy", "1.0.0",
                        "\"dependencies\":{\"lib\":\"*\"}")),
                entry(JijLayout.ROOT + "lib-1.0.0.jar", modJar("lib", "1.0.0")));

        try (ModLoader loader = ModLoader.discover(mods, "client", line -> { }, ModPolicies.warn(),
                temp.resolve("jij"))) {
            assertEquals(List.of("healthy", "lib"), ids(loader).stream().sorted().toList());
        }
    }

    @Test
    void aParentThatNeedsItsOwnNestedLibraryStillLoads() throws IOException {
        Path mods = Files.createDirectories(temp.resolve("mods"));
        writeJar(mods.resolve("parent.jar"),
                entry("weaver.mod.json", descriptor("parent", "1.0.0",
                        "\"dependencies\":{\"lib\":\"^1.0\"}")),
                entry(JijLayout.ROOT + "lib-1.0.0.jar", modJar("lib", "1.0.0")));

        try (ModLoader loader = ModLoader.discover(mods, "client", line -> { }, ModPolicies.warn(),
                temp.resolve("jij"))) {
            assertEquals(List.of("lib", "parent"), ids(loader));
        }
    }

    @Test
    void parentsWithTheSameSanitizedNameExtractIntoSeparateDirectories() throws IOException {
        Path mods = Files.createDirectories(temp.resolve("mods"));
        writeJar(mods.resolve("a b.jar"),
                entry("weaver.mod.json", descriptor("first", "1.0.0", null)),
                entry(JijLayout.ROOT + "one-1.0.0.jar", modJar("one", "1.0.0")));
        writeJar(mods.resolve("a-b.jar"),
                entry("weaver.mod.json", descriptor("second", "1.0.0", null)),
                entry(JijLayout.ROOT + "two-1.0.0.jar", modJar("two", "1.0.0")));
        Path jij = temp.resolve("jij");

        try (ModLoader loader = ModLoader.discover(mods, "client", line -> { }, ModPolicies.warn(),
                jij)) {
            assertEquals(List.of("first", "one", "second", "two"), ids(loader));
        }
        try (var dirs = Files.list(jij)) {
            assertEquals(2L, dirs.filter(Files::isDirectory).count());
        }
        assertNotEquals(ModLoader.shortHash(mods.resolve("a b.jar")),
                ModLoader.shortHash(mods.resolve("a-b.jar")));
    }

    private static List<String> ids(ModLoader loader) {
        return loader.mods().stream().map(mod -> mod.metadata().id()).sorted().toList();
    }

    private static Map<String, ModListEntry> byId(ModLoader loader) {
        return loader.report().entries().stream()
                .collect(Collectors.toMap(ModListEntry::id, entry -> entry, (left, right) -> left));
    }

    private record Entry(String name, byte[] bytes) {
    }

    private static Entry entry(String name, byte[] bytes) {
        return new Entry(name, bytes);
    }

    private static byte[] descriptor(String id, String version, String extra) {
        return ("{\"schemaVersion\":2,\"id\":\"" + id + "\",\"version\":\"" + version
                + "\",\"mainClass\":\"example." + id + ".Mod\""
                + (extra == null ? "" : "," + extra) + "}").getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] modJar(String id, String version) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        writeEntries(bytes, entry("weaver.mod.json", descriptor(id, version, null)));
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
