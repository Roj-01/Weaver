package weaver.datagen;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The compact manifest ({@code datagen.json}) and its validation.
 *
 * <p>Every rule here exists because a silently-wrong manifest produces JSON that the game
 * rejects at load time with a much worse error message than this file can give. Validation is
 * complete before a single byte is written: on any error nothing is generated.
 */
public final class Manifest {

    /** Namespaces the loader reserves: a module may not ship {@code assets/minecraft/**}. */
    public static final String RESERVED_NAMESPACE = "minecraft";

    /** Crafting book categories, measured from 26.3's {@code CraftingBookCategory} enum. */
    public static final Set<String> CRAFTING_CATEGORIES =
            Set.of("building", "redstone", "equipment", "misc");

    private static final Pattern NAMESPACE = Pattern.compile("^[a-z0-9_.-]+$");
    private static final Pattern PATH = Pattern.compile("^[a-z0-9_./-]+$");

    public record Block(String id, String name, String texture, String drops, List<String> tags) {
    }

    public record Item(String id, String name, String model, String texture, List<String> tags) {
    }

    public record Recipe(String id, String type, String category, List<String> pattern,
                         Map<String, String> key, List<String> ingredients, String result,
                         int count) {
        public boolean shaped() {
            return "shaped".equals(type);
        }
    }

    /**
     * One generated advancement.
     *
     * <p>{@code item} selects the item form: the criterion id is {@code has_item}, the trigger is
     * forced to {@code minecraft:inventory_changed}, and the criterion gains an item condition.
     * Without {@code item} the criterion id is the trigger path (for example {@code impossible}).
     *
     * <p>{@code title}, {@code description} and {@code icon} are either all present (the
     * advancement gets a display) or all absent (it is an invisible child advancement).
     */
    public record Advancement(String id, String parent, String title, String description,
                              String icon, String frame, String background, boolean showToast,
                              boolean announceToChat, boolean hidden, String trigger, String item) {
    }

    /**
     * One explicit tag declaration.
     *
     * <p>{@code values} are in declaration order and deduplicated. {@code priority} is written
     * as {@code priority} only for namespaced custom tag registries; vanilla registries such as
     * {@code block} and {@code item} must leave it at {@code 0}. {@code replace} is written only
     * when true.
     */
    public record TagDeclaration(String path, List<String> values, int priority, boolean replace) {
    }

    /**
     * One custom data-driven registry: a namespaced registry id and its entries in manifest order.
     */
    public record RegistryData(String id, List<RegistryEntryData> entries) {
    }

    /**
     * One entry of a custom registry: a resource path and its JSON object payload.
     */
    public record RegistryEntryData(String id, Map<String, Object> data) {
    }

    private final String namespace;
    private final String description;
    private final List<Block> blocks;
    private final List<Item> items;
    private final List<Recipe> recipes;
    private final List<Advancement> advancements;
    private final List<RegistryData> registries;
    private final Map<String, Map<String, TagDeclaration>> tags;

    private Manifest(String namespace, String description, List<Block> blocks, List<Item> items,
                     List<Recipe> recipes, List<Advancement> advancements,
                     List<RegistryData> registries,
                     Map<String, Map<String, TagDeclaration>> tags) {
        this.namespace = namespace;
        this.description = description;
        this.blocks = blocks;
        this.items = items;
        this.recipes = recipes;
        this.advancements = advancements;
        this.registries = registries;
        this.tags = tags;
    }

    public String namespace() {
        return namespace;
    }

    public String description() {
        return description;
    }

    public List<Block> blocks() {
        return blocks;
    }

    public List<Item> items() {
        return items;
    }

    public List<Recipe> recipes() {
        return recipes;
    }

    /** Advancements in manifest order. */
    public List<Advancement> advancements() {
        return advancements;
    }

    /** Custom data-driven registries in manifest order. */
    public List<RegistryData> registries() {
        return registries;
    }

    /**
     * Explicit tags: registry kind ({@code block}, {@code item}, another vanilla tag directory,
     * or a namespaced custom registry id) -> tag path -> declaration.
     */
    public Map<String, Map<String, TagDeclaration>> tags() {
        return tags;
    }

    /** A manifest that failed validation; carries every error, not just the first. */
    public static final class Invalid extends RuntimeException {
        private final List<String> errors;

        Invalid(List<String> errors) {
            super(errors.isEmpty() ? "invalid manifest" : errors.get(0));
            this.errors = List.copyOf(errors);
        }

