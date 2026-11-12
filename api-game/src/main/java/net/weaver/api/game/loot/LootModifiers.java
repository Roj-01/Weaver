package net.weaver.api.game.loot;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.weaver.api.game.data.ModifierFiles;

/**
 * Global loot modifiers: data pack files that change what any loot table
 * generates, reloaded with {@code /reload}.
 *
 * <p>Files live at {@code data/<namespace>/weaver/loot_modifiers/<path>.json};
 * the file id is {@code <namespace>:<path>}. A pack higher in the list
 * replaces a file with the same id. Every file applies, in id order:</p>
 *
 * <pre>{@code
 * {
 *   "type": "weaver:add_items",
 *   "tables": ["minecraft:blocks/short_grass"],
 *   "conditions": [{"type": "minecraft:random_chance", "chance": 0.1}],
 *   "items": [{"id": "minecraft:diamond"}]
 * }
 * }</pre>
 *
 * <ul>
 *   <li>{@code tables} (optional): loot table ids the file applies to; absent
 *       or empty means every table that has an id.</li>
 *   <li>{@code conditions} (optional): vanilla loot conditions, all of which
 *       must pass for the roll.</li>
 *   <li>{@code enabled} (optional, default {@code true}): {@code false}
 *       switches the file off, so a pack can disable another pack's file by
 *       overriding it.</li>
 * </ul>
 *
 * <p>Built-in types: {@code weaver:add_items} ({@code items}: item stacks),
 * {@code weaver:remove_items} ({@code items}: item ids or a {@code #tag}), and
 * {@code weaver:replace_items} ({@code from}: ids or a tag, {@code to}: an item
 * id; counts are kept). Modifiers run on the whole result of a top-level roll
 * (block drops, entity drops, chests, fishing, ...), not on nested tables.</p>
 */
public final class LootModifiers {
    private static final Map<Identifier, MapCodec<? extends LootModifier>> TYPES = new ConcurrentHashMap<>();
    private static final ThreadLocal<LootContext> ROLLING = new ThreadLocal<>();

    /** One loaded file. */
    public record Entry(Identifier id, Set<Identifier> tables, List<LootItemCondition> conditions,
                        LootModifier modifier) {
        public Entry {
            tables = Set.copyOf(tables);
            conditions = List.copyOf(conditions);
        }
    }

    private static volatile List<Entry> entries = List.of();
    private static volatile HolderLookup.Provider idSource;
    private static volatile Map<LootTable, Identifier> tableIds = Map.of();

    static {
        registerType(Identifier.fromNamespaceAndPath("weaver", "add_items"), BuiltInLootModifiers.AddItems.CODEC);
        registerType(Identifier.fromNamespaceAndPath("weaver", "remove_items"), BuiltInLootModifiers.RemoveItems.CODEC);
        registerType(Identifier.fromNamespaceAndPath("weaver", "replace_items"), BuiltInLootModifiers.ReplaceItems.CODEC);
    }

    private LootModifiers() {
    }

    /**
     * Registers a modifier type. Files with {@code "type": "<id>"} are read
     * with {@code codec}; register during mod setup, before the first data
     * load.
     *
     * @throws IllegalArgumentException when the id is taken
     */
    public static void registerType(Identifier id, MapCodec<? extends LootModifier> codec) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(codec, "codec");
        if (TYPES.putIfAbsent(id, codec) != null) {
            throw new IllegalArgumentException("loot modifier type already registered: " + id);
        }
    }

    /** The files loaded by the last data pack load, in the order they apply. */
    public static List<Entry> loaded() {
        return entries;
    }

    /**
     * Parses modifier files. Invalid files are skipped with a message to
     * {@code warn}.
     */
    public static List<Entry> parse(Map<Identifier, JsonElement> files, HolderLookup.Provider lookup,
            Consumer<String> warn) {
        RegistryOps<JsonElement> ops = lookup.createSerializationContext(JsonOps.INSTANCE);
        return ModifierFiles.parse("LOOT MODIFIER", files, TYPES, ops, parsed -> {
            JsonObject object = parsed.json();
            Set<Identifier> tables = new LinkedHashSet<>();
            if (object.has("tables")) {
                tables.addAll(ModifierFiles.orThrow(Identifier.CODEC.listOf().parse(ops, object.get("tables"))));
            }
            List<LootItemCondition> conditions = List.of();
            if (object.has("conditions")) {
                conditions = ModifierFiles.orThrow(
                        LootItemCondition.DIRECT_CODEC.listOf().parse(ops, object.get("conditions")));
            }
            return new Entry(parsed.id(), tables, conditions, parsed.value());
        }, warn);
    }

    /** Replaces the active modifiers. */
    public static void install(List<Entry> loaded) {
        entries = List.copyOf(loaded);
    }

    /**
     * Runs a top-level roll of {@code table} through the modifiers. Called by
     * the loot table mixin; returns {@code false} when it did nothing and the
     * table should roll normally.
     */
    public static boolean roll(LootTable table, LootContext context, Set<?> visited, Consumer<ItemStack> output) {
        List<Entry> active = entries;
        if (active.isEmpty() || !visited.isEmpty() || ROLLING.get() == context) {
            return false;
        }
        ObjectArrayList<ItemStack> loot = new ObjectArrayList<>();
        LootContext previous = ROLLING.get();
        ROLLING.set(context);
        try {
            table.getRandomItemsRaw(context, loot::add);
        } finally {
            ROLLING.set(previous);
        }
        Identifier id = tableId(context.getLevel(), table);
        for (Entry entry : active) {
            if (!entry.tables().isEmpty() && (id == null || !entry.tables().contains(id))) {
                continue;
            }
            if (id == null && entry.tables().isEmpty()) {
                continue;
            }
            try {
                // A failing condition or modifier skips this file only.
                if (passes(entry.conditions(), context)) {
                    entry.modifier().apply(loot, context);
                }
            } catch (VirtualMachineError fatal) {
                if (!(fatal instanceof StackOverflowError)) {
                    throw fatal;
                }
                System.out.println("[weaver] LOOT MODIFIER FAILED id=" + entry.id() + " table=" + id
                        + " reason=" + fatal);
            } catch (Throwable failure) {
                System.out.println("[weaver] LOOT MODIFIER FAILED id=" + entry.id() + " table=" + id
                        + " reason=" + failure);
            }
        }
        for (ItemStack stack : loot) {
            if (stack != null && !stack.isEmpty()) {
                output.accept(stack);
            }
        }
        return true;
    }

    private static boolean passes(List<LootItemCondition> conditions, LootContext context) {
        for (LootItemCondition condition : conditions) {
            if (!condition.test(context)) {
                return false;
            }
        }
        return true;
    }

    /** The registry id of {@code table}, or {@code null} for a table built in code. */
    public static Identifier tableId(ServerLevel level, LootTable table) {
        HolderLookup.Provider lookup = level.getServer().reloadableRegistries().lookup();
        if (idSource != lookup) {
            Map<LootTable, Identifier> ids = new IdentityHashMap<>();
            lookup.lookupOrThrow(Registries.LOOT_TABLE).listElements()
                    .forEach(holder -> ids.put(holder.value(), holder.key().identifier()));
            tableIds = ids;
            idSource = lookup;
        }
        return tableIds.get(table);
    }
}
