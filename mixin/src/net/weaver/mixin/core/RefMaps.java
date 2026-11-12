package net.weaver.mixin.core;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.weaver.mixin.spi.MappingService;

/**
 * Resolves a Fabric-style refmap (JSON with {@code mappings} and {@code data}) inside the mod
 * loader and, when a {@link MappingService} is bound, remaps every referenced owner/member into
 * the runtime namespace. The resolved buffer gets a synthetic resource name so two mods cannot
 * collide over {@code "my.mod.refmap.json"}.
 *
 * <p>Mixin's {@code ReferenceMapper} keeps three levels: {@code mappings[mixinClass][key]=reference}
 * and {@code data[context][mixinClass][key]=reference}. Keys are source aliases looked up by the
 * mixin bytecode and must stay untouched; only the <em>reference values</em> are remapped. A
 * reference is either the canonical JVM form write by the Mixin annotation processor
 * ({@code Lowner;name(desc)V}, {@code Lowner;name:fieldDesc}) or the older dotted form
 * ({@code owner.name(desc)}) used by early Weaver fixtures. Both are handled, including nested
 * {@code data} maps, so a refmap that another mod ships under a sub-directory is remapped whole
 * instead of silently passing through.
 */
public final class RefMaps {

    /** Result of a refmap lookup: either resolved bytes or an explicit miss. */
    public record Resolved(String resourceName, byte[] json, int mappings, int changed,
                           boolean remapped) {
    }

    private RefMaps() {
    }

