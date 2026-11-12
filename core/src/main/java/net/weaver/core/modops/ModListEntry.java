package net.weaver.core.modops;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One discovered candidate mod jar as exposed to reports.
 *
 * <p>Entries are immutable snapshots of the descriptor plus the loader's final
 * disposition. Rejected entries keep their parsed metadata whenever the jar was
 * readable, so a UI can display why a jar did not load without reopening it.
 *
 * @param id                  mod id from {@code weaver.mod.json}; for unreadable jars
 *                            the jar file name without the {@code .jar} suffix
 * @param version             declared version, or {@code "unknown"} for unreadable jars
 * @param name                display name, defaulting to the id in the descriptor
 * @param side                declared side: {@code client}, {@code server}, or {@code both}
 * @param minecraft           declared Minecraft version
 * @param mainClass           declared entry point class
 * @param dependencies        required dependency id to version-range map
 * @param optional            optional dependency id to version-range map
 * @param breaks              incompatible mod id to version-range map
 * @param mixins              declared mixin configuration names
 * @param accessTransformers  declared access transformer file names
 * @param loadOrder           zero-based load position, or {@code -1} when not loaded
 * @param status              final disposition
 * @param statusDetail        human-readable rejection detail, or empty when loaded
 * @param file                jar file name, without any directory
 */
public record ModListEntry(
        String id,
        String version,
        String name,
        String side,
        String minecraft,
        String mainClass,
        Map<String, String> dependencies,
        Map<String, String> optional,
        Map<String, String> breaks,
        List<String> mixins,
        List<String> accessTransformers,
        int loadOrder,
        ModStatus status,
        String statusDetail,
        String file) {

    /**
     * Creates an entry, copies its collections, and validates that required
     * components are present.
     *
     * @throws NullPointerException     if a required component is {@code null}
     * @throws IllegalArgumentException if {@code loadOrder} is less than {@code -1}
     */
    public ModListEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(minecraft, "minecraft");
        Objects.requireNonNull(mainClass, "mainClass");
        dependencies = immutableMap(dependencies, "dependencies");
        optional = immutableMap(optional, "optional");
        breaks = immutableMap(breaks, "breaks");
        mixins = List.copyOf(Objects.requireNonNull(mixins, "mixins"));
        accessTransformers = List.copyOf(Objects.requireNonNull(accessTransformers, "accessTransformers"));
        status = Objects.requireNonNull(status, "status");
        statusDetail = Objects.requireNonNull(statusDetail, "statusDetail");
        file = Objects.requireNonNull(file, "file");
        if (loadOrder < -1) {
            throw new IllegalArgumentException("loadOrder must be >= -1: " + loadOrder);
        }
    }

    /**
     * Returns a copy of the entry with a different status and detail.
     *
     * @param newStatus the replacement status
     * @param detail    the replacement detail
     * @return a new entry with the same metadata and {@code loadOrder = -1}
     */
    public ModListEntry withStatus(ModStatus newStatus, String detail) {
        return new ModListEntry(id, version, name, side, minecraft, mainClass,
                dependencies, optional, breaks, mixins, accessTransformers,
                -1, newStatus, detail, file);
    }

    /**
     * Returns a copy of the entry with a load position.
     *
     * @param newLoadOrder the zero-based load position
     * @return a new entry with the same status and detail
     */
    public ModListEntry withLoadOrder(int newLoadOrder) {
        return new ModListEntry(id, version, name, side, minecraft, mainClass,
                dependencies, optional, breaks, mixins, accessTransformers,
                newLoadOrder, status, statusDetail, file);
    }

    private static Map<String, String> immutableMap(Map<String, String> source, String name) {
        Objects.requireNonNull(source, name);
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
