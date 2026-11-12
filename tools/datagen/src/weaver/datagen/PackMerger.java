package weaver.datagen;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Merges several generated pack directories into one pack, applying layer priority and the tag
 * merge rules the runtime loader expects.
 *
 * <p>A layer is a pack directory produced by this tool (the tree under a {@code --out} root). The
 * merger reads every regular file once, decides a winner per path, writes the result sorted by
 * path, and then re-verifies the merged tree with {@link Verifier}.
 */
public final class PackMerger {

    private static final String MARKER = ".weaver-datagen";

    /**
     * One generated pack directory to merge.
     *
     * @param root     the pack root
     * @param priority the layer's merge priority; higher wins for non-tag files and supplies the
     *                 effective priority of tags that do not declare their own
     * @param id       a stable identifier used in reports and diagnostics
     */
    public record Layer(Path root, int priority, String id) {
        public Layer {
            Objects.requireNonNull(root, "root");
            Objects.requireNonNull(id, "id");
        }
    }

    /**
     * One merge diagnostic.
     *
     * @param layerId the layer that caused the problem, or {@code <merged>} for a problem found
     *                in the finished output
     * @param path    the relative file path, or an empty string for a layer/directory-level
     *                problem
     * @param reason  a one-line explanation
     */
    public record Problem(String layerId, String path, String reason) {
        @Override
        public String toString() {
            return path == null || path.isEmpty() ? layerId + ": " + reason
                    : layerId + ": " + path + ": " + reason;
        }
    }

    /**
     * The outcome of a merge.
     *
     * @param ok          true when there are no problems and no conflicts
     * @param files       the written pack files, sorted by '/'-normalized relative path; the
     *                    marker is not listed
     * @param problems    per-file or per-layer problems plus every {@link Verifier} problem
     * @param conflicts   same-priority non-tag paths whose bytes differ across layers
     * @param fingerprint the {@link Verifier#fingerprint(Path)} of the finished output, or an
     *                    empty string when the merge was refused before writing
     */
    public record Report(boolean ok, List<String> files, List<Problem> problems,
                         List<String> conflicts, String fingerprint) {
        public Report {
            files = List.copyOf(files);
            problems = List.copyOf(problems);
            conflicts = List.copyOf(conflicts);
        }
    }

    private PackMerger() {
    }

    /**
     * Merges {@code layers} into {@code outDir}.
     *
     * <p>The existing {@code outDir} is cleared first only when it is empty or carries a
     * {@code .weaver-datagen} marker; any other non-empty directory is left untouched and the
     * merge fails with a problem. Layers are processed from low priority to high priority.
     *
     * <p>For a non-tag file (everything outside {@code data/<ns>/tags/} and
     * {@code data/<ns>/weaver/tags/}, including {@code pack.mcmeta} and
     * {@code pack.data.mcmeta}) the highest-priority layer wins. When several layers share that
     * highest priority and their bytes differ, the first layer in input order is kept and a
     * conflict is recorded; identical bytes are not a conflict.
     *
     * <p>A tag file is merged across layers. Each layer's {@code values} are parsed, ordered by
     * effective priority (the file's own {@code priority} when declared, otherwise the layer's
     * priority) descending and then by input order, and are included until and including the
     * first {@code replace} layer. Values are concatenated in that order and deduplicated
     * exactly, including {@code #} references. The merged file always carries
     * {@code "replace": true}; {@code priority} is written for custom tags
     * ({@code data/<ns>/weaver/tags/...}) and for vanilla tags only when a contributing layer
     * declared one. A layer whose tag JSON is malformed contributes nothing and adds a problem.
     *
     * <p>Output files are written in sorted path order and non-tag bytes are preserved exactly,
     * so the same layers in the same order produce a byte-identical tree and fingerprint. After
     * writing, the tree is checked with {@link Verifier#verify(Path)} and every verifier problem
     * becomes a {@link Problem} with layer id {@code <merged>}.
     *
     * @param layers the pack layers, in input order
     * @param outDir the merged pack root
     * @return the merge report; per-file problems never throw
     * @throws IOException only when IO prevents the merge from producing an output tree
     */
    public static Report merge(List<Layer> layers, Path outDir) throws IOException {
        Objects.requireNonNull(layers, "layers");
        Objects.requireNonNull(outDir, "outDir");
        Path target = outDir.toAbsolutePath().normalize();

        List<Problem> problems = new ArrayList<>();
        List<String> conflicts = new ArrayList<>();
        Map<String, List<Source>> byPath = new TreeMap<>();

        for (int order = 0; order < layers.size(); order++) {
            collect(order, layers.get(order), byPath, problems);
        }

        if (!prepare(target)) {
            problems.add(new Problem("<merge>", "", "output directory is not empty and was not"
                    + " created by this tool: " + slashes(target)));
            return new Report(false, List.of(), problems, conflicts, "");
        }

        Map<String, byte[]> output = new TreeMap<>();
        for (Map.Entry<String, List<Source>> entry : byPath.entrySet()) {
            String rel = entry.getKey();
            List<Source> sources = entry.getValue();
            if (sources.isEmpty()) {
                continue;
            }
            if (isTag(rel)) {
                byte[] merged = mergeTag(rel, sources, problems);
                if (merged != null) {
                    output.put(rel, merged);
                }
            } else {
                output.put(rel, mergeSingle(rel, sources, conflicts));
            }
        }

        for (Map.Entry<String, byte[]> entry : output.entrySet()) {
            Path file = target.resolve(entry.getKey());
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.write(file, entry.getValue());
        }
        Files.writeString(target.resolve(MARKER),
                "merged by tools/weaver-datagen — safe to delete\n");

        Verifier.Report verified = Verifier.verify(target);
        for (String problem : verified.problems()) {
            problems.add(new Problem("<merged>", "", problem));
        }
        String fingerprint = Verifier.fingerprint(target);
        boolean ok = problems.isEmpty() && conflicts.isEmpty();
        return new Report(ok, List.copyOf(output.keySet()), problems, conflicts, fingerprint);
    }

