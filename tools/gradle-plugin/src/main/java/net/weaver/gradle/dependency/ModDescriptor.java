package net.weaver.gradle.dependency;

import groovy.json.JsonSlurper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * The {@code weaver.mod.json} descriptor embedded in a Weaver mod jar.
 *
 * <p>Only the fields needed for dependency resolution are modeled. Values
 * absent from the JSON fall back to the same defaults the loader uses: version
 * {@code 0.0.0}, name equal to the id, side {@code both}, and Minecraft
 * version {@code 26.3}.
 *
 * @param id the stable mod id, matching {@code [a-z0-9_-]+}
 * @param version the mod version, never blank
 * @param name the human-readable name, defaulting to the id
 * @param mainClass the mod entrypoint, or an empty string when absent
 * @param mixins the mixin configuration names, never null
 * @param dependencies required mod dependencies keyed by mod id
 * @param optional optional mod dependencies keyed by mod id
 * @param side the supported side: {@code client}, {@code server}, or {@code both}
 * @param minecraft the Minecraft version the mod targets
 */
public record ModDescriptor(
        String id,
        String version,
        String name,
        String mainClass,
        List<String> mixins,
        Map<String, String> dependencies,
        Map<String, String> optional,
        String side,
        String minecraft) {

    /** Descriptor file name inside a mod jar. */
    public static final String FILE = "weaver.mod.json";

    /**
     * Creates a descriptor, normalizing optional fields and validating the id.
     *
     * @throws IllegalArgumentException if the id is missing or does not match
     *         {@code [a-z0-9_-]+}
     */
    public ModDescriptor {
        if (id == null || !id.matches("[a-z0-9_-]+")) {
            throw new IllegalArgumentException("invalid mod id '" + id + "' (expected [a-z0-9_-]+)");
        }
        version = version == null || version.isBlank() ? "0.0.0" : version;
        name = name == null || name.isBlank() ? id : name;
        mainClass = mainClass == null ? "" : mainClass;
        mixins = mixins == null ? List.of() : List.copyOf(mixins);
        dependencies = immutableMap(dependencies);
        optional = immutableMap(optional);
        side = side == null || side.isBlank() ? "both" : side;
        minecraft = minecraft == null || minecraft.isBlank() ? "26.3" : minecraft;
    }

    /**
     * Reads and parses the descriptor from a jar file.
     *
     * @param jar the mod jar to inspect
     * @return the parsed descriptor
     * @throws IOException if the jar cannot be read or does not contain a
     *         {@code weaver.mod.json}; the message names the jar
     * @throws IllegalArgumentException if the descriptor is present but
     *         malformed; the message names the jar
     */
    public static ModDescriptor read(File jar) throws IOException {
        Objects.requireNonNull(jar, "jar");
        if (!jar.isFile()) {
            throw new IOException("weaver.mod.json not found in " + jar.getName()
                    + ": file does not exist at " + jar.getPath());
        }
        try (ZipFile zip = new ZipFile(jar, StandardCharsets.UTF_8)) {
            ZipEntry entry = zip.getEntry(FILE);
            if (entry == null) {
                throw new IOException("weaver.mod.json not found in " + jar.getName());
            }
            try (InputStream input = zip.getInputStream(entry)) {
                try {
                    return parse(input.readAllBytes());
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("invalid weaver.mod.json in " + jar.getName()
                            + ": " + e.getMessage(), e);
                }
            }
        } catch (ZipException e) {
            throw new IOException("cannot read weaver.mod.json from " + jar.getName()
                    + ": " + e.getMessage(), e);
        }
    }

    /**
     * Parses a descriptor from raw UTF-8 JSON.
     *
     * @param json the descriptor bytes
     * @return the parsed descriptor
     * @throws IllegalArgumentException if the JSON is malformed, is not a JSON
     *         object, or declares an invalid mod id
     */
    public static ModDescriptor parse(byte[] json) {
        Objects.requireNonNull(json, "json");
        Object parsed;
        try {
            parsed = new JsonSlurper().parseText(new String(json, StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("invalid weaver.mod.json: " + oneLine(e.getMessage()), e);
        }
        if (!(parsed instanceof Map<?, ?> root)) {
            throw new IllegalArgumentException("weaver.mod.json must be a JSON object");
        }
        return new ModDescriptor(
                string(root.get("id")),
                string(root.get("version")),
                string(root.get("name")),
                string(root.get("mainClass")),
                strings(root.get("mixins")),
                stringMap(root.get("dependencies")),
                stringMap(root.get("optional")),
                string(root.get("side")),
                string(root.get("minecraft")));
    }

    private static Map<String, String> immutableMap(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<String, String> copy = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                copy.put(entry.getKey(), entry.getValue());
            }
        }
        return Collections.unmodifiableMap(copy);
    }

    private static String string(Object value) {
        if (value instanceof CharSequence || value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        return null;
    }

    private static List<String> strings(Object value) {
        List<String> out = new ArrayList<>();
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                String text = string(item);
                if (text != null) {
                    out.add(text);
                }
            }
            return out;
        }
        String text = string(value);
        if (text != null) {
            out.add(text);
        }
        return out;
    }

    private static Map<String, String> stringMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            String text = string(entry.getValue());
            if (text != null) {
                out.put(entry.getKey().toString(), text);
            }
        }
        return out;
    }

    private static String oneLine(String value) {
        return value == null ? "" : value.replace('\r', ' ').replace('\n', ' ').trim();
    }
}
