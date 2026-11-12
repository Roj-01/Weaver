package weaver.datagen;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * Turns a validated {@link Manifest} into the JSON tree a Weaver module ships.
 *
 * <p>Every key this class writes was measured against a real 26.3 file inside
 * {@code work/game-26.3/client.jar} — the file each shape came from is named in the javadoc of
 * the method that writes it. Shapes are not guessed.
 */
public final class Generator {

    /** Resource pack format of 26.3 (version.json: pack_version.resource_major/minor). */
    public static final int[] RESOURCE_FORMAT = {97, 1};

    /** Data pack format of 26.3 (version.json: pack_version.data_major/minor). */
    public static final int[] DATA_FORMAT = {121, 0};

    private Generator() {
    }

    /** Relative path -> bytes, in generation order. Text files are UTF-8. */
    public static Map<String, byte[]> generate(Manifest manifest) {
        Map<String, byte[]> out = new LinkedHashMap<>();
        String ns = manifest.namespace();

        Map<String, String> lang = new TreeMap<>();
        Map<String, Set<String>> blockTags = new LinkedHashMap<>();
        Map<String, Set<String>> itemTags = new LinkedHashMap<>();

        for (Manifest.Block block : manifest.blocks()) {
            String texture = block.texture() != null ? block.texture() : ns + ":block/" + block.id();

            // assets/<ns>/blockstates/<id>.json — same shape as
            // assets/minecraft/blockstates/stone_bricks.json in client.jar (a single
            // unparameterised "variants" entry keyed by the empty string).
            out.put(assets(ns, "blockstates/" + block.id() + ".json"), json(Json.obj(
                    "variants", Json.obj(
                            "", Json.obj("model", ns + ":block/" + block.id())))));

            // assets/<ns>/models/block/<id>.json — same shape as
            // assets/minecraft/models/block/stone_bricks.json (parent cube_all + one "all").
            out.put(assets(ns, "models/block/" + block.id() + ".json"), json(Json.obj(
                    "parent", "minecraft:block/cube_all",
                    "textures", Json.obj("all", texture))));

            // assets/<ns>/items/<id>.json — the 26.3 item model definition; same shape as
            // assets/minecraft/items/andesite.json. A block item points straight at the block
            // model, exactly as vanilla does.
            out.put(assets(ns, "items/" + block.id() + ".json"), json(Json.obj(
                    "model", Json.obj(
                            "type", "minecraft:model",
                            "model", ns + ":block/" + block.id()))));

            // assets/minecraft/lang/en_us.json carries "block.minecraft.andesite"; a block's item
            // inherits its name from the block, so no "item." key is written for a block item.
            lang.put("block." + ns + "." + block.id(), block.name());

            if (!"none".equals(block.drops())) {
                String drop = "self".equals(block.drops()) ? ns + ":" + block.id() : block.drops();
                // data/<ns>/loot_table/blocks/<id>.json — same shape as
                // data/minecraft/loot_table/blocks/dirt.json: pool-level "condition" (singular,
                // 26.3), "entries", "rolls", and the block loot table id "blocks/<path>".
                out.put(data(ns, "loot_table/blocks/" + block.id() + ".json"), json(Json.obj(
                        "type", "minecraft:block",
                        "pools", Json.arr(Json.obj(
                                "condition", Json.obj("type", "minecraft:survives_explosion"),
                                "entries", Json.arr(Json.obj(
                                        "type", "minecraft:item",
                                        "name", drop)),
                                "rolls", 1)),
                        "random_sequence", ns + ":blocks/" + block.id())));
            }

            if (block.texture() == null) {
                out.put(assets(ns, "textures/block/" + block.id() + ".png"),
                        placeholderTexture(block.id()));
            }

            for (String tag : block.tags()) {
                blockTags.computeIfAbsent(tag, k -> new LinkedHashSet<>()).add(ns + ":" + block.id());
            }
        }

        for (Manifest.Item item : manifest.items()) {
            String texture = item.texture() != null ? item.texture() : ns + ":item/" + item.id();
            String parent = "handheld".equals(item.model())
                    ? "minecraft:item/handheld" : "minecraft:item/generated";

            // assets/<ns>/models/item/<id>.json — same shape as
            // assets/minecraft/models/item/diamond_sword.json (parent + layer0).
            out.put(assets(ns, "models/item/" + item.id() + ".json"), json(Json.obj(
                    "parent", parent,
                    "textures", Json.obj("layer0", texture))));

            // assets/<ns>/items/<id>.json — same shape as assets/minecraft/items/diamond_sword.json.
            out.put(assets(ns, "items/" + item.id() + ".json"), json(Json.obj(
                    "model", Json.obj(
                            "type", "minecraft:model",
                            "model", ns + ":item/" + item.id()))));

            lang.put("item." + ns + "." + item.id(), item.name());

            if (item.texture() == null) {
                out.put(assets(ns, "textures/item/" + item.id() + ".png"),
                        placeholderTexture(item.id()));
            }

            for (String tag : item.tags()) {
                itemTags.computeIfAbsent(tag, k -> new LinkedHashSet<>()).add(ns + ":" + item.id());
            }
        }

        for (Manifest.Recipe recipe : manifest.recipes()) {
            Map<String, Object> body = new LinkedHashMap<>();
            if (recipe.shaped()) {
                // Same shape as data/minecraft/recipe/stone_bricks.json:
                // type, category, key, pattern, result{count,id}. "count" is omitted when 1,
                // exactly as vanilla omits it (data/minecraft/recipe/furnace.json).
                body.put("type", "minecraft:crafting_shaped");
                if (recipe.category() != null) {
                    body.put("category", recipe.category());
                }
                Map<String, Object> key = new LinkedHashMap<>(recipe.key());
                body.put("key", key);
                body.put("pattern", new ArrayList<>(recipe.pattern()));
            } else {
                // Same shape as data/minecraft/recipe/andesite.json:
                // type, category, ingredients, result{count,id}.
                body.put("type", "minecraft:crafting_shapeless");
                if (recipe.category() != null) {
                    body.put("category", recipe.category());
                }
                body.put("ingredients", new ArrayList<>(recipe.ingredients()));
            }
            Map<String, Object> result = new LinkedHashMap<>();
            if (recipe.count() != 1) {
                result.put("count", recipe.count());
            }
            result.put("id", recipe.result());
            body.put("result", result);

            out.put(data(ns, "recipe/" + recipe.id() + ".json"), json(body));
        }

        for (Manifest.Advancement advancement : manifest.advancements()) {
            emitAdvancement(out, ns, advancement, lang);
        }

        // Explicit tags merge into the per-block/per-item ones (values deduplicated, order kept).
        emitTags(out, ns, "block", blockTags, manifest.tags().get("block"));
        emitTags(out, ns, "item", itemTags, manifest.tags().get("item"));
        for (Map.Entry<String, Map<String, Manifest.TagDeclaration>> kind
                : manifest.tags().entrySet()) {
            String registry = kind.getKey();
            if (registry.equals("block") || registry.equals("item")) {
                continue;
            }
            if (registry.contains(":")) {
                emitCustomTags(out, ns, registry, kind.getValue());
            } else {
                emitTags(out, ns, registry, new LinkedHashMap<>(), kind.getValue());
            }
        }

        for (Manifest.RegistryData registry : manifest.registries()) {
            int colon = registry.id().indexOf(':');
            String registryNs = registry.id().substring(0, colon);
            String registryPath = registry.id().substring(colon + 1);
            for (Manifest.RegistryEntryData entry : registry.entries()) {
                // data/<ns>/weaver/registry/<registryNs>/<registryPath>/<entry>.json — the
                // loader walks this tree for custom data-driven registries.
                out.put(data(ns, "weaver/registry/" + registryNs + "/" + registryPath + "/"
                        + entry.id() + ".json"), json(entry.data()));
            }
        }

        // assets/<ns>/lang/en_us.json — only the module's own keys, alphabetically, the layout
        // vanilla's en_us.json uses for the same keys ("block.minecraft.andesite").
        if (!lang.isEmpty()) {
            out.put(assets(ns, "lang/en_us.json"), json(new LinkedHashMap<>(lang)));
        }

        // pack.mcmeta resources — same shape as the pack.mcmeta the s15/s4 spike packs ship
        // (min_format/max_format = [97, 1], measured for 26.3's resource_major/resource_minor).
        out.put("pack.mcmeta", json(Json.obj(
                "pack", Json.obj(
                        "description", manifest.description(),
                        "min_format", Json.arr(RESOURCE_FORMAT[0], RESOURCE_FORMAT[1]),
                        "max_format", Json.arr(RESOURCE_FORMAT[0], RESOURCE_FORMAT[1])))));

        // pack.data.mcmeta — the data-pack twin ([121, 0] = data_major/data_minor in version.json).
        // A jar has one root pack.mcmeta and the loader reads it for both pack types, so a
        // data-only jar ships this file renamed to pack.mcmeta (see docs/team/datagen.md).
        out.put("pack.data.mcmeta", json(Json.obj(
                "pack", Json.obj(
                        "description", manifest.description() + " (data)",
                        "min_format", Json.arr(DATA_FORMAT[0], DATA_FORMAT[1]),
                        "max_format", Json.arr(DATA_FORMAT[0], DATA_FORMAT[1])))));

        return out;
    }