    private static void collect(int order, Layer layer, Map<String, List<Source>> byPath,
                                List<Problem> problems) {
        Path root = layer.root().toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            problems.add(new Problem(layer.id(), "",
                    "layer directory does not exist: " + slashes(root)));
            return;
        }
        List<Path> paths;
        try (Stream<Path> walk = Files.walk(root)) {
            paths = walk.filter(Files::isRegularFile).sorted().toList();
        } catch (IOException e) {
            problems.add(new Problem(layer.id(), "", "unreadable layer: " + oneLine(e)));
            return;
        }
        for (Path path : paths) {
            String rel = root.relativize(path).toString().replace('\\', '/');
            if (rel.equals(MARKER)) {
                continue;
            }
            byte[] bytes;
            try {
                bytes = Files.readAllBytes(path);
            } catch (IOException e) {
                problems.add(new Problem(layer.id(), rel, "unreadable: " + oneLine(e)));
                continue;
            }
            byPath.computeIfAbsent(rel, k -> new ArrayList<>())
                    .add(new Source(order, layer, bytes));
        }
    }

    private static byte[] mergeSingle(String rel, List<Source> sources, List<String> conflicts) {
        int highest = Integer.MIN_VALUE;
        for (Source source : sources) {
            highest = Math.max(highest, source.layer().priority());
        }
        List<Source> candidates = new ArrayList<>();
        for (Source source : sources) {
            if (source.layer().priority() == highest) {
                candidates.add(source);
            }
        }
        Source kept = candidates.get(0);
        boolean differs = false;
        StringBuilder ids = new StringBuilder();
        for (Source candidate : candidates) {
            if (ids.length() > 0) {
                ids.append(',');
            }
            ids.append(candidate.layer().id());
            if (!Arrays.equals(kept.bytes(), candidate.bytes())) {
                differs = true;
            }
        }
        if (differs) {
            conflicts.add(rel + " layers=" + ids + " priority=" + highest);
        }
        return kept.bytes();
    }

    private static byte[] mergeTag(String rel, List<Source> sources, List<Problem> problems) {
        List<TagSource> parsed = new ArrayList<>();
        for (Source source : sources) {
            TagSource tag = parseTag(rel, source, problems);
            if (tag != null) {
                parsed.add(tag);
            }
        }
        if (parsed.isEmpty()) {
            return null;
        }
        parsed.sort(Comparator.comparingInt(TagSource::effectivePriority).reversed()
                .thenComparingInt(TagSource::order));

        Set<String> values = new LinkedHashSet<>();
        int highest = Integer.MIN_VALUE;
        boolean declared = false;
        for (TagSource tag : parsed) {
            for (String value : tag.values()) {
                values.add(value);
            }
            highest = Math.max(highest, tag.effectivePriority());
            declared |= tag.declaredPriority();
            if (tag.replace()) {
                break;
            }
        }

        Map<String, Object> body = Json.obj("values", new ArrayList<>(values));
        if (isCustomTag(rel) || declared) {
            body.put("priority", highest);
        }
        body.put("replace", true);
        return Json.write(body).getBytes(StandardCharsets.UTF_8);
    }

    private static TagSource parseTag(String rel, Source source, List<Problem> problems) {
        String layerId = source.layer().id();
        Object root;
        try {
            root = Json.parse(new String(source.bytes(), StandardCharsets.UTF_8));
        } catch (Json.ParseError e) {
            problems.add(new Problem(layerId, rel, "not valid tag JSON: " + e.getMessage()));
            return null;
        }
        if (!(root instanceof Map<?, ?>)) {
            problems.add(new Problem(layerId, rel,
                    "tag root is " + Json.typeName(root) + ", not an object"));
            return null;
        }
        Map<String, Object> obj = Json.asObject(root, rel);
        Object rawValues = obj.get("values");
        if (!(rawValues instanceof List<?> list)) {
            problems.add(new Problem(layerId, rel, "missing or invalid \"values\" array"));
            return null;
        }
        List<String> values = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof String value)) {
                problems.add(new Problem(layerId, rel,
                        "tag value is " + Json.typeName(item) + ", not a string"));
                return null;
            }
            values.add(value);
        }

        int effective = source.layer().priority();
        boolean declared = false;
        if (obj.containsKey("priority")) {
            Object value = obj.get("priority");
            if (!(value instanceof Number number) || value instanceof Double
                    || value instanceof Float) {
                problems.add(new Problem(layerId, rel,
                        "priority is " + Json.typeName(value) + ", not an integer"));
                return null;
            }
            long raw = number.longValue();
            if (raw < Integer.MIN_VALUE || raw > Integer.MAX_VALUE) {
                problems.add(new Problem(layerId, rel,
                        "priority " + raw + " does not fit in a 32-bit integer"));
                return null;
            }
            effective = (int) raw;
            declared = true;
        }

        boolean replace = false;
        if (obj.containsKey("replace")) {
            Object value = obj.get("replace");
            if (!(value instanceof Boolean flag)) {
                problems.add(new Problem(layerId, rel,
                        "replace is " + Json.typeName(value) + ", not a boolean"));
                return null;
            }
            replace = flag;
        }
        return new TagSource(source.order(), effective, declared, values, replace);
    }

    private static boolean prepare(Path outDir) throws IOException {
        if (!Files.isDirectory(outDir)) {
            Files.createDirectories(outDir);
            return true;
        }
        boolean marked = Files.exists(outDir.resolve(MARKER));
        boolean empty;
        try (Stream<Path> list = Files.list(outDir)) {
            empty = list.findAny().isEmpty();
        }
        if (!marked && !empty) {
            return false;
        }
        List<Path> paths = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(outDir)) {
            walk.forEach(paths::add);
        }
        paths.sort(Comparator.reverseOrder());
        for (Path path : paths) {
            Files.deleteIfExists(path);
        }
        return true;
    }

    private static boolean isTag(String rel) {
        String[] parts = rel.split("/");
        if (parts.length < 4 || !parts[0].equals("data")) {
            return false;
        }
        if (parts[2].equals("tags")) {
            return true;
        }
        return parts.length >= 5 && parts[2].equals("weaver") && parts[3].equals("tags");
    }

    private static boolean isCustomTag(String rel) {
        String[] parts = rel.split("/");
        return parts.length >= 5 && parts[0].equals("data") && parts[2].equals("weaver")
                && parts[3].equals("tags");
    }

    private static String slashes(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static String oneLine(Throwable t) {
        String message = t.getMessage();
        return (message == null ? t.getClass().getSimpleName() : message).replace('\n', ' ')
                .replace('\r', ' ');
    }

    private record Source(int order, Layer layer, byte[] bytes) {
    }

    private record TagSource(int order, int effectivePriority, boolean declaredPriority,
                             List<String> values, boolean replace) {
    }
}
