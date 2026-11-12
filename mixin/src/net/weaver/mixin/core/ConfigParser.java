package net.weaver.mixin.core;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

/**
 * Reads a Fabric/Weaver {@code *.mixins.json} buffer and its mixin class annotations without
 * loading any class: the target list is taken from the {@code @Mixin} annotation via ASM.
 *
 * <p>Every field the engine acts on is read here, once, and handed to {@link MixinEngine} as an
 * immutable {@link Parsed}. Fields the engine only forwards to upstream Mixin ({@code priority},
 * {@code plugin}, {@code injectors}, {@code requiredFeatures}, unknown extensions) stay in
 * {@link Parsed#json()} untouched, so a config written for Fabric keeps its exact semantics.
 */
public final class ConfigParser {

    private static final String MIXIN_DESC = "Lorg/spongepowered/asm/mixin/Mixin;";
    private static final String ACCESSOR_DESC = "Lorg/spongepowered/asm/mixin/gen/Accessor;";
    private static final String INVOKER_DESC = "Lorg/spongepowered/asm/mixin/gen/Invoker;";

    /** Side marker for a config that applies to both sides. */
    public static final String SIDE_BOTH = "both";

    private static final java.util.regex.Pattern BINARY_NAME = java.util.regex.Pattern.compile(
            "[\\p{javaJavaIdentifierStart}][\\p{javaJavaIdentifierPart}]*"
                    + "(\\.[\\p{javaJavaIdentifierStart}][\\p{javaJavaIdentifierPart}]*)*");

    /**
     * One accepted config, fully resolved against the declaring mod loader.
     *
     * @param mixinClasses       classes armed for this side, in config order, deduplicated
     * @param targetsByMixin     targets declared by each armed mixin class
     * @param refmapName         declared {@code refmap} resource, or {@code null}
     * @param parentName         declared {@code parent} config, or {@code null}
     * @param minVersion         declared {@code minVersion}, or {@code null}
     * @param compatibilityLevel declared {@code compatibilityLevel}, or {@code null}
     * @param priority           declared {@code priority}, or {@code null}
     * @param pluginClass        declared {@code plugin} class, or {@code null}
     * @param injectorConfig     declared {@code injectors.defaultRequire}, or {@code null}
     * @param side               {@code client}, {@code server} or {@code both}
     * @param sideSkipped        entries removed because they belong to the other side
     * @param duplicates         entries listed more than once and collapsed
     * @param json               the config buffer (a private copy) for forwarding
     */
    public record Parsed(
            String modId,
            String configName,
            String syntheticName,
            List<String> mixinClasses,
            Set<String> targets,
            java.util.Map<String, Set<String>> targetsByMixin,
            String refmapName,
            String parentName,
            int accessorMethods,
            int invokerMethods,
            boolean required,
            JsonObject json,
            String minVersion,
            String compatibilityLevel,
            Integer priority,
            String pluginClass,
            String injectorConfig,
            String side,
            int sideSkipped,
            int duplicates) {
    }

    /** A config that cannot be armed; the reason lands verbatim in a marker. */
    public static final class Rejected extends Exception {
        private static final long serialVersionUID = 1L;

        private final String code;

        public Rejected(String reason) {
            this(reason, reason);
        }

        public Rejected(String reason, String code) {
            super(reason);
            this.code = code;
        }

        /** Stable machine-readable category (the {@code code=} field of a reject marker). */
        public String code() {
            return this.code;
        }
    }

    private ConfigParser() {
    }

