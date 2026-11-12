package net.weaver.core.modops;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.weaver.core.ModMetadata;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

final class ModOpsFixtures {

    private ModOpsFixtures() {
    }

    static Path createRoot(String prefix) throws IOException {
        return Files.createTempDirectory(prefix);
    }

    static Path modsDir(Path root) throws IOException {
        return Files.createDirectories(root.resolve("mods"));
    }

    static Builder mod(String id, String version) {
        return new Builder(id, version);
    }

    static Path writeRawJar(Path dir, String fileName, Map<String, byte[]> entries)
            throws IOException {
        Path path = dir.resolve(fileName);
        writeJar(path, entries);
        return path;
    }

    static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    static void writeJar(Path path, Map<String, byte[]> entries) throws IOException {
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(path))) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                out.putNextEntry(new JarEntry(entry.getKey()));
                out.write(entry.getValue());
                out.closeEntry();
            }
        }
    }

    static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    static final class Builder {

        private final JsonObject descriptor = new JsonObject();
        private final Map<String, byte[]> entries = new LinkedHashMap<>();

        private Builder(String id, String version) {
            descriptor.addProperty("schemaVersion", 2);
            descriptor.addProperty("id", id);
            descriptor.addProperty("version", version);
            descriptor.addProperty("name", id);
            descriptor.addProperty("mainClass", "com.example.Mod");
            descriptor.addProperty("side", "both");
            descriptor.addProperty("minecraft", "26.3");
        }

        Builder name(String name) {
            descriptor.addProperty("name", name);
            return this;
        }

        Builder side(String side) {
            descriptor.addProperty("side", side);
            return this;
        }

        Builder minecraft(String minecraft) {
            descriptor.addProperty("minecraft", minecraft);
            return this;
        }

        Builder mainClass(String mainClass) {
            descriptor.addProperty("mainClass", mainClass);
            return this;
        }

        Builder dependency(String id, String range) {
            map("dependencies").addProperty(id, range);
            return this;
        }

        Builder optional(String id, String range) {
            map("optional").addProperty(id, range);
            return this;
        }

        Builder breaks(String id, String range) {
            map("breaks").addProperty(id, range);
            return this;
        }

        Builder mixin(String config) {
            array("mixins").add(config);
            entries.put(config, utf8("{\"package\":\"com.example.mixin\"}"));
            return this;
        }

        Builder classEntry(String entry) {
            entries.put(entry, new byte[]{1});
            return this;
        }

        Path write(Path dir, String fileName) throws IOException {
            entries.put(ModMetadata.FILE, utf8(descriptor.toString()));
            Path path = dir.resolve(fileName);
            writeJar(path, entries);
            return path;
        }

        private JsonObject map(String key) {
            JsonObject existing = descriptor.getAsJsonObject(key);
            if (existing == null) {
                existing = new JsonObject();
                descriptor.add(key, existing);
            }
            return existing;
        }

        private JsonArray array(String key) {
            JsonArray existing = descriptor.getAsJsonArray(key);
            if (existing == null) {
                existing = new JsonArray();
                descriptor.add(key, existing);
            }
            return existing;
        }
    }
}
