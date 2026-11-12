package net.weaver.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModLoaderTest {

    @Test
    void warnsOnDuplicateClassEntriesButLoadsBothMods() throws IOException {
        Path root = Files.createTempDirectory("weaver-modloader-conflict");
        try {
            Path modsDir = Files.createDirectories(root.resolve("mods"));
            Map<String, byte[]> first = new LinkedHashMap<>();
            first.put(ModMetadata.FILE, utf8(descriptor("moda")));
            first.put("com/example/Shared.class", new byte[]{1});
            first.put("com/example/OnlyA.class", new byte[]{2});
            first.put("META-INF/versions/9/com/example/Shared.class", new byte[]{3});
            first.put("META-INF/jars/embedded.jar", new byte[]{4});
            first.put("module-info.class", new byte[]{5});
            first.put("assets/example/data.txt", utf8("a"));
            writeJar(modsDir.resolve("moda.jar"), first);

            Map<String, byte[]> second = new LinkedHashMap<>();
            second.put(ModMetadata.FILE, utf8(descriptor("modb")));
            second.put("com/example/Shared.class", new byte[]{6});
            second.put("com/example/OnlyB.class", new byte[]{7});
            second.put("META-INF/versions/9/com/example/Shared.class", new byte[]{8});
            second.put("META-INF/jars/embedded.jar", new byte[]{9});
            second.put("module-info.class", new byte[]{10});
            second.put("assets/example/data.txt", utf8("b"));
            writeJar(modsDir.resolve("modb.jar"), second);

            List<String> log = new ArrayList<>();
            try (ModLoader loader = ModLoader.discover(modsDir, "client", log::add)) {
                assertEquals(List.of("moda", "modb"), loader.mods().stream()
                        .map(mod -> mod.metadata().id())
                        .sorted()
                        .toList());
            }

            List<String> conflicts = log.stream()
                    .filter(line -> line.contains("MOD CLASS CONFLICT"))
                    .toList();
            assertEquals(List.of("[weaver] MOD CLASS CONFLICT class=com/example/Shared.class"
                    + " mods=moda,modb first=moda"), conflicts, log.toString());
            assertFalse(log.stream().anyMatch(line -> line.contains("MOD CLASS CONFLICT")
                    && (line.contains("OnlyA") || line.contains("OnlyB"))), log.toString());
            assertFalse(log.stream().anyMatch(line -> line.contains("MOD CLASS CONFLICT")
                    && (line.contains("META-INF") || line.contains("module-info"))), log.toString());
        } finally {
            deleteRecursively(root);
        }
    }

    @Test
    void stillRejectsDuplicateModIds() throws IOException {
        Path root = Files.createTempDirectory("weaver-modloader-duplicate-id");
        try {
            Path modsDir = Files.createDirectories(root.resolve("mods"));
            Map<String, byte[]> entries = new LinkedHashMap<>();
            entries.put(ModMetadata.FILE, utf8(descriptor("same")));
            entries.put("com/example/Same.class", new byte[]{1});
            writeJar(modsDir.resolve("first.jar"), entries);
            writeJar(modsDir.resolve("second.jar"), entries);

            List<String> log = new ArrayList<>();
            try (ModLoader loader = ModLoader.discover(modsDir, "client", log::add)) {
                assertEquals(1, loader.mods().size());
                assertEquals("same", loader.mods().get(0).metadata().id());
            }

            assertTrue(log.stream().anyMatch(line -> line.contains("MOD SKIP id=same")
                    && line.contains("reason=duplicate-id")), log.toString());
            assertFalse(log.stream().anyMatch(line -> line.contains("MOD CLASS CONFLICT")),
                    log.toString());
        } finally {
            deleteRecursively(root);
        }
    }

    @Test
    void classEntriesSkipMetaInfModuleInfoAndResources() throws IOException {
        Path root = Files.createTempDirectory("weaver-modloader-entries");
        try {
            Path jarPath = root.resolve("mod.jar");
            Map<String, byte[]> entries = new LinkedHashMap<>();
            entries.put(ModMetadata.FILE, utf8(descriptor("moda")));
            entries.put("com/example/", new byte[0]);
            entries.put("com/example/One.class", new byte[]{1});
            entries.put("com/example/two/Two.class", new byte[]{2});
            entries.put("META-INF/MANIFEST.MF", utf8("Manifest-Version: 1.0\n"));
            entries.put("META-INF/versions/9/com/example/One.class", new byte[]{3});
            entries.put("META-INF/jars/embedded.jar", new byte[]{4});
            entries.put("module-info.class", new byte[]{5});
            entries.put("assets/example/data.txt", utf8("asset"));
            entries.put("com/example/notes.txt", utf8("notes"));
            writeJar(jarPath, entries);

            ModJar mod = ModJar.read(jarPath);
            try {
                assertEquals(List.of("com/example/One.class", "com/example/two/Two.class"),
                        mod.classEntries());
            } finally {
                mod.close();
            }
        } finally {
            deleteRecursively(root);
        }
    }

    private static String descriptor(String id) {
        return "{\"schemaVersion\":1,\"id\":\"" + id + "\",\"version\":\"1.0.0\","
                + "\"mainClass\":\"com.example.Mod\"}";
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static void writeJar(Path path, Map<String, byte[]> entries) throws IOException {
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(path))) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                out.putNextEntry(new JarEntry(entry.getKey()));
                out.write(entry.getValue());
                out.closeEntry();
            }
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