    /** Returns {@code null} when the config declares no refmap or the mod does not ship it. */
    public static Resolved resolve(String syntheticBase, String declaredName, ClassLoader modLoader,
                                   MappingService mapper) {
        if (declaredName == null || declaredName.isBlank()) {
            return null;
        }
        byte[] original = ResourceRegistry.resource(modLoader, declaredName);
        if (original == null) {
            return null;
        }
        try {
            JsonElement root = JsonParser.parseString(new String(original, StandardCharsets.UTF_8));
            if (!root.isJsonObject()) {
                return null;
            }
            JsonObject json = root.getAsJsonObject();
            int total;
            int changed = 0;
            if (mapper != null) {
                total = remapSection(json, mapper);
                changed = total;
            } else {
                total = countEntries(json);
            }
            String resourceName = syntheticBase + ".refmap.json";
            return new Resolved(resourceName, json.toString().getBytes(StandardCharsets.UTF_8),
                    total, changed, mapper != null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Walks one level of the refmap tree: {@code {mixinClass -> {key -> reference}}} is a leaf and
     * is remapped member-wise, anything nested (the {@code data[context]} map) is descended.
     */
    static int remapSection(JsonObject section, MappingService mapper) {
        int changed = 0;
        for (String key : new ArrayList<>(section.keySet())) {
            JsonElement value = section.get(key);
            if (value == null || !value.isJsonObject()) {
                if (value != null && value.isJsonPrimitive()
                        && value.getAsJsonPrimitive().isString()) {
                    String original = value.getAsString();
                    String mapped = mapReference(original, mapper);
                    if (!mapped.equals(original)) {
                        changed++;
                        section.addProperty(key, mapped);
                    }
                }
                continue;
            }
            JsonObject child = value.getAsJsonObject();
            if (child.isEmpty()) {
                continue;
            }
            if (isMemberTable(child)) {
                changed += remapMemberTable(child, mapper);
            } else {
                changed += remapSection(child, mapper);
            }
        }
        return changed;
    }

    private static boolean isMemberTable(JsonObject object) {
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            JsonElement value = entry.getValue();
            if (value == null || !value.isJsonPrimitive()
                    || !value.getAsJsonPrimitive().isString()) {
                return false;
            }
        }
        return !object.isEmpty();
    }

    private static int remapMemberTable(JsonObject table, MappingService mapper) {
        int changed = 0;
        for (String key : new ArrayList<>(table.keySet())) {
            JsonElement value = table.get(key);
            if (value == null || !value.isJsonPrimitive()
                    || !value.getAsJsonPrimitive().isString()) {
                continue;
            }
            String original = value.getAsString();
            String mapped = mapReference(original, mapper);
            if (!mapped.equals(original)) {
                changed++;
                table.addProperty(key, mapped);
            }
        }
        return changed;
    }

    private static int countEntries(JsonObject json) {
        return countSection(json);
    }

    private static int countSection(JsonObject section) {
        int count = 0;
        for (Map.Entry<String, JsonElement> entry : section.entrySet()) {
            JsonElement value = entry.getValue();
            if (value == null) {
                continue;
            }
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                count++;
            } else if (value.isJsonObject()) {
                count += countSection(value.getAsJsonObject());
            }
        }
        return count;
    }

    /**
     * Remaps one reference into the runtime namespace. Both the canonical JVM reference form
     * ({@code Lowner;name(desc)}) and the legacy dotted form ({@code owner.name(desc)}) keep their
     * shape; a reference that cannot be mapped is returned verbatim (fail-open per reference, the
     * config itself still carries a warning when the mapping service rejected it).
     */
    static String mapReference(String reference, MappingService mapper) {
        if (reference == null || reference.isBlank() || mapper == null) {
            return reference;
        }
        String text = reference.trim();
        String namespace = mapper.runtimeNamespace();
        if (text.charAt(0) == 'L') {
            int semi = text.indexOf(';');
            if (semi > 1) {
                return mapInternalReference(text, semi, mapper, namespace);
            }
        }
        return mapDottedReference(text, mapper, namespace);
    }

    /** {@code Lowner;member(desc)} / {@code Lowner;member:desc} / {@code Lowner;}. */
    private static String mapInternalReference(String text, int semi, MappingService mapper,
                                               String namespace) {
        String owner = text.substring(1, semi);
        String rest = text.substring(semi + 1);
        if (rest.isEmpty() || rest.startsWith("[")) {
            String mapped = mapper.mapDescriptor(text, "intermediary", namespace);
            return mapped == null || mapped.isBlank() ? text : mapped;
        }
        int paren = rest.indexOf('(');
        if (paren > 0) {
            String name = rest.substring(0, paren);
            String desc = rest.substring(paren);
            String mappedOwner = mapClass(owner, mapper, namespace);
            String mappedDesc = mapDescriptor(desc, mapper, namespace);
            String mappedName = mapper.mapMethod(mappedOwner, name, mappedDesc,
                    "intermediary", namespace);
            if (mappedName == null || mappedName.isBlank()) {
                mappedName = mapper.mapMethod(owner, name, desc, "intermediary", namespace);
            }
            if (mappedName == null || mappedName.isBlank()) {
                return text;
            }
            return "L" + mappedOwner + ";" + mappedName + mappedDesc;
        }
        int colon = rest.indexOf(':');
        if (colon > 0) {
            String name = rest.substring(0, colon);
            String desc = rest.substring(colon + 1);
            String mappedOwner = mapClass(owner, mapper, namespace);
            String mappedDesc = mapDescriptor(desc, mapper, namespace);
            String mappedName = mapper.mapField(mappedOwner, name, mappedDesc,
                    "intermediary", namespace);
            if (mappedName == null || mappedName.isBlank()) {
                mappedName = mapper.mapField(owner, name, desc, "intermediary", namespace);
            }
            if (mappedName == null || mappedName.isBlank()) {
                return text;
            }
            return "L" + mappedOwner + ";" + mappedName + ":" + mappedDesc;
        }
        return text;
    }

    /** {@code owner.name(desc)} / {@code owner.name} (legacy Weaver fixture form). */
    private static String mapDottedReference(String text, MappingService mapper, String namespace) {
        int paren = text.indexOf('(');
        String desc = paren >= 0 ? text.substring(paren) : null;
        String body = paren >= 0 ? text.substring(0, paren) : text;
        int dot = body.lastIndexOf('.');
        if (dot <= 0) {
            if (text.startsWith("[")) {
                String mapped = mapper.mapDescriptor(text, "intermediary", namespace);
                return mapped == null || mapped.isBlank() ? text : mapped;
            }
            return text;
        }
        String owner = body.substring(0, dot);
        String name = body.substring(dot + 1);
        String mappedOwnerInternal = mapClass(owner.replace('.', '/'), mapper, namespace);
        String mappedOwner = mappedOwnerInternal.replace('/', '.');
        String mappedName;
        if (desc == null) {
            mappedName = mapper.mapField(mappedOwnerInternal, name, "",
                    "intermediary", namespace);
        } else {
            String mappedDesc = mapDescriptor(desc, mapper, namespace);
            mappedName = mapper.mapMethod(mappedOwnerInternal, name, mappedDesc,
                    "intermediary", namespace);
            if (mappedName == null || mappedName.isBlank()) {
                mappedName = mapper.mapMethod(owner.replace('.', '/'), name, desc,
                        "intermediary", namespace);
            }
            desc = mappedDesc;
        }
        if (mappedName == null || mappedName.isBlank()) {
            return text;
        }
        return mappedOwner + "." + mappedName + (desc == null ? "" : desc);
    }

    private static String mapClass(String owner, MappingService mapper, String namespace) {
        String mapped = mapper.mapClass(owner, "intermediary", namespace);
        return mapped == null || mapped.isBlank() ? owner : mapped;
    }

    private static String mapDescriptor(String desc, MappingService mapper, String namespace) {
        if (desc == null || desc.isEmpty()) {
            return desc;
        }
        String mapped = mapper.mapDescriptor(desc, "intermediary", namespace);
        return mapped == null || mapped.isBlank() ? desc : mapped;
    }

    /** Owner names of every mixin table in a refmap, used for diagnostics only. */
    static List<String> owners(JsonObject json) {
        List<String> out = new ArrayList<>();
        JsonElement mappings = json.get("mappings");
        if (mappings != null && mappings.isJsonObject()) {
            out.addAll(mappings.getAsJsonObject().keySet());
        }
        return out;
    }
}
