package net.weaver.core.modops;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.weaver.core.Weaver;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Immutable mod-list report produced by a discovery run.
 *
 * <p>The report contains every discovered candidate, including rejected jars,
 * in deterministic jar-file order. Accepted entries carry a zero-based
 * {@link ModListEntry#loadOrder()} and are also available through
 * {@link #loadOrdered()}. Diagnostics are recorded in the order the loader
 * emitted them.
 *
 * <p>{@link #toJson()} emits a stable, self-describing document suitable for a
 * launcher UI or CLI. {@link #toTextLines()} emits the same information in a
 * line-oriented human-readable form.
 */
public final class ModList {

    /** Version of the JSON/text report schema emitted by this class. */
    public static final int SCHEMA_VERSION = 1;

    private static final String LOADER_NAME = "weaver";
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private final String side;
    private final List<ModListEntry> entries;
    private final List<ModDiagnostic> diagnostics;

    /**
     * Creates a report.
     *
     * @param side        process side the report was produced for; {@code "unknown"}
     *                    when the caller passed {@code null}
     * @param entries     all discovered entries; copied and sorted by jar file name
     * @param diagnostics all diagnostics; copied in iteration order
     * @throws NullPointerException if {@code entries} or {@code diagnostics} is {@code null}
     */
    public ModList(String side, List<ModListEntry> entries, List<ModDiagnostic> diagnostics) {
        this.side = side == null ? "unknown" : side;
        List<ModListEntry> sorted = new ArrayList<>(Objects.requireNonNull(entries, "entries"));
        sorted.sort(Comparator.comparing(ModListEntry::file).thenComparing(ModListEntry::id));
        this.entries = List.copyOf(sorted);
        this.diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    }

    /**
     * Returns the process side this list was produced for.
     *
     * @return {@code client}, {@code server}, or {@code unknown}
     */
    public String side() {
        return side;
    }

    /**
     * Returns every discovered entry, including rejected entries, in jar-file order.
     *
     * @return immutable entry list
     */
    public List<ModListEntry> entries() {
        return entries;
    }

    /**
     * Returns the structured diagnostics emitted during discovery.
     *
     * @return immutable diagnostic list
     */
    public List<ModDiagnostic> diagnostics() {
        return diagnostics;
    }

    /**
     * Returns the entries that were loaded, in load order.
     *
     * @return immutable accepted entry list
     */
    public List<ModListEntry> accepted() {
        return loadOrdered();
    }

    /**
     * Returns the entries that were discovered but not loaded, in jar-file order.
     *
     * @return immutable rejected entry list
     */
    public List<ModListEntry> rejected() {
        return entries.stream()
                .filter(entry -> entry.status() != ModStatus.LOADED)
                .toList();
    }

    /**
     * Returns loaded entries sorted by their zero-based load position.
     *
     * @return immutable load-ordered entry list
     */
    public List<ModListEntry> loadOrdered() {
        return entries.stream()
                .filter(entry -> entry.status() == ModStatus.LOADED)
                .sorted(Comparator.comparingInt(ModListEntry::loadOrder))
                .toList();
    }

    /**
     * Serializes the report as stable, pretty-printed JSON.
     *
     * <p>The document contains {@code schemaVersion}, a {@code loader} object
     * with {@code name} and {@code version}, the process {@code side}, a
     * {@code summary}, the {@code mods} array, and the {@code diagnostics}
     * array. Map-valued fields are emitted in sorted key order.
     *
     * @return JSON text without a trailing newline
     */
    public String toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION);

        JsonObject loader = new JsonObject();
        loader.addProperty("name", LOADER_NAME);
        loader.addProperty("version", Weaver.VERSION);
        root.add("loader", loader);

        root.addProperty("side", side);

        JsonObject summary = new JsonObject();
        summary.addProperty("total", entries.size());
        summary.addProperty("loaded", accepted().size());
        summary.addProperty("rejected", rejected().size());
        summary.addProperty("diagnostics", diagnostics.size());
        summary.addProperty("cycles", cycleCount());
        root.add("summary", summary);

        JsonArray mods = new JsonArray();
        for (ModListEntry entry : entries) {
            mods.add(entryJson(entry));
        }
        root.add("mods", mods);

        JsonArray diagnosticArray = new JsonArray();
        for (ModDiagnostic diagnostic : diagnostics) {
            JsonObject item = new JsonObject();
            item.addProperty("severity", diagnostic.severity().name());
            item.addProperty("type", diagnostic.type().name());
            item.addProperty("subject", diagnostic.subject());
            item.addProperty("message", diagnostic.message());
            diagnosticArray.add(item);
        }
        root.add("diagnostics", diagnosticArray);

        return GSON.toJson(root);
    }

    /**
     * Renders the report as human-readable lines.
     *
     * @return immutable lines, starting with the header and ending with the summary
     */
    public List<String> toTextLines() {
        List<String> lines = new ArrayList<>();
        lines.add("weaver mod list schema=" + SCHEMA_VERSION + " loader=" + Weaver.VERSION
                + " side=" + side);
        for (ModListEntry entry : entries) {
            StringBuilder line = new StringBuilder("mod id=").append(entry.id())
                    .append(" version=").append(entry.version())
                    .append(" name=").append(entry.name())
                    .append(" side=").append(entry.side())
                    .append(" minecraft=").append(entry.minecraft())
                    .append(" main=").append(entry.mainClass())
                    .append(" file=").append(entry.file())
                    .append(" status=").append(entry.status())
                    .append(" order=").append(entry.loadOrder())
                    .append(" deps=").append(entry.dependencies())
                    .append(" optional=").append(entry.optional())
                    .append(" breaks=").append(entry.breaks())
                    .append(" mixins=").append(entry.mixins())
                    .append(" at=").append(entry.accessTransformers());
            if (!entry.statusDetail().isBlank()) {
                line.append(" detail=").append(entry.statusDetail());
            }
            lines.add(line.toString());
        }
        for (ModDiagnostic diagnostic : diagnostics) {
            lines.add("diagnostic severity=" + diagnostic.severity()
                    + " type=" + diagnostic.type()
                    + " subject=" + diagnostic.subject()
                    + " message=" + diagnostic.message());
        }
        lines.add("summary " + summaryLine());
        return List.copyOf(lines);
    }

    /**
     * Returns a compact one-line summary of counts.
     *
     * @return text such as {@code mods total=3 loaded=2 rejected=1 diagnostics=1 cycles=0}
     */
    public String summaryLine() {
        return "mods total=" + entries.size()
                + " loaded=" + accepted().size()
                + " rejected=" + rejected().size()
                + " diagnostics=" + diagnostics.size()
                + " cycles=" + cycleCount();
    }

    private long cycleCount() {
        return diagnostics.stream()
                .filter(diagnostic -> diagnostic.type() == ModDiagnostic.Type.CYCLE)
                .count();
    }

    private static JsonObject entryJson(ModListEntry entry) {
        JsonObject mod = new JsonObject();
        mod.addProperty("id", entry.id());
        mod.addProperty("version", entry.version());
        mod.addProperty("name", entry.name());
        mod.addProperty("side", entry.side());
        mod.addProperty("minecraft", entry.minecraft());
        mod.addProperty("mainClass", entry.mainClass());
        mod.add("dependencies", mapJson(entry.dependencies()));
        mod.add("optional", mapJson(entry.optional()));
        mod.add("breaks", mapJson(entry.breaks()));
        mod.add("mixins", arrayJson(entry.mixins()));
        mod.add("accessTransformers", arrayJson(entry.accessTransformers()));
        mod.addProperty("loadOrder", entry.loadOrder());
        mod.addProperty("status", entry.status().name());
        mod.addProperty("statusDetail", entry.statusDetail());
        mod.addProperty("file", entry.file());
        return mod;
    }

    private static JsonObject mapJson(Map<String, String> values) {
        JsonObject object = new JsonObject();
        for (Map.Entry<String, String> entry : new TreeMap<>(values).entrySet()) {
            object.addProperty(entry.getKey(), entry.getValue());
        }
        return object;
    }

    private static JsonArray arrayJson(List<String> values) {
        JsonArray array = new JsonArray();
        for (String value : values) {
            array.add(value);
        }
        return array;
    }
}
