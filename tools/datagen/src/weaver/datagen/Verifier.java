package weaver.datagen;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

/**
 * {@code --verify}: re-reads every file the generator wrote and checks it the way the game will
 * — parse it as JSON, then assert the exact keys 26.3 needs for that file family.
 *
 * <p>The check is deliberately independent of {@link Generator}: it walks the output directory
 * from disk and re-parses the bytes, so a writer bug cannot vouch for itself.
 */
public final class Verifier {

    public record Report(List<String> files, List<String> problems, List<String> warnings) {
        public boolean ok() {
            return problems.isEmpty();
        }
    }

    private static final Pattern NAMESPACED =
            Pattern.compile("^[a-z0-9_.-]+:[a-z0-9_./-]+$");
    private static final Pattern NAMESPACED_OR_REF =
            Pattern.compile("^#?[a-z0-9_.-]+:[a-z0-9_./-]+$");

    private Verifier() {
    }

    /**
     * Hashes every regular file under {@code outDir} into one lowercase hex SHA-256 digest.
     *
     * <p>The digest covers each file's '/'-normalized relative path and its exact bytes, taken in
     * sorted path order, so two trees with the same content and layout have the same fingerprint
     * regardless of how or where they were written. The directory must exist and be readable.
     *
     * @param outDir the pack root to fingerprint
     * @return 64 lowercase hexadecimal characters
     * @throws IOException when the directory is missing, unreadable, or a file cannot be read
     */
    public static String fingerprint(Path outDir) throws IOException {
        if (!Files.isDirectory(outDir)) {
            throw new IOException("not a directory: " + outDir);
        }
        if (!Files.isReadable(outDir)) {
            throw new IOException("not readable: " + outDir);
        }

        List<String> rels = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(outDir)) {
            for (Path path : walk.filter(Files::isRegularFile).toList()) {
                rels.add(outDir.relativize(path).toString().replace('\\', '/'));
            }
        }
        rels.sort(String::compareTo);

        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
        for (String rel : rels) {
            byte[] bytes = Files.readAllBytes(outDir.resolve(rel));
            digest.update(rel.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '\n');
            digest.update(bytes);
            digest.update((byte) '\n');
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public static Report verify(Path outDir) throws IOException {
        List<String> problems = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> files = new ArrayList<>();

        if (!Files.isDirectory(outDir)) {
            problems.add("output directory does not exist: " + outDir);
            return new Report(files, problems, warnings);
        }

        List<Path> onDisk;
        try (Stream<Path> walk = Files.walk(outDir)) {
            onDisk = walk.filter(Files::isRegularFile).sorted().toList();
        }

        Set<String> jsonPaths = new LinkedHashSet<>();
        for (Path path : onDisk) {
            String rel = outDir.relativize(path).toString().replace('\\', '/');
            if (rel.equals(".weaver-datagen")) {
                continue;
            }
            files.add(rel);
            jsonPaths.add(rel);
        }

        for (String rel : files) {
            Path path = outDir.resolve(rel);
            try {
                if (rel.endsWith(".png")) {
                    checkPng(path, rel, problems);
                } else if (rel.endsWith(".json") || rel.endsWith(".mcmeta")) {
                    String text = Files.readString(path, StandardCharsets.UTF_8);
                    Object root = Json.parse(text);
                    checkJson(rel, root, problems);
                } else {
                    problems.add(rel + ": unexpected file type (only .json/.mcmeta/.png are generated)");
                }
            } catch (Json.ParseError e) {
                problems.add(rel + ": not valid JSON — " + e.getMessage());
            } catch (IOException e) {
                problems.add(rel + ": unreadable — " + e.getMessage());
            }
        }

        checkReferences(files, outDir, problems, warnings);
        return new Report(files, problems, warnings);
    }

    // ---------------------------------------------------------------- per-family checks

    private static void checkJson(String rel, Object root, List<String> problems) {
        String kind = kindOf(rel);
        if (kind == null) {
            problems.add(rel + ": not a path this generator produces"
                    + " (expected assets/<ns>/… or data/<ns>/… or pack.mcmeta)");
            return;
        }
        switch (kind) {
            case "blockstate" -> checkBlockstate(rel, root, problems);
            case "block-model" -> checkBlockModel(rel, root, problems);
            case "item-model" -> checkItemModel(rel, root, problems);
            case "item-def" -> checkItemDef(rel, root, problems);
            case "lang" -> checkLang(rel, root, problems);
            case "loot" -> checkLoot(rel, root, problems);
            case "recipe" -> checkRecipe(rel, root, problems);
            case "tag" -> checkTag(rel, root, problems);
            case "advancement" -> checkAdvancement(rel, root, problems);
            case "custom-registry" -> checkCustomRegistry(rel, root, problems);
            case "custom-tag" -> checkCustomTag(rel, root, problems);
            case "pack-meta" -> checkPackMeta(rel, root, problems);
            default -> problems.add(rel + ": unknown family " + kind);
        }
    }

    /** {@code assets/<ns>/blockstates/x.json} — measured: assets/minecraft/blockstates/stone_bricks.json. */
    private static void checkBlockstate(String rel, Object root, List<String> problems) {
        Map<String, Object> obj = object(rel, root, problems);
        if (obj == null) {
            return;
        }
        Map<String, Object> variants = objectMember(rel, obj, "variants", problems);
        if (variants == null) {
            return;
        }
        if (variants.isEmpty()) {
            problems.add(rel + ": \"variants\" is empty");
            return;
        }
        if (!variants.containsKey("")) {
            problems.add(rel + ": a single-cube blockstate must be keyed by the empty variant \"\"");
        }
        for (Map.Entry<String, Object> e : variants.entrySet()) {
            Map<String, Object> variant = object(rel + ".variants[\"" + e.getKey() + "\"]", e.getValue(), problems);
            if (variant == null) {
                continue;
            }
            stringMember(rel, variant, "model", problems);
        }
    }

    /** {@code assets/<ns>/models/block/x.json} — measured: assets/minecraft/models/block/stone_bricks.json. */
    private static void checkBlockModel(String rel, Object root, List<String> problems) {
        Map<String, Object> obj = object(rel, root, problems);
        if (obj == null) {
            return;
        }
        String parent = stringMember(rel, obj, "parent", problems);
        if (parent != null && !parent.equals("minecraft:block/cube_all")) {
            problems.add(rel + ": parent must be \"minecraft:block/cube_all\" for a generated"
                    + " single cube, found \"" + parent + "\"");
        }
        Map<String, Object> textures = objectMember(rel, obj, "textures", problems);
        if (textures != null) {
            stringMember(rel, textures, "all", problems);
        }
    }

    /** {@code assets/<ns>/models/item/x.json} — measured: assets/minecraft/models/item/diamond_sword.json. */
    private static void checkItemModel(String rel, Object root, List<String> problems) {
        Map<String, Object> obj = object(rel, root, problems);
        if (obj == null) {
            return;
        }
        String parent = stringMember(rel, obj, "parent", problems);
        if (parent != null && !parent.equals("minecraft:item/generated")
                && !parent.equals("minecraft:item/handheld")) {
            problems.add(rel + ": parent must be minecraft:item/generated or minecraft:item/handheld,"
                    + " found \"" + parent + "\"");
        }
        Map<String, Object> textures = objectMember(rel, obj, "textures", problems);
        if (textures != null) {
            stringMember(rel, textures, "layer0", problems);
        }
    }

    /** {@code assets/<ns>/items/x.json} — measured: assets/minecraft/items/andesite.json. */
    private static void checkItemDef(String rel, Object root, List<String> problems) {
        Map<String, Object> obj = object(rel, root, problems);
        if (obj == null) {
            return;
        }
        Map<String, Object> model = objectMember(rel, obj, "model", problems);
        if (model == null) {
            return;
        }
        String type = stringMember(rel, model, "type", problems);
        if (type != null && !type.equals("minecraft:model")) {
            problems.add(rel + ": model.type must be \"minecraft:model\", found \"" + type + "\"");
        }
        stringMember(rel, model, "model", problems);
    }

    /** {@code assets/<ns>/lang/en_us.json} — measured: assets/minecraft/lang/en_us.json. */
    private static void checkLang(String rel, Object root, List<String> problems) {
        Map<String, Object> obj = object(rel, root, problems);
        if (obj == null) {
            return;
        }
        if (obj.isEmpty()) {
            problems.add(rel + ": lang file has no entries");
        }
        for (Map.Entry<String, Object> e : obj.entrySet()) {
            if (!(e.getValue() instanceof String)) {
                problems.add(rel + ": \"" + e.getKey() + "\" must map to a string, found "
                        + Json.typeName(e.getValue()));
            }
            boolean contentKey = e.getKey()
                    .matches("^(block|item)\\.[a-z0-9_.-]+\\.[a-z0-9_./-]+$");
            boolean advancementKey = e.getKey()
                    .matches("^advancements\\.[a-z0-9_.-]+\\.[a-z0-9_./-]+\\.(title|description)$");
            if (!contentKey && !advancementKey) {
                problems.add(rel + ": \"" + e.getKey() + "\" is not a block.<ns>.<path> or"
                        + " item.<ns>.<path> translation key");
            }
        }
    }

    /** {@code data/<ns>/loot_table/blocks/x.json} — measured: data/minecraft/loot_table/blocks/dirt.json. */
    private static void checkLoot(String rel, Object root, List<String> problems) {
        Map<String, Object> obj = object(rel, root, problems);
        if (obj == null) {
            return;
        }
        String type = stringMember(rel, obj, "type", problems);
        if (type != null && !type.equals("minecraft:block")) {
            problems.add(rel + ": type must be \"minecraft:block\" for a block loot table, found \""
                    + type + "\"");
        }
        List<Object> pools = arrayMember(rel, obj, "pools", problems);
        if (pools == null) {
            return;
        }
        if (pools.isEmpty()) {
            problems.add(rel + ": \"pools\" is empty");
        }
        for (int i = 0; i < pools.size(); i++) {
            String where = rel + ".pools[" + i + "]";
            Map<String, Object> pool = object(where, pools.get(i), problems);
            if (pool == null) {
                continue;
            }
            numberMember(where, pool, "rolls", problems);
            List<Object> entries = arrayMember(where, pool, "entries", problems);
            if (entries == null || entries.isEmpty()) {
                problems.add(where + ": \"entries\" must be a non-empty array");
                continue;
            }
            for (int j = 0; j < entries.size(); j++) {
                String entryWhere = where + ".entries[" + j + "]";
                Map<String, Object> entry = object(entryWhere, entries.get(j), problems);
                if (entry == null) {
                    continue;
                }
                String entryType = stringMember(entryWhere, entry, "type", problems);
                if (entryType != null && !entryType.equals("minecraft:item")) {
                    problems.add(entryWhere + ": type must be \"minecraft:item\", found \""
                            + entryType + "\"");
                }
                stringMember(entryWhere, entry, "name", problems);
            }
            if (pool.containsKey("condition")) {
                Map<String, Object> condition = object(where + ".condition", pool.get("condition"), problems);
                if (condition != null) {
                    stringMember(where + ".condition", condition, "type", problems);
                }
            }
            if (pool.containsKey("conditions")) {
                problems.add(where + ": 26.3 uses the singular pool key \"condition\", not"
                        + " \"conditions\" (measured on data/minecraft/loot_table/blocks/dirt.json)");
            }
        }
        if (obj.containsKey("random_sequence")) {
            stringMember(rel, obj, "random_sequence", problems);
        }
    }

    /** {@code data/<ns>/recipe/x.json} — measured: recipe/stone_bricks.json + recipe/andesite.json. */
    private static void checkRecipe(String rel, Object root, List<String> problems) {
        Map<String, Object> obj = object(rel, root, problems);
        if (obj == null) {
            return;
        }
        String type = stringMember(rel, obj, "type", problems);
        if (type == null) {
            return;
        }
        if (!type.equals("minecraft:crafting_shaped") && !type.equals("minecraft:crafting_shapeless")) {
            problems.add(rel + ": only minecraft:crafting_shaped / minecraft:crafting_shapeless are"
                    + " generated, found \"" + type + "\"");
            return;
        }
        if (obj.containsKey("category")) {
            String category = stringMember(rel, obj, "category", problems);
            if (category != null && !Manifest.CRAFTING_CATEGORIES.contains(category)) {
                problems.add(rel + ": category \"" + category + "\" is not one of "
                        + Manifest.CRAFTING_CATEGORIES);
            }
        }
        if (type.equals("minecraft:crafting_shaped")) {
            List<Object> pattern = arrayMember(rel, obj, "pattern", problems);
            if (pattern == null || pattern.isEmpty()) {
                problems.add(rel + ": a shaped recipe needs a non-empty \"pattern\"");
            } else {
                for (int i = 0; i < pattern.size(); i++) {
                    if (!(pattern.get(i) instanceof String row)) {
                        problems.add(rel + ".pattern[" + i + "]: expected a string");
                    } else if (row.isEmpty() || row.length() > 3) {
                        problems.add(rel + ".pattern[" + i + "]: row must be 1..3 characters, got '"
                                + row + "'");
                    }
                }
            }
            Map<String, Object> key = objectMember(rel, obj, "key", problems);
            if (key != null) {
                for (Map.Entry<String, Object> e : key.entrySet()) {
                    if (e.getKey().length() != 1) {
                        problems.add(rel + ".key: \"" + e.getKey() + "\" is not a single character");
                    }
                    if (!(e.getValue() instanceof String)) {
                        problems.add(rel + ".key: \"" + e.getKey() + "\" must map to an item id string");
                    }
                }
            }
        } else {
            List<Object> ingredients = arrayMember(rel, obj, "ingredients", problems);
            if (ingredients == null || ingredients.isEmpty()) {
                problems.add(rel + ": a shapeless recipe needs a non-empty \"ingredients\" array");
            } else {
                for (int i = 0; i < ingredients.size(); i++) {
                    if (!(ingredients.get(i) instanceof String)) {
                        problems.add(rel + ".ingredients[" + i + "]: expected an item id string");
                    }
                }
            }
        }
        Map<String, Object> result = objectMember(rel, obj, "result", problems);
        if (result != null) {
            stringMember(rel + ".result", result, "id", problems);
            if (result.containsKey("count")) {
                numberMember(rel + ".result", result, "count", problems);
            }
        }
    }

    /** {@code data/<ns>/tags/<kind>/x.json} — measured: data/minecraft/tags/item/stone_bricks.json. */
    private static void checkTag(String rel, Object root, List<String> problems) {
        Map<String, Object> obj = object(rel, root, problems);
        if (obj == null) {
            return;
        }
        List<Object> values = arrayMember(rel, obj, "values", problems);
        if (values == null) {
            return;
        }
        if (values.isEmpty()) {
            problems.add(rel + ": a tag with no values is refused by the loader; declare at least one");
        }
        for (int i = 0; i < values.size(); i++) {
            if (!(values.get(i) instanceof String value)) {
                problems.add(rel + ".values[" + i + "]: expected an id string (this generator does"
                        + " not emit {\"id\":…,\"required\":…} entries)");
                continue;
            }
            if (!value.matches("^#?[a-z0-9_.-]+:[a-z0-9_./-]+$")) {
                problems.add(rel + ".values[" + i + "]: \"" + value + "\" is not a namespaced id");
            }
        }
    }

    /**
     * {@code data/<ns>/advancement/x.json} — the 26.3 advancement shape: required criteria,
     * optional display/parent, requirements, and sends_telemetry_event.
     */
    private static void checkAdvancement(String rel, Object root, List<String> problems) {
        Map<String, Object> obj = object(rel, root, problems);
        if (obj == null) {
            return;
        }
        Map<String, Object> criteria = objectMember(rel, obj, "criteria", problems);
        if (criteria == null) {
            return;
        }
        if (criteria.isEmpty()) {
            problems.add(rel + ".criteria: must not be empty");
        }
        for (Map.Entry<String, Object> e : criteria.entrySet()) {
            String where = rel + ".criteria[\"" + e.getKey() + "\"]";
            Map<String, Object> criterion = object(where, e.getValue(), problems);
            if (criterion != null) {
                stringMember(where, criterion, "trigger", problems);
            }
        }

        if (obj.containsKey("requirements")) {
            List<Object> requirements = arrayMember(rel, obj, "requirements", problems);
            if (requirements != null) {
                for (int i = 0; i < requirements.size(); i++) {
                    String where = rel + ".requirements[" + i + "]";
                    if (!(requirements.get(i) instanceof List<?> group)) {
                        problems.add(where + ": expected an array of criterion ids, found "
                                + Json.typeName(requirements.get(i)));
                        continue;
                    }
                    for (int j = 0; j < group.size(); j++) {
                        Object id = group.get(j);
                        if (!(id instanceof String criterionId)) {
                            problems.add(where + "[" + j + "]: expected a criterion id string,"
                                    + " found " + Json.typeName(id));
                        } else if (!criteria.containsKey(criterionId)) {
                            problems.add(where + "[" + j + "]: \"" + criterionId
                                    + "\" is not declared in criteria");
                        }
                    }
                }
            }
        }

        if (obj.containsKey("display")) {
            Map<String, Object> display = object(rel + ".display", obj.get("display"), problems);
            if (display != null) {
                checkAdvancementDisplay(rel + ".display", display, problems);
            }
        }

        if (obj.containsKey("parent")) {
            String parent = stringMember(rel, obj, "parent", problems);
            if (parent != null && !NAMESPACED.matcher(parent).matches()) {
                problems.add(rel + ".parent: \"" + parent + "\" is not a namespaced id");
            }
        }

        if (obj.containsKey("sends_telemetry_event")
                && !(obj.get("sends_telemetry_event") instanceof Boolean)) {
            problems.add(rel + ".sends_telemetry_event: expected a boolean, found "
                    + Json.typeName(obj.get("sends_telemetry_event")));
        }
    }

    private static void checkAdvancementDisplay(String rel, Map<String, Object> display,
                                                List<String> problems) {
        checkDisplayText(rel, display, "title", problems);
        checkDisplayText(rel, display, "description", problems);

        Map<String, Object> icon = objectMember(rel, display, "icon", problems);
        if (icon != null) {
            stringMember(rel + ".icon", icon, "id", problems);
        }

        if (display.containsKey("frame")) {
            String frame = stringMember(rel, display, "frame", problems);
            if (frame != null && !frame.equals("task") && !frame.equals("goal")
                    && !frame.equals("challenge")) {
                problems.add(rel + ".frame: \"" + frame
                        + "\" is not one of task|goal|challenge");
            }
        }

        for (String key : new String[] {"announce_to_chat", "show_toast", "hidden"}) {
            if (display.containsKey(key) && !(display.get(key) instanceof Boolean)) {
                problems.add(rel + "." + key + ": expected a boolean, found "
                        + Json.typeName(display.get(key)));
            }
        }

        if (display.containsKey("background")) {
            stringMember(rel, display, "background", problems);
        }
    }

    private static void checkDisplayText(String rel, Map<String, Object> display, String key,
                                         List<String> problems) {
        String where = rel + "." + key;
        if (!display.containsKey(key)) {
            problems.add(rel + ": missing required key \"" + key + "\"");
            return;
        }
        Object value = display.get(key);
        if (value instanceof String text) {
            if (text.isBlank()) {
                problems.add(where + ": must not be blank");
            }
            return;
        }
        if (!(value instanceof Map<?, ?>)) {
            problems.add(where + ": expected an object with a \"translate\" string or a string,"
                    + " found " + Json.typeName(value));
            return;
        }
        Map<String, Object> text = object(where, value, problems);
        if (text != null) {
            stringMember(where, text, "translate", problems);
        }
    }

    /**
     * {@code data/<ns>/weaver/registry/...} — a custom data-driven registry entry. The payload is
     * defined by the module, so only the root shape is asserted here.
     */
    private static void checkCustomRegistry(String rel, Object root, List<String> problems) {
        object(rel, root, problems);
    }

    /** {@code data/<ns>/weaver/tags/...} — a Weaver custom tag with priority/replace support. */
    private static void checkCustomTag(String rel, Object root, List<String> problems) {
        Map<String, Object> obj = object(rel, root, problems);
        if (obj == null) {
            return;
        }
        List<Object> values = arrayMember(rel, obj, "values", problems);
        if (values == null) {
            return;
        }
        if (values.isEmpty()) {
            problems.add(rel + ": a tag with no values is refused by the loader; declare at least"
                    + " one");
        }
        for (int i = 0; i < values.size(); i++) {
            String where = rel + ".values[" + i + "]";
            Object value = values.get(i);
            if (value instanceof String id) {
                if (!NAMESPACED_OR_REF.matcher(id).matches()) {
                    problems.add(where + ": \"" + id + "\" is not a namespaced id or reference");
                }
            } else if (value instanceof Map<?, ?>) {
                Map<String, Object> entry = object(where, value, problems);
                if (entry == null) {
                    continue;
                }
                String id = stringMember(where, entry, "id", problems);
                if (id != null && !NAMESPACED.matcher(id).matches()) {
                    problems.add(where + ".id: \"" + id + "\" is not a namespaced id");
                }
                if (entry.containsKey("required")
                        && !(entry.get("required") instanceof Boolean)) {
                    problems.add(where + ".required: expected a boolean, found "
                            + Json.typeName(entry.get("required")));
                }
            } else {
                problems.add(where + ": expected a namespaced id string or an {id, required}"
                        + " object, found " + Json.typeName(value));
            }
        }
        if (obj.containsKey("priority")) {
            integerMember(rel, obj, "priority", problems);
        }
        if (obj.containsKey("replace") && !(obj.get("replace") instanceof Boolean)) {
            problems.add(rel + ".replace: expected a boolean, found "
                    + Json.typeName(obj.get("replace")));
        }
    }

    /** {@code pack.mcmeta} — measured: the pack.mcmeta the s15/s4 spike packs ship for 26.3. */
    private static void checkPackMeta(String rel, Object root, List<String> problems) {
        Map<String, Object> obj = object(rel, root, problems);
        if (obj == null) {
            return;
        }
        Map<String, Object> pack = objectMember(rel, obj, "pack", problems);
        if (pack == null) {
            return;
        }
        stringMember(rel + ".pack", pack, "description", problems);
        for (String key : new String[] {"min_format", "max_format"}) {
            List<Object> format = arrayMember(rel + ".pack", pack, key, problems);
            if (format == null) {
                continue;
            }
            if (format.size() != 2 || !(format.get(0) instanceof Number) || !(format.get(1) instanceof Number)) {
                problems.add(rel + ".pack." + key + ": must be [major, minor]");
            }
        }
        if (pack.containsKey("pack_format")) {
            problems.add(rel + ".pack: 26.3 uses min_format/max_format; \"pack_format\" alone is the"
                    + " pre-1.21.9 form");
        }
    }

    private static void checkPng(Path path, String rel, List<String> problems) {
        try {
            BufferedImage image = ImageIO.read(path.toFile());
            if (image == null) {
                problems.add(rel + ": not a readable PNG");
                return;
            }
            if (image.getWidth() != 16 || image.getHeight() != 16) {
                problems.add(rel + ": expected a 16x16 texture, got "
                        + image.getWidth() + "x" + image.getHeight());
            }
        } catch (IOException e) {
            problems.add(rel + ": unreadable image — " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- referential checks

    /**
     * Cross-file checks: the model an {@code items/} definition names must exist in this tree, and
     * the lang file must not translate ids the manifest never declared. Warnings, not failures,
     * for the reference case (a module may point at a model it ships by hand).
     */
    private static void checkReferences(List<String> files, Path outDir, List<String> problems,
                                        List<String> warnings) {
        Set<String> present = new LinkedHashSet<>(files);
        Map<String, String> langKeys = new TreeMap<>();
        for (String rel : files) {
            if (!rel.endsWith(".json")) {
                continue;
            }
            try {
                Object root = Json.parse(Files.readString(outDir.resolve(rel), StandardCharsets.UTF_8));
                String kind = kindOf(rel);
                if ("lang".equals(kind)) {
                    Map<String, Object> lang = Json.asObject(root, rel);
                    for (String key : lang.keySet()) {
                        langKeys.put(key, rel);
                    }
                }
                if ("item-def".equals(kind)) {
                    Map<String, Object> model = Json.asObject(
                            Json.asObject(root, rel).get("model"), rel + ".model");
                    Object target = model.get("model");
                    if (target instanceof String modelId) {
                        checkModelTarget(rel, modelId, present, problems, warnings);
                    }
                }
            } catch (IOException | RuntimeException ignored) {
                // per-file checks already reported the problem
            }
        }
        for (Map.Entry<String, String> e : langKeys.entrySet()) {
            String key = e.getKey();
            if (key.startsWith("advancements.")) {
                continue;
            }
            int dot = key.indexOf('.', key.indexOf('.') + 1);
            if (dot < 0) {
                continue;
            }
            String ns = key.substring(key.indexOf('.') + 1, dot);
            String path = key.substring(dot + 1);
            String kind = key.startsWith("block.") ? "block" : "item";
            String dir = kind.equals("block") ? "/blockstates/" : "/items/";
            boolean declared = present.contains("assets/" + ns + dir + path + ".json")
                    || present.contains("assets/" + ns + "/items/" + path + ".json");
            if (!declared) {
                warnings.add(e.getValue() + ": \"" + key + "\" translates \"" + ns + ":" + path
                        + "\" which this run did not generate");
            }
        }
    }

    private static void checkModelTarget(String rel, String modelId, Set<String> present,
                                         List<String> problems, List<String> warnings) {
        int colon = modelId.indexOf(':');
        if (colon <= 0) {
            problems.add(rel + ": model id \"" + modelId + "\" is not namespaced");
            return;
        }
        String ns = modelId.substring(0, colon);
        String path = modelId.substring(colon + 1);
        String target = "assets/" + ns + "/models/" + path + ".json";
        if (present.contains(target)) {
            return;
        }
        if (ns.equals("minecraft")) {
            warnings.add(rel + ": points at vanilla model \"" + modelId + "\" (not in this tree)");
        } else {
            problems.add(rel + ": model \"" + modelId + "\" has no " + target
                    + " in the generated tree");
        }
    }

    // ---------------------------------------------------------------- helpers

    static String kindOf(String rel) {
        if (rel.equals("pack.mcmeta") || rel.equals("pack.data.mcmeta")) {
            return "pack-meta";
        }
        if (rel.startsWith("assets/")) {
            String[] parts = rel.split("/");
            if (parts.length < 4) {
                return null;
            }
            return switch (parts[2]) {
                case "blockstates" -> "blockstate";
                case "items" -> "item-def";
                case "lang" -> "lang";
                case "models" -> parts.length > 3 && parts[3].equals("block") ? "block-model"
                        : parts.length > 3 && parts[3].equals("item") ? "item-model" : null;
                default -> null;
            };
        }
        if (rel.startsWith("data/")) {
            String[] parts = rel.split("/");
            if (parts.length < 4) {
                return null;
            }
            return switch (parts[2]) {
                case "loot_table" -> "loot";
                case "recipe" -> "recipe";
                case "tags" -> "tag";
                case "advancement" -> "advancement";
                case "weaver" -> {
                    if (parts.length < 7) {
                        yield null;
                    }
                    if (parts[3].equals("registry")) {
                        yield "custom-registry";
                    }
                    if (parts[3].equals("tags")) {
                        yield "custom-tag";
                    }
                    yield null;
                }
                default -> null;
            };
        }
        return null;
    }

    private static Map<String, Object> object(String rel, Object value, List<String> problems) {
        if (value instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> cast = (Map<String, Object>) map;
            return cast;
        }
        problems.add(rel + ": expected a JSON object, found " + Json.typeName(value));
        return null;
    }

    private static Map<String, Object> objectMember(String rel, Map<String, Object> parent,
                                                    String key, List<String> problems) {
        if (!parent.containsKey(key)) {
            problems.add(rel + ": missing required key \"" + key + "\"");
            return null;
        }
        return object(rel + "." + key, parent.get(key), problems);
    }

    private static List<Object> arrayMember(String rel, Map<String, Object> parent, String key,
                                            List<String> problems) {
        if (!parent.containsKey(key)) {
            problems.add(rel + ": missing required key \"" + key + "\"");
            return null;
        }
        Object value = parent.get(key);
        if (value instanceof List<?> list) {
            @SuppressWarnings("unchecked")
            List<Object> cast = (List<Object>) list;
            return cast;
        }
        problems.add(rel + "." + key + ": expected an array, found " + Json.typeName(value));
        return null;
    }

    private static String stringMember(String rel, Map<String, Object> parent, String key,
                                       List<String> problems) {
        if (!parent.containsKey(key)) {
            problems.add(rel + ": missing required key \"" + key + "\"");
            return null;
        }
        Object value = parent.get(key);
        if (value instanceof String s) {
            if (s.isBlank()) {
                problems.add(rel + "." + key + ": must not be blank");
                return null;
            }
            return s;
        }
        problems.add(rel + "." + key + ": expected a string, found " + Json.typeName(value));
        return null;
    }

    private static void numberMember(String rel, Map<String, Object> parent, String key,
                                     List<String> problems) {
        if (!parent.containsKey(key)) {
            problems.add(rel + ": missing required key \"" + key + "\"");
            return;
        }
        if (!(parent.get(key) instanceof Number)) {
            problems.add(rel + "." + key + ": expected a number, found "
                    + Json.typeName(parent.get(key)));
        }
    }

    private static void integerMember(String rel, Map<String, Object> parent, String key,
                                      List<String> problems) {
        Object value = parent.get(key);
        if (!(value instanceof Number n) || n instanceof Double || n instanceof Float) {
            problems.add(rel + "." + key + ": expected an integer, found "
                    + Json.typeName(value));
            return;
        }
        long v = n.longValue();
        if (v < Integer.MIN_VALUE || v > Integer.MAX_VALUE) {
            problems.add(rel + "." + key + ": " + v + " does not fit in a 32-bit integer");
        }
    }
}