        public List<String> errors() {
            return errors;
        }
    }

    public static Manifest load(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new Invalid(List.of("manifest not found: " + file));
        }
        String text = Files.readString(file, StandardCharsets.UTF_8);
        Object root;
        try {
            root = Json.parse(text);
        } catch (Json.ParseError e) {
            throw new Invalid(List.of("manifest is not valid JSON: " + e.getMessage()));
        }
        return parse(Json.asObject(root, "manifest"));
    }

    public static Manifest parse(Map<String, Object> root) {
        Errors errors = new Errors();

        String namespace = errors.string(root, "namespace", "manifest", true, "");
        if (namespace != null && !namespace.isBlank()) {
            if (!NAMESPACE.matcher(namespace).matches()) {
                errors.add("manifest.namespace: '" + namespace
                        + "' is not a valid namespace (lowercase a-z, 0-9, '_', '.', '-')");
            }
            if (RESERVED_NAMESPACE.equals(namespace)) {
                errors.add("manifest.namespace: 'minecraft' is reserved — a module may not ship"
                        + " assets/minecraft/** or data/minecraft/**");
            }
        }

        String description = errors.string(root, "description", "manifest", false,
                "Weaver datagen output");
        if (description != null && description.isBlank()) {
            description = "Weaver datagen output";
        }

        List<Block> blocks = new ArrayList<>();
        Set<String> blockIds = new LinkedHashSet<>();
        List<Object> rawBlocks = errors.array(root, "blocks", "manifest");
        for (int i = 0; i < rawBlocks.size(); i++) {
            String where = "blocks[" + i + "]";
            Map<String, Object> raw = errors.object(rawBlocks.get(i), where);
            String id = errors.path(raw, "id", where, true);
            String name = errors.string(raw, "name", where, true, null);
            if (id != null && !blockIds.add(id)) {
                errors.add(where + ".id: duplicate block id '" + id + "'");
            }
            String texture = errors.texture(raw, "texture", where);
            String drops = errors.drops(raw, "drops", where);
            List<String> tags = errors.paths(raw, "tags", where);
            if (id != null && name != null) {
                blocks.add(new Block(id, name, texture, drops, tags));
            }
        }

        List<Item> items = new ArrayList<>();
        Set<String> itemIds = new LinkedHashSet<>();
        List<Object> rawItems = errors.array(root, "items", "manifest");
        for (int i = 0; i < rawItems.size(); i++) {
            String where = "items[" + i + "]";
            Map<String, Object> raw = errors.object(rawItems.get(i), where);
            String id = errors.path(raw, "id", where, true);
            String name = errors.string(raw, "name", where, true, null);
            if (id != null && !itemIds.add(id)) {
                errors.add(where + ".id: duplicate item id '" + id + "'");
            }
            if (id != null && blockIds.contains(id)) {
                errors.add(where + ".id: item '" + id + "' already exists as a block — a block"
                        + " already gets an item model and an item translation key; declaring it"
                        + " twice would emit two assets/<ns>/items/" + id + ".json files");
            }
            String model = errors.choice(raw, "model", where, "generated", "handheld");
            String texture = errors.texture(raw, "texture", where);
            List<String> tags = errors.paths(raw, "tags", where);
            if (id != null && name != null) {
                items.add(new Item(id, name, model, texture, tags));
            }
        }

        List<Recipe> recipes = new ArrayList<>();
        Set<String> recipeIds = new LinkedHashSet<>();
        List<Object> rawRecipes = errors.array(root, "recipes", "manifest");
        for (int i = 0; i < rawRecipes.size(); i++) {
            Recipe recipe = parseRecipe(rawRecipes.get(i), "recipes[" + i + "]", recipeIds, errors);
            if (recipe != null) {
                recipes.add(recipe);
            }
        }

        List<Advancement> advancements = new ArrayList<>();
        Set<String> advancementIds = new LinkedHashSet<>();
        List<Object> rawAdvancements = errors.array(root, "advancements", "manifest");
        for (int i = 0; i < rawAdvancements.size(); i++) {
            Advancement advancement = parseAdvancement(rawAdvancements.get(i),
                    "advancements[" + i + "]", advancementIds, errors);
            if (advancement != null) {
                advancements.add(advancement);
            }
        }

        List<RegistryData> registries = new ArrayList<>();
        Set<String> registryIds = new LinkedHashSet<>();
        List<Object> rawRegistries = errors.array(root, "registries", "manifest");
        for (int i = 0; i < rawRegistries.size(); i++) {
            RegistryData registry = parseRegistry(rawRegistries.get(i), "registries[" + i + "]",
                    registryIds, errors);
            if (registry != null) {
                registries.add(registry);
            }
        }

        Map<String, Map<String, TagDeclaration>> tags = new LinkedHashMap<>();
        if (root.containsKey("tags")) {
            Map<String, Object> rawTags = errors.object(root.get("tags"), "tags");
            for (Map.Entry<String, Object> e : rawTags.entrySet()) {
                String kind = e.getKey();
                if (kind.isEmpty()) {
                    errors.add("tags.: a tag registry kind must not be empty");
                    continue;
                }
                if (!kind.contains(":")) {
                    if (!PATH.matcher(kind).matches() || kind.startsWith("/")
                            || kind.endsWith("/")) {
                        errors.add("tags." + kind + ": '" + kind + "' is not a valid vanilla tag"
                                + " directory (lowercase a-z, 0-9, '_', '.', '/', '-')");
                        continue;
                    }
                } else {
                    String registryId = errors.namespacedId(kind, "tags." + kind, true);
                    if (registryId == null) {
                        continue;
                    }
                    String registryPath = registryId.substring(registryId.indexOf(':') + 1);
                    if (registryPath.contains("/")) {
                        errors.add("tags." + kind + ": custom registry path '" + registryPath
                                + "' must be a single path segment (the runtime loader nests"
                                + " files under weaver/tags/<namespace>/<path>/)");
                        continue;
                    }
                }
                Map<String, Object> perKind = errors.object(e.getValue(), "tags." + kind);
                Map<String, TagDeclaration> out = new LinkedHashMap<>();
                for (Map.Entry<String, Object> t : perKind.entrySet()) {
                    String where = "tags." + kind + "." + t.getKey();
                    String path = t.getKey();
                    if (!PATH.matcher(path).matches() || path.startsWith("/") || path.endsWith("/")) {
                        errors.add(where + ": '" + path + "' is not a valid tag path");
                        continue;
                    }
                    TagDeclaration declaration = parseTagDeclaration(path, t.getValue(), where,
                            errors);
                    if (declaration == null) {
                        continue;
                    }
                    if (!kind.contains(":") && declaration.priority() != 0) {
                        errors.add(where + ".priority: vanilla tag directories such as '" + kind
                                + "' do not support priority; use a namespaced custom registry"
                                + " id (for example '<mod>:<registry>')");
                        continue;
                    }
                    out.put(path, declaration);
                }
                tags.put(kind, Collections.unmodifiableMap(out));
            }
        }

        if (errors.list.isEmpty() && blocks.isEmpty() && items.isEmpty() && recipes.isEmpty()
                && advancements.isEmpty() && registries.isEmpty() && tags.isEmpty()) {
            errors.add("manifest: nothing to generate — declare at least one block, item, recipe,"
                    + " advancement, registry or tag");
        }

        if (!errors.list.isEmpty()) {
            throw new Invalid(errors.list);
        }
        return new Manifest(namespace, description, List.copyOf(blocks), List.copyOf(items),
                List.copyOf(recipes), List.copyOf(advancements), List.copyOf(registries),
                Collections.unmodifiableMap(new LinkedHashMap<>(tags)));
    }

    private static Advancement parseAdvancement(Object rawValue, String where, Set<String> seen,
                                                Errors errors) {
        Map<String, Object> raw = errors.object(rawValue, where);
        String id = errors.path(raw, "id", where, true);
        if (id != null && !seen.add(id)) {
            errors.add(where + ".id: duplicate advancement id '" + id + "'");
        }
        String parent = errors.namespacedId(raw, "parent", where, false, null);

        boolean hasTitle = raw.containsKey("title");
        boolean hasDescription = raw.containsKey("description");
        boolean hasIcon = raw.containsKey("icon");
        int displayParts = (hasTitle ? 1 : 0) + (hasDescription ? 1 : 0) + (hasIcon ? 1 : 0);
        if (displayParts != 0 && displayParts != 3) {
            errors.add(where + ": title, description and icon must be declared together");
        }
        String title = displayParts == 3 ? errors.string(raw, "title", where, true, null) : null;
        String description = displayParts == 3
                ? errors.string(raw, "description", where, true, null) : null;
        String icon = displayParts == 3
                ? errors.namespacedId(raw, "icon", where, true, null) : null;

        String frame = errors.choice(raw, "frame", where, "task", "goal", "challenge");
        String background = errors.namespacedId(raw, "background", where, false, null);
        boolean showToast = errors.bool(raw, "show_toast", where, true);
        boolean announceToChat = errors.bool(raw, "announce_to_chat", where, true);
        boolean hidden = errors.bool(raw, "hidden", where, false);

        boolean triggerDeclared = raw.containsKey("trigger");
        String trigger = errors.namespacedId(raw, "trigger", where, false,
                "minecraft:impossible");
        String item = errors.namespacedId(raw, "item", where, false, null);
        if (item != null && triggerDeclared && trigger != null
                && !trigger.equals("minecraft:inventory_changed")) {
            errors.add(where + ".item: an item criterion forces trigger"
                    + " 'minecraft:inventory_changed', but the manifest declares '" + trigger + "'");
        }
        if (item != null && !triggerDeclared) {
            trigger = "minecraft:inventory_changed";
        }

        if (id == null || trigger == null) {
            return null;
        }
        return new Advancement(id, parent, title, description, icon, frame, background, showToast,
                announceToChat, hidden, trigger, item);
    }

    private static RegistryData parseRegistry(Object rawValue, String where, Set<String> seen,
                                              Errors errors) {
        Map<String, Object> raw = errors.object(rawValue, where);
        String id = errors.namespacedId(raw, "id", where, true, null);
        if (id != null && !seen.add(id)) {
            errors.add(where + ".id: duplicate registry id '" + id + "'");
        }
        if (id != null) {
            String registryPath = id.substring(id.indexOf(':') + 1);
            if (registryPath.contains("/")) {
                errors.add(where + ".id: custom registry path '" + registryPath
                        + "' must be a single path segment (the runtime loader nests files under"
                        + " weaver/registry/<namespace>/<path>/)");
                id = null;
            }
        }

        List<RegistryEntryData> entries = new ArrayList<>();
        Set<String> entryIds = new LinkedHashSet<>();
        List<Object> rawEntries = errors.array(raw, "entries", where);
        for (int i = 0; i < rawEntries.size(); i++) {
            String entryWhere = where + ".entries[" + i + "]";
            Map<String, Object> rawEntry = errors.object(rawEntries.get(i), entryWhere);
            String entryId = errors.path(rawEntry, "id", entryWhere, true);
            if (entryId != null && !entryIds.add(entryId)) {
                errors.add(entryWhere + ".id: duplicate entry id '" + entryId + "'");
            }
            Map<String, Object> data = new LinkedHashMap<>();
            if (rawEntry.containsKey("data")) {
                Object rawData = rawEntry.get("data");
                if (rawData instanceof Map<?, ?>) {
                    data = errors.object(rawData, entryWhere + ".data");
                } else {
                    errors.add(entryWhere + ".data: expected an object, found "
                            + Json.typeName(rawData));
                }
            }
            if (entryId != null) {
                entries.add(new RegistryEntryData(entryId, orderedObject(data)));
            }
        }

        if (id == null) {
            return null;
        }
        return new RegistryData(id, List.copyOf(entries));
    }

    private static TagDeclaration parseTagDeclaration(String path, Object rawValue, String where,
                                                      Errors errors) {
        List<String> ids;
        int priority = 0;
        boolean replace = false;
        if (rawValue instanceof Map<?, ?>) {
            Map<String, Object> raw = errors.object(rawValue, where);
            if (!raw.containsKey("values")) {
                errors.add(where + ".values: a tag object needs a \"values\" array");
            }
            List<Object> values = errors.array(raw, "values", where);
            ids = parseTagValues(values, where + ".values", errors);
            if (raw.containsKey("priority")) {
                priority = errors.anyInteger(raw, "priority", where);
            }
            if (raw.containsKey("replace")) {
                replace = errors.bool(raw, "replace", where, false);
            }
        } else {
            List<Object> values = errors.array(rawValue, where);
            ids = parseTagValues(values, where, errors);
        }
        return new TagDeclaration(path, List.copyOf(ids), priority, replace);
    }

    private static List<String> parseTagValues(List<Object> values, String where, Errors errors) {
        Set<String> ids = new LinkedHashSet<>();
        for (int i = 0; i < values.size(); i++) {
            String id = errors.resourceId(
                    errors.stringValue(values.get(i), where + "[" + i + "]"),
                    where + "[" + i + "]", true);
            if (id != null) {
                ids.add(id);
            }
        }
        return List.copyOf(ids);
    }

    /** A JSON object with insertion order preserved at every nesting level, made immutable. */
    private static Map<String, Object> orderedObject(Map<String, Object> raw) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            copy.put(e.getKey(), orderedValue(e.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Object orderedValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                copy.put(String.valueOf(e.getKey()), orderedValue(e.getValue()));
            }
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object item : list) {
                copy.add(orderedValue(item));
            }
            return Collections.unmodifiableList(copy);
        }
        return value;
    }

    private static Recipe parseRecipe(Object rawValue, String where, Set<String> seen, Errors errors) {
        Map<String, Object> raw = errors.object(rawValue, where);
        String id = errors.path(raw, "id", where, true);
        if (id != null && !seen.add(id)) {
            errors.add(where + ".id: duplicate recipe id '" + id + "'");
        }
        String type = errors.choice(raw, "type", where, "shaped", "shapeless");
        String category = raw.containsKey("category")
                ? errors.string(raw, "category", where, true, null)
                : null;
        if (category != null && !CRAFTING_CATEGORIES.contains(category)) {
            errors.add(where + ".category: '" + category + "' is not a crafting book category;"
                    + " 26.3's CraftingBookCategory enum serialises building|redstone|equipment|misc"
                    + " (verified against net/minecraft/world/item/crafting/CraftingBookCategory)");
        }
        String result = errors.resourceId(
                errors.string(raw, "result", where, true, null), where + ".result", true);
        int count = 1;
        if (raw.containsKey("count")) {
            count = errors.integer(raw, "count", where, 1, 64);
        }

        List<String> pattern = new ArrayList<>();
        Map<String, String> key = new LinkedHashMap<>();
        List<String> ingredients = new ArrayList<>();

        if ("shaped".equals(type)) {
            List<Object> rawPattern = errors.array(raw, "pattern", where);
            for (int r = 0; r < rawPattern.size(); r++) {
                String row = errors.stringValue(rawPattern.get(r), where + ".pattern[" + r + "]");
                if (row == null) {
                    continue;
                }
                if (row.isEmpty() || row.length() > 3) {
                    errors.add(where + ".pattern[" + r + "]: each row must be 1..3 characters, got '"
                            + row + "'");
                }
                pattern.add(row);
            }
            if (pattern.isEmpty()) {
                errors.add(where + ".pattern: a shaped recipe needs 1..3 rows");
            } else {
                int width = pattern.get(0).length();
                for (int r = 1; r < pattern.size(); r++) {
                    if (pattern.get(r).length() != width) {
                        errors.add(where + ".pattern: row " + r + " is " + pattern.get(r).length()
                                + " wide but row 0 is " + width + " — a shaped pattern must be a"
                                + " rectangle");
                    }
                }
            }

            Map<String, Object> rawKey = raw.containsKey("key")
                    ? errors.object(raw.get("key"), where + ".key") : Map.of();
            if (!raw.containsKey("key")) {
                errors.add(where + ".key: a shaped recipe needs a key object");
            } else if (rawKey.isEmpty()) {
                errors.add(where + ".key: a shaped recipe needs a key object");
            }
            for (Map.Entry<String, Object> e : rawKey.entrySet()) {
                String symbol = e.getKey();
                if (symbol.length() != 1) {
                    errors.add(where + ".key.\"" + symbol + "\": a key must be exactly one character");
                    continue;
                }
                String ingredient = errors.resourceId(
                        errors.stringValue(e.getValue(), where + ".key.\"" + symbol + "\""),
                        where + ".key.\"" + symbol + "\"", true);
                if (ingredient != null) {
                    key.put(symbol, ingredient);
                }
            }

            // Symbols and pattern are checked once both are known (the key may be written
            // before or after the pattern in the manifest).
            java.util.Set<Character> missing = new java.util.LinkedHashSet<>();
            for (String row : pattern) {
                for (int c = 0; c < row.length(); c++) {
                    char symbol = row.charAt(c);
                    if (symbol != ' ' && !key.containsKey(String.valueOf(symbol))) {
                        missing.add(symbol);
                    }
                }
            }
            for (char symbol : missing) {
                errors.add(where + ".key: the pattern uses '" + symbol + "' but no key defines it");
            }
            for (String symbol : key.keySet()) {
                boolean used = false;
                for (String row : pattern) {
                    if (row.indexOf(symbol.charAt(0)) >= 0) {
                        used = true;
                        break;
                    }
                }
                if (!used && !pattern.isEmpty()) {
                    errors.add(where + ".key.\"" + symbol + "\": declared but never used in the"
                            + " pattern");
                }
            }
        } else if ("shapeless".equals(type)) {
            List<Object> rawIngredients = errors.array(raw, "ingredients", where);
            if (rawIngredients.isEmpty() || rawIngredients.size() > 9) {
                errors.add(where + ".ingredients: a shapeless recipe needs 1..9 ingredients, got "
                        + rawIngredients.size());
            }
            for (int i = 0; i < rawIngredients.size(); i++) {
                String ingredient = errors.resourceId(
                        errors.stringValue(rawIngredients.get(i), where + ".ingredients[" + i + "]"),
                        where + ".ingredients[" + i + "]", true);
                if (ingredient != null) {
                    ingredients.add(ingredient);
                }
            }
            if (raw.containsKey("pattern") || raw.containsKey("key")) {
                errors.add(where + ": a shapeless recipe must not declare pattern/key");
            }
        }

        if (id == null || type == null || result == null) {
            return null;
        }
        if ("shaped".equals(type) && (pattern.isEmpty() || key.isEmpty())) {
            return null;
        }
        if ("shapeless".equals(type) && ingredients.isEmpty()) {
            return null;
        }
        return new Recipe(id, type, category, List.copyOf(pattern),
                Collections.unmodifiableMap(new LinkedHashMap<>(key)), List.copyOf(ingredients),
                result, count);
    }

    /** Collects every error so the developer sees the whole list in one run. */
    private static final class Errors {
        private final List<String> list = new ArrayList<>();

        void add(String message) {
            list.add(message);
        }

        Map<String, Object> object(Object value, String where) {
            if (value instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cast = (Map<String, Object>) map;
                return cast;
            }
            add(where + ": expected an object, found " + Json.typeName(value));
            return Map.of();
        }

        List<Object> array(Map<String, Object> parent, String key, String where) {
            if (!parent.containsKey(key)) {
                return List.of();
            }
            Object value = parent.get(key);
            if (value instanceof List<?> l) {
                @SuppressWarnings("unchecked")
                List<Object> cast = (List<Object>) l;
                return cast;
            }
            add(where + "." + key + ": expected an array, found " + Json.typeName(value));
            return List.of();
        }

        List<Object> array(Object value, String where) {
            if (value instanceof List<?> l) {
                @SuppressWarnings("unchecked")
                List<Object> cast = (List<Object>) l;
                return cast;
            }
            add(where + ": expected an array, found " + Json.typeName(value));
            return List.of();
        }

        String stringValue(Object value, String where) {
            if (value instanceof String s) {
                return s;
            }
            add(where + ": expected a string, found " + Json.typeName(value));
            return null;
        }

        String string(Map<String, Object> parent, String key, String where, boolean required,
                      String fallback) {
            if (!parent.containsKey(key) || parent.get(key) == null) {
                if (required) {
                    add(where + "." + key + ": required string is missing");
                }
                return fallback;
            }
            String s = stringValue(parent.get(key), where + "." + key);
            if (s != null && s.isBlank() && required) {
                add(where + "." + key + ": must not be blank");
                return fallback;
            }
            return s;
        }

        String choice(Map<String, Object> parent, String key, String where, String... allowed) {
            String value = string(parent, key, where, false, allowed[0]);
            if (value == null) {
                return allowed[0];
            }
            for (String candidate : allowed) {
                if (candidate.equals(value)) {
                    return value;
                }
            }
            add(where + "." + key + ": '" + value + "' is not one of " + String.join("|", allowed));
            return allowed[0];
        }

        String path(Map<String, Object> parent, String key, String where, boolean required) {
            String value = string(parent, key, where, required, null);
            if (value == null) {
                return null;
            }
            if (!PATH.matcher(value).matches() || value.startsWith("/") || value.endsWith("/")) {
                add(where + "." + key + ": '" + value + "' is not a valid resource path"
                        + " (lowercase a-z, 0-9, '_', '.', '/', '-')");
                return null;
            }
            return value;
        }

        /** An optional {@code ns:path} member of {@code parent}; a fallback is validated too. */
        String namespacedId(Map<String, Object> parent, String key, String where,
                            boolean required, String fallback) {
            String value = string(parent, key, where, required, fallback);
            if (value == null) {
                return null;
            }
            return namespacedId(value, where + "." + key, required);
        }

        /** {@code ns:path} without a leading {@code #}; null when invalid. */
        String namespacedId(String value, String where, boolean required) {
            if (value == null) {
                return null;
            }
            if (value.startsWith("#")) {
                add(where + ": '" + value + "' must be a namespaced id, not a tag reference");
                return null;
            }
            return resourceId(value, where, required);
        }

        /** {@code ns:path}, or {@code #ns:path} for a tag reference; null when invalid. */
        String resourceId(String value, String where, boolean required) {
            if (value == null) {
                return null;
            }
            String body = value.startsWith("#") ? value.substring(1) : value;
            if (body.isEmpty()) {
                add(where + ": empty resource id");
                return null;
            }
            int colon = body.indexOf(':');
            if (colon <= 0 || colon == body.length() - 1) {
                add(where + ": '" + value + "' must be a namespaced id like 'mymod:rune'"
                        + (value.startsWith("#") ? " (or '#mymod:tag')" : ""));
                return null;
            }
            String ns = body.substring(0, colon);
            String path = body.substring(colon + 1);
            if (!NAMESPACE.matcher(ns).matches()) {
                add(where + ": '" + value + "' has an invalid namespace '" + ns + "'");
                return null;
            }
            if (!PATH.matcher(path).matches()) {
                add(where + ": '" + value + "' has an invalid path '" + path + "'");
                return null;
            }
            return value;
        }

        String texture(Map<String, Object> parent, String key, String where) {
            String value = string(parent, key, where, false, null);
            if (value == null) {
                return null;
            }
            return resourceId(value, where + "." + key, false);
        }

        List<String> paths(Map<String, Object> parent, String key, String where) {
            if (!parent.containsKey(key)) {
                return List.of();
            }
            List<Object> raw = array(parent.get(key), where + "." + key);
            List<String> out = new ArrayList<>();
            for (int i = 0; i < raw.size(); i++) {
                String p = pathRaw(raw.get(i), where + "." + key + "[" + i + "]");
                if (p != null) {
                    out.add(p);
                }
            }
            return List.copyOf(out);
        }

        private String pathRaw(Object value, String where) {
            String s = stringValue(value, where);
            if (s == null) {
                return null;
            }
            if (!PATH.matcher(s).matches() || s.startsWith("/") || s.endsWith("/")) {
                add(where + ": '" + s + "' is not a valid resource path");
                return null;
            }
            return s;
        }

        String drops(Map<String, Object> parent, String key, String where) {
            String value = string(parent, key, where, false, "self");
            if (value == null) {
                return "self";
            }
            if (value.equals("self") || value.equals("none")) {
                return value;
            }
            return resourceId(value, where + "." + key, false);
        }

        boolean bool(Map<String, Object> parent, String key, String where, boolean fallback) {
            if (!parent.containsKey(key)) {
                return fallback;
            }
            Object value = parent.get(key);
            if (value instanceof Boolean b) {
                return b;
            }
            add(where + "." + key + ": expected a boolean, found " + Json.typeName(value));
            return fallback;
        }

        /** Any 32-bit integer — used where the value is not constrained to a range. */
        int anyInteger(Map<String, Object> parent, String key, String where) {
            Object value = parent.get(key);
            if (!(value instanceof Number) || value instanceof Double || value instanceof Float) {
                add(where + "." + key + ": expected an integer, found " + Json.typeName(value));
                return 0;
            }
            long v = ((Number) value).longValue();
            if (v < Integer.MIN_VALUE || v > Integer.MAX_VALUE) {
                add(where + "." + key + ": " + v + " does not fit in a 32-bit integer");
                return 0;
            }
            return (int) v;
        }

        int integer(Map<String, Object> parent, String key, String where, int min, int max) {
            Object value = parent.get(key);
            if (!(value instanceof Number n)) {
                add(where + "." + key + ": expected a number, found " + Json.typeName(value));
                return min;
            }
            long v = n.longValue();
            if (v < min || v > max) {
                add(where + "." + key + ": " + v + " is outside " + min + ".." + max);
                return min;
            }
            return (int) v;
        }
    }
}
