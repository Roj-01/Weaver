package net.weaver.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.TreeMap;

/**
 * Writes a {@code weaver.mod.json} v2 descriptor into the processed resources.
 *
 * <p>Dependency and optional-dependency maps are emitted in sorted key order so
 * the descriptor is byte-for-byte deterministic across machines.
 */
public abstract class WeaverModJsonTask extends DefaultTask {

    @Input
    public abstract Property<String> getModId();

    @Input
    public abstract Property<String> getModName();

    @Input
    public abstract Property<String> getModVersion();

    @Input
    public abstract Property<String> getMinecraftVersion();

    @Input
    public abstract Property<String> getSide();

    @Input
    public abstract Property<String> getMainClass();

    @Input
    public abstract ListProperty<String> getMixins();

    @Input
    @Optional
    public abstract MapProperty<String, String> getDependencies();

    @Input
    @Optional
    public abstract MapProperty<String, String> getOptionalDependencies();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @TaskAction
    public void generate() throws IOException {
        String main = getMainClass().getOrElse("").trim();
        if (main.isEmpty()) {
            throw new IllegalStateException("weaver { mainClass.set(\"...\") } is required");
        }
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"schemaVersion\": 2,\n");
        json.append("  \"id\": ").append(quote(getModId().get())).append(",\n");
        json.append("  \"name\": ").append(quote(getModName().get())).append(",\n");
        json.append("  \"version\": ").append(quote(getModVersion().get())).append(",\n");
        json.append("  \"mainClass\": ").append(quote(main)).append(",\n");
        json.append("  \"minecraft\": ").append(quote(getMinecraftVersion().get())).append(",\n");
        json.append("  \"side\": ").append(quote(getSide().get()));
        if (!getMixins().getOrElse(java.util.List.of()).isEmpty()) {
            json.append(",\n  \"mixins\": [");
            boolean first = true;
            for (String mixin : getMixins().get()) {
                if (!first) {
                    json.append(", ");
                }
                json.append(quote(mixin));
                first = false;
            }
            json.append("]");
        }
        appendMap(json, "dependencies", getDependencies().getOrElse(Map.of()));
        appendMap(json, "optional", getOptionalDependencies().getOrElse(Map.of()));
        json.append("\n}\n");
        java.io.File file = getOutputFile().get().getAsFile();
        Files.createDirectories(file.toPath().getParent());
        Files.writeString(file.toPath(), json.toString(), StandardCharsets.UTF_8);
    }

    private static void appendMap(StringBuilder json, String key, Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        Map<String, String> sorted = new TreeMap<>(values);
        json.append(",\n  ").append(quote(key)).append(": {");
        boolean first = true;
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            if (!first) {
                json.append(",");
            }
            json.append("\n    ").append(quote(entry.getKey())).append(": ")
                    .append(quote(entry.getValue()));
            first = false;
        }
        json.append("\n  }");
    }

    private static String quote(String value) {
        String text = value == null ? "" : value;
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