    /**
     * Writes one advancement: {@code criteria}, optional {@code display}, optional {@code parent},
     * {@code requirements} and {@code sends_telemetry_event}, in that order.
     *
     * <p>The criterion id is {@code has_item} for the item form and otherwise the trigger path,
     * so the default {@code minecraft:impossible} trigger produces an {@code impossible}
     * criterion. Display translation keys are added to the shared lang map.
     */
    private static void emitAdvancement(Map<String, byte[]> out, String ns,
                                        Manifest.Advancement advancement, Map<String, String> lang) {
        boolean hasDisplay = advancement.title() != null && advancement.description() != null
                && advancement.icon() != null;
        String criterionId = advancement.item() != null
                ? "has_item" : triggerPath(advancement.trigger());

        Map<String, Object> criterion = Json.obj("trigger", advancement.trigger());
        if (advancement.item() != null) {
            criterion.put("conditions", Json.obj(
                    "items", Json.arr(Json.obj("items", advancement.item()))));
        }

        Map<String, Object> body = Json.obj(
                "criteria", Json.obj(criterionId, criterion));
        if (hasDisplay) {
            String titleKey = "advancements." + ns + "." + advancement.id() + ".title";
            String descriptionKey = "advancements." + ns + "." + advancement.id()
                    + ".description";
            Map<String, Object> display = Json.obj();
            display.put("announce_to_chat", advancement.announceToChat());
            if (advancement.background() != null) {
                display.put("background", advancement.background());
            }
            display.put("description", Json.obj("translate", descriptionKey));
            display.put("frame", advancement.frame());
            display.put("hidden", advancement.hidden());
            display.put("icon", Json.obj("id", advancement.icon()));
            display.put("show_toast", advancement.showToast());
            display.put("title", Json.obj("translate", titleKey));
            body.put("display", display);
            lang.put(titleKey, advancement.title());
            lang.put(descriptionKey, advancement.description());
        }
        if (advancement.parent() != null) {
            body.put("parent", advancement.parent());
        }
        body.put("requirements", Json.arr(Json.arr(criterionId)));
        body.put("sends_telemetry_event", false);

        // data/<ns>/advancement/<id>.json — singular "advancement" in 26.3.
        out.put(data(ns, "advancement/" + advancement.id() + ".json"), json(body));
    }

