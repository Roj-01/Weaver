package weaver.datagen;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Adds a placed feature to an existing vanilla biome without replacing the file by hand.
 *
 * <p>Why this exists: a data pack can only override a biome wholesale, so a module that wants one
 * ore in the overworld would otherwise have to ship a copy of every biome it touches — and that copy
 * would silently freeze every other vanilla value at the moment it was made. NeoForge solves this
 * with a runtime {@code biome_modifier}; Weaver solves it at build time, which is honest: the output
 * is a normal biome file the game already understands, and the merge is visible in the generated
 * JSON.
 *
 * <p>Input: the biome id, the placed-feature id, and the generation step. Output: one file at
 * {@code data/<namespace>/worldgen/biome/<biome path>.json}.
 */
public final class BiomeMerge {

    /** Vanilla step order inside {@code features} — the array is positional, so the index matters. */
    public static final List<String> STEPS = List.of(
            "raw_generation", "lakes", "local_modifications", "underground_structures",
            "surface_structures", "strongholds", "underground_ores", "underground_decoration",
            "fluid_springs", "vegetal_decoration", "top_layer_modification");

    private BiomeMerge() {
    }

    public record Result(boolean ok, String biome, String feature, String step, Path file, String reason) {
    }

    /**
     * @param gameJar  the client jar that carries {@code data/minecraft/worldgen/biome/*.json}
     * @param namespace target namespace for the generated file
     * @param biomeId  full biome id, {@code minecraft:plains}
     * @param featureId full placed-feature id, {@code ruby:gem_ore_placed}
     * @param step     a name from {@link #STEPS}
     * @param outDir   root that receives {@code data/...}
     */
    public static Result merge(Path gameJar, String namespace, String biomeId, String featureId,
                               String step, Path outDir) {
        if (gameJar == null || !Files.isRegularFile(gameJar)) {
            return new Result(false, biomeId, featureId, step, null, "game-jar-missing");
        }
        int index = STEPS.indexOf(step);
        if (index < 0) {
            return new Result(false, biomeId, featureId, step, null, "unknown-step:" + step);
        }
        String biomePath = pathOf(biomeId);
        if (biomePath == null) {
            return new Result(false, biomeId, featureId, step, null, "bad-biome-id");
        }
        if (pathOf(featureId) == null) {
            return new Result(false, biomeId, featureId, step, null, "bad-feature-id");
        }
        // Fail closed on a feature that does not exist: vanilla logs and drops an unknown feature
        // reference, so a silent pass here would ship a biome that quietly does nothing.
        String featurePath = pathOf(featureId);
        String featureEntry = "data/" + featureId.substring(0, featureId.indexOf(':'))
                + "/worldgen/placed_feature/" + featurePath + ".json";
        boolean featureKnown = Files.isRegularFile(outDir.resolve(featureEntry));
        if (!featureKnown) {
            try (ZipFile probe = new ZipFile(gameJar.toFile())) {
                featureKnown = probe.getEntry(featureEntry) != null;
            } catch (Exception e) {
                return new Result(false, biomeId, featureId, step, null, "read:" + e.getMessage());
            }
        }
        if (!featureKnown) {
            return new Result(false, biomeId, featureId, step, null, "feature-unknown:" + featureEntry);
        }

        String entryName = "data/minecraft/worldgen/biome/" + biomePath + ".json";
        String outPath = "data/" + safeNamespace(namespace) + "/worldgen/biome/" + biomePath + ".json";
        Path target = outDir.resolve(outPath);

        // A previous merge in the same run may already have written this biome. Read that copy, not
        // the jar, otherwise two merges of different features each start from vanilla and the second
        // silently discards the first (measured: two identical merges both reported ok).
        String text = null;
        if (Files.isRegularFile(target)) {
            try {
                text = Files.readString(target, StandardCharsets.UTF_8);
            } catch (Exception e) {
                return new Result(false, biomeId, featureId, step, null, "read-merged:" + e.getMessage());
            }
        }
        if (text == null) {
            try (ZipFile zip = new ZipFile(gameJar.toFile())) {
                ZipEntry entry = zip.getEntry(entryName);
                if (entry == null) {
                    return new Result(false, biomeId, featureId, step, null, "biome-not-in-jar:" + biomePath);
                }
                try (InputStream in = zip.getInputStream(entry)) {
                    text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
            } catch (Exception e) {
                return new Result(false, biomeId, featureId, step, null, "read:" + e.getMessage());
            }
        }

        Map<String, Object> root = asObject(Json.parse(text));
        if (root == null) {
            return new Result(false, biomeId, featureId, step, null, "biome-not-an-object");
        }
        Object featuresNode = root.get("features");
        List<Object> steps = asList(featuresNode);
        if (steps == null) {
            return new Result(false, biomeId, featureId, step, null, "no-features-array");
        }
        if (steps.size() != STEPS.size()) {
            // do not guess: a different shape means a different game version, and a wrong index
            // would drop the feature into a random step
            return new Result(false, biomeId, featureId, step, null,
                    "unexpected-feature-steps:" + steps.size());
        }
        List<Object> bucket = asList(steps.get(index));
        if (bucket == null) {
            bucket = new ArrayList<>();
            steps.set(index, bucket);
        }
        if (bucket.contains(featureId)) {
            return new Result(false, biomeId, featureId, step, null, "already-present");
        }
        bucket.add(featureId);

        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, Json.write(root) + "\n", StandardCharsets.UTF_8);
        } catch (Exception e) {
            return new Result(false, biomeId, featureId, step, null, "write:" + e.getMessage());
        }
        return new Result(true, biomeId, featureId, step, target, "ok");
    }

    // ------------------------------------------------------------------ helpers

    private static String pathOf(String id) {
        if (id == null) {
            return null;
        }
        int colon = id.indexOf(':');
        if (colon <= 0 || colon == id.length() - 1) {
            return null;
        }
        String namespace = id.substring(0, colon);
        String path = id.substring(colon + 1);
        if (!namespace.matches("[a-z0-9_.-]+") || !path.matches("[a-z0-9_./-]+") || path.contains("..")) {
            return null;
        }
        return path;
    }

    private static String safeNamespace(String namespace) {
        String value = namespace == null ? "weaver" : namespace.toLowerCase(Locale.ROOT);
        return value.replaceAll("[^a-z0-9_.-]", "_");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asObject(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : null;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object value) {
        return value instanceof List ? (List<Object>) value : null;
    }

    /** Kept for callers that want to build the patch without touching the jar (tests use it). */
    public static Map<String, Object> emptyPatch() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("features", new ArrayList<>(STEPS.size()));
        return root;
    }
}