    /**
     * The {@code plugin} a raw config declares, or {@code null}.
     *
     * <p>Read before parsing decides anything: a config can have no mixins and still be meaningful
     * because its plugin supplies them (and arms further configs), so the caller needs the name even
     * when {@link #parse} rejects the file as empty.
     */
    public static String declaredPlugin(byte[] bytes) {
        try {
            JsonElement root = JsonParser.parseString(
                    new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
            if (!root.isJsonObject()) {
                return null;
            }
            JsonElement plugin = root.getAsJsonObject().get("plugin");
            if (plugin == null || plugin.isJsonNull() || !plugin.getAsJsonPrimitive().isString()) {
                return null;
            }
            String name = plugin.getAsString();
            return name == null || name.isBlank() ? null : name;
        } catch (Throwable t) {
            return null;
        }
    }

    public static Parsed parse(String modId, String configName, byte[] bytes, ClassLoader modLoader)
            throws Rejected {
        return parse(modId, configName, bytes, modLoader, SIDE_BOTH);
    }

    /**
     * Parses one config for a given launch side.
     *
     * <p>{@code client} entries are armed only when the launch side is {@code client} and
     * {@code server} entries only when it is {@code server}; arming both on a dedicated server
     * makes every client-only target look absent and rejects the whole config.
     */
    public static Parsed parse(String modId, String configName, byte[] bytes, ClassLoader modLoader,
                               String side) throws Rejected {
        String effectiveSide = side == null || side.isBlank()
                ? SIDE_BOTH : side.toLowerCase(java.util.Locale.ROOT);
        if (!SIDE_BOTH.equals(effectiveSide) && !"client".equals(effectiveSide)
                && !"server".equals(effectiveSide)) {
            throw new Rejected("invalid-side:" + effectiveSide, "invalid-side");
        }
        JsonObject json;
        try {
            JsonElement root = JsonParser.parseString(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
            if (!root.isJsonObject()) {
                throw new Rejected("json:not-an-object", "json");
            }
            json = root.getAsJsonObject();
        } catch (Rejected r) {
            throw r;
        } catch (RuntimeException e) {
            throw new Rejected("json:" + Marker.oneLine(e), "json");
        }

        String pkg = string(json, "package");
        if (pkg != null && !pkg.isBlank() && !BINARY_NAME.matcher(pkg).matches()) {
            throw new Rejected("package-invalid:" + pkg, "package-invalid");
        }
        LinkedHashSet<String> classes = new LinkedHashSet<>();
        int duplicates = collect(json, pkg, "mixins", classes);
        int sideSkipped = 0;
        if (SIDE_BOTH.equals(effectiveSide) || "client".equals(effectiveSide)) {
            duplicates += collect(json, pkg, "client", classes);
        } else {
            sideSkipped += count(json, "client");
        }
        if (SIDE_BOTH.equals(effectiveSide) || "server".equals(effectiveSide)) {
            duplicates += collect(json, pkg, "server", classes);
        } else {
            sideSkipped += count(json, "server");
        }
        if (classes.isEmpty()) {
            throw new Rejected("no-mixins-declared", "empty-config");
        }

        Set<String> targets = new LinkedHashSet<>();
        java.util.Map<String, Set<String>> targetsByMixin = new java.util.LinkedHashMap<>();
        int accessors = 0;
        int invokers = 0;
        for (String mixin : classes) {
            Set<String> mixinTargets = targetsOf(mixin, modLoader);
            targetsByMixin.put(mixin, Set.copyOf(mixinTargets));
            targets.addAll(mixinTargets);
            int[] counts = accessorCount(mixin, modLoader);
            accessors += counts[0];
            invokers += counts[1];
        }
        if (targets.isEmpty()) {
            throw new Rejected("no-targets:annotation-missing", "no-targets");
        }

        String refmap = string(json, "refmap");
        String parent = string(json, "parent");
        if (parent != null && parent.isBlank()) {
            parent = null;
        }
        boolean required = true;
        JsonElement requiredElement = json.get("required");
        if (requiredElement != null && requiredElement.isJsonPrimitive()
                && requiredElement.getAsJsonPrimitive().isBoolean()) {
            required = requiredElement.getAsBoolean();
        } else if (requiredElement != null && !requiredElement.isJsonNull()) {
            throw new Rejected("json:required-not-a-boolean", "json");
        }
        String minVersion = string(json, "minVersion");
        String compatibilityLevel = string(json, "compatibilityLevel");
        String pluginClass = string(json, "plugin");
        String injectorConfig = string(json, "injectors.defaultRequire");
        Integer priority = readPriority(json);
        String synthetic = "weaver/" + modId + "/" + configName;
        return new Parsed(modId, configName, synthetic, List.copyOf(classes), Set.copyOf(targets),
                java.util.Map.copyOf(targetsByMixin), refmap, parent, accessors, invokers,
                required, json, minVersion, compatibilityLevel, priority, pluginClass,
                injectorConfig, effectiveSide, sideSkipped, duplicates);
    }

    private static Integer readPriority(JsonObject json) throws Rejected {
        JsonElement value = json.get("priority");
        if (value == null || value.isJsonNull()) {
            return null;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new Rejected("json:priority-not-a-number", "json");
        }
        return value.getAsInt();
    }

    /** Collects one section; returns how many duplicate entries were collapsed. */
    private static int collect(JsonObject json, String pkg, String key, Set<String> out)
            throws Rejected {
        JsonElement element = json.get(key);
        if (element == null || element.isJsonNull()) {
            return 0;
        }
        if (!element.isJsonArray()) {
            throw new Rejected("json:" + key + "-not-an-array", "json");
        }
        int duplicates = 0;
        JsonArray array = element.getAsJsonArray();
        for (JsonElement item : array) {
            if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) {
                throw new Rejected("json:" + key + "-entry-not-a-string", "json");
            }
            String simple = item.getAsString().trim();
            if (simple.isEmpty()) {
                throw new Rejected("json:" + key + "-entry-blank", "json");
            }
            // Config entries are relative to "package", even when they name a sub-package
            // ("accessor.ChunkMapAccessor"). Only an already-prefixed name is left untouched.
            String binary = pkg == null || pkg.isBlank() || simple.startsWith(pkg + ".")
                    ? simple : pkg + "." + simple;
            if (!BINARY_NAME.matcher(binary).matches()) {
                throw new Rejected("mixin-entry-invalid:" + simple, "mixin-entry-invalid");
            }
            if (!out.add(binary)) {
                duplicates++;
            }
        }
        return duplicates;
    }

    /** Counts entries in a section without arming it (side filtering). */
    private static int count(JsonObject json, String key) {
        JsonElement element = json.get(key);
        return element != null && element.isJsonArray() ? element.getAsJsonArray().size() : 0;
    }

    private static String string(JsonObject json, String key) {
        JsonElement element = json.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
                ? element.getAsString() : null;
    }

    /**
     * Walks the mixin's own class, its superclasses and its interfaces, reading {@code @Mixin}
     * targets. Mixin allows a mixin class to inherit the annotation from an abstract parent or a
     * shared interface; restricting the walk to {@code superName} made those classes look
     * target-less and the whole config was rejected.
     */
    static Set<String> targetsOf(String binaryName, ClassLoader loader) throws Rejected {
        Set<String> targets = new LinkedHashSet<>();
        java.util.Deque<String> pending = new java.util.ArrayDeque<>();
        Set<String> visited = new LinkedHashSet<>();
        pending.add(binaryName);
        while (!pending.isEmpty() && visited.size() < 32) {
            String current = pending.poll();
            if (current == null || !visited.add(current) || "java.lang.Object".equals(current)) {
                continue;
            }
            ClassNode node = readNode(current, loader);
            if (node == null) {
                if (visited.size() == 1) {
                    throw new Rejected("mixin-class-missing:" + current, "mixin-class-missing");
                }
                continue;
            }
            AnnotationNode mixin = annotation(node, MIXIN_DESC);
            if (mixin != null) {
                readTargets(mixin, targets);
                if (!targets.isEmpty()) {
                    return targets;
                }
            }
            if (node.superName != null) {
                pending.add(node.superName.replace('/', '.'));
            }
            if (node.interfaces != null) {
                for (String iface : node.interfaces) {
                    pending.add(iface.replace('/', '.'));
                }
            }
        }
        return targets;
    }

    /** Counts {@code @Accessor}/{@code @Invoker} methods so a config marker states what it carries. */
    static int[] accessorCount(String binaryName, ClassLoader loader) {
        byte[] bytes = classBytes(binaryName, loader);
        if (bytes == null) {
            return new int[]{0, 0};
        }
        ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(bytes).accept(node,
                ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        int accessors = 0;
        int invokers = 0;
        if (node.methods != null) {
            for (org.objectweb.asm.tree.MethodNode method : node.methods) {
                if (annotation(method.visibleAnnotations, ACCESSOR_DESC)
                        || annotation(method.invisibleAnnotations, ACCESSOR_DESC)) {
                    accessors++;
                }
                if (annotation(method.visibleAnnotations, INVOKER_DESC)
                        || annotation(method.invisibleAnnotations, INVOKER_DESC)) {
                    invokers++;
                }
            }
        }
        return new int[]{accessors, invokers};
    }

    private static boolean annotation(List<AnnotationNode> annotations, String desc) {
        if (annotations == null) {
            return false;
        }
        for (AnnotationNode annotation : annotations) {
            if (desc.equals(annotation.desc)) {
                return true;
            }
        }
        return false;
    }

    static ClassNode readNode(String binaryName, ClassLoader loader) {
        byte[] bytes = classBytes(binaryName, loader);
        if (bytes == null) {
            return null;
        }
        ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(bytes).accept(node,
                ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return node;
    }

    /**
     * Bytes of one class through a specific loader, cached: a config is parsed once but each mixin
     * class is read by target discovery, accessor counting and the feature census.
     */
    private static byte[] classBytes(String binaryName, ClassLoader loader) {
        String path = binaryName.replace('.', '/') + ".class";
        byte[] cached = MixinEngine.get().resources().cachedResource(loader, path);
        return cached != null ? cached : ResourceRegistry.resource(loader, path);
    }

    private static AnnotationNode annotation(ClassNode node, String desc) {
        if (node.visibleAnnotations != null) {
            for (AnnotationNode a : node.visibleAnnotations) {
                if (desc.equals(a.desc)) {
                    return a;
                }
            }
        }
        if (node.invisibleAnnotations != null) {
            for (AnnotationNode a : node.invisibleAnnotations) {
                if (desc.equals(a.desc)) {
                    return a;
                }
            }
        }
        return null;
    }

    private static void readTargets(AnnotationNode mixin, Set<String> targets) {
        if (mixin.values == null) {
            return;
        }
        for (int i = 0; i + 1 < mixin.values.size(); i += 2) {
            String key = String.valueOf(mixin.values.get(i));
            Object value = mixin.values.get(i + 1);
            if ("value".equals(key) && value instanceof List<?> list) {
                for (Object item : list) {
                    String name = typeName(item);
                    if (name != null) {
                        targets.add(name);
                    }
                }
            } else if ("targets".equals(key) && value instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof String s && !s.isBlank()) {
                        targets.add(s.replace('/', '.'));
                    }
                }
            }
        }
    }

    private static String typeName(Object value) {
        if (value instanceof org.objectweb.asm.Type type && type.getSort() == org.objectweb.asm.Type.OBJECT) {
            return type.getClassName();
        }
        return null;
    }

    /** Extracts a specific string field from a config buffer (used for diagnostics). */
    public static String field(byte[] bytes, String name) {
        try {
            JsonElement root = JsonParser.parseString(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
            if (!root.isJsonObject()) {
                return null;
            }
            JsonObject object = root.getAsJsonObject();
            if (name.indexOf('.') > 0) {
                JsonElement current = object;
                for (String part : name.split("\\.")) {
                    if (current == null || !current.isJsonObject()) {
                        return null;
                    }
                    current = current.getAsJsonObject().get(part);
                }
                return current != null && current.isJsonPrimitive()
                        && current.getAsJsonPrimitive().isString() ? current.getAsString() : null;
            }
            return string(object, name);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