    private static String triggerPath(String trigger) {
        int colon = trigger.indexOf(':');
        return colon < 0 ? trigger : trigger.substring(colon + 1);
    }

    private static void emitTags(Map<String, byte[]> out, String ns, String kind,
                                 Map<String, Set<String>> fromContent,
                                 Map<String, Manifest.TagDeclaration> explicit) {
        Map<String, Set<String>> merged = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> e : fromContent.entrySet()) {
            merged.computeIfAbsent(e.getKey(), k -> new LinkedHashSet<>()).addAll(e.getValue());
        }
        Set<String> replace = new LinkedHashSet<>();
        if (explicit != null) {
            for (Manifest.TagDeclaration e : explicit.values()) {
                merged.computeIfAbsent(e.path(), k -> new LinkedHashSet<>()).addAll(e.values());
                if (e.replace()) {
                    replace.add(e.path());
                }
            }
        }
        for (Map.Entry<String, Set<String>> e : merged.entrySet()) {
            if (e.getValue().isEmpty()) {
                continue;
            }
            // data/<ns>/tags/<kind>/<path>.json — same shape as
            // data/minecraft/tags/item/stone_bricks.json ({"values": [...]}).
            Map<String, Object> body = Json.obj("values", new ArrayList<>(e.getValue()));
            if (replace.contains(e.getKey())) {
                body.put("replace", true);
            }
            out.put(data(ns, "tags/" + kind + "/" + e.getKey() + ".json"), json(body));
        }
    }

    /**
     * Writes custom tags under the Weaver nested layout, which can carry merge priority and
     * replace:
     * {@code data/<ns>/weaver/tags/<registryNs>/<registryPath>/<tagPath>.json}.
     */
    private static void emitCustomTags(Map<String, byte[]> out, String ns, String registryId,
                                       Map<String, Manifest.TagDeclaration> declarations) {
        int colon = registryId.indexOf(':');
        String registryNs = registryId.substring(0, colon);
        String registryPath = registryId.substring(colon + 1);
        for (Manifest.TagDeclaration declaration : declarations.values()) {
            Set<String> values = new LinkedHashSet<>(declaration.values());
            Map<String, Object> body = Json.obj("values", new ArrayList<>(values));
            if (declaration.priority() != 0) {
                body.put("priority", declaration.priority());
            }
            if (declaration.replace()) {
                body.put("replace", true);
            }
            out.put(data(ns, "weaver/tags/" + registryNs + "/" + registryPath + "/"
                    + declaration.path() + ".json"), json(body));
        }
    }

    private static String assets(String ns, String rel) {
        return "assets/" + ns + "/" + rel;
    }

    private static String data(String ns, String rel) {
        return "data/" + ns + "/" + rel;
    }

    private static byte[] json(Object value) {
        return Json.write(value).getBytes(StandardCharsets.UTF_8);
    }

    // ---------------------------------------------------------------- placeholder texture

    private static final int[][] PALETTE = {
            {0x6B, 0x8E, 0xA8}, {0x8A, 0x7A, 0xA8}, {0x8E, 0xA8, 0x6B},
            {0xA8, 0x8E, 0x6B}, {0xA8, 0x6B, 0x7A}, {0x6B, 0xA8, 0x9A},
            {0x8C, 0x8C, 0x8C}, {0xA8, 0xA0, 0x6B},
    };

    /**
     * A 16x16 placeholder PNG so a freshly generated module renders instead of showing the
     * game's missing-texture checkerboard. Deliberately not magenta/black (that pattern means
     * "missing texture"); replace it with real art. Written with {@link Deflater} + {@link CRC32}
     * — no image library, no dependency.
     */
    static byte[] placeholderTexture(String id) {
        int size = 16;
        int hash = 0x811C9DC5;
        for (int i = 0; i < id.length(); i++) {
            hash = (hash ^ id.charAt(i)) * 0x01000193;
        }
        int[] base = PALETTE[Math.floorMod(hash, PALETTE.length)];
        int light = (base[0] << 16) | (base[1] << 8) | base[2];
        int dark = (dim(base[0]) << 16) | (dim(base[1]) << 8) | dim(base[2]);
        int edge = (dim(dim(base[0])) << 16) | (dim(dim(base[1])) << 8) | dim(dim(base[2]));

        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        for (int y = 0; y < size; y++) {
            raw.write(0); // PNG filter type 0 ("none") for this scanline
            for (int x = 0; x < size; x++) {
                boolean border = x == 0 || y == 0 || x == size - 1 || y == size - 1;
                boolean tile = ((x / 4) + (y / 4)) % 2 == 0;
                int rgb = border ? edge : (tile ? light : dark);
                raw.write((rgb >> 16) & 0xFF);
                raw.write((rgb >> 8) & 0xFF);
                raw.write(rgb & 0xFF);
            }
        }

        ByteArrayOutputStream png = new ByteArrayOutputStream();
        png.writeBytes(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});

        ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
        writeInt(ihdr, size);
        writeInt(ihdr, size);
        ihdr.write(8); // bit depth
        ihdr.write(2); // colour type 2 = truecolour RGB
        ihdr.write(0); // compression
        ihdr.write(0); // filter
        ihdr.write(0); // interlace
        chunk(png, "IHDR", ihdr.toByteArray());
        chunk(png, "IDAT", deflate(raw.toByteArray()));
        chunk(png, "IEND", new byte[0]);
        return png.toByteArray();
    }

    private static int dim(int channel) {
        return Math.max(0, (channel * 7) / 10);
    }

    private static void writeInt(ByteArrayOutputStream out, int value) {
        out.write((value >>> 24) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] data) {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        writeInt(out, data.length);
        out.writeBytes(typeBytes);
        out.writeBytes(data);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        writeInt(out, (int) crc.getValue());
    }

    private static byte[] deflate(byte[] data) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        try {
            deflater.setInput(data);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(data.length);
            byte[] buffer = new byte[8192];
            while (!deflater.finished()) {
                int n = deflater.deflate(buffer);
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }
}
