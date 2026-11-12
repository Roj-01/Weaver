package net.weaver.mixin;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;

/**
 * Thin glue between Weaver and the real Mixin engine.
 * Boots the engine against WeaverMixinService and exposes the load-time transform.
 */
public final class MixinBridge {

    private static IMixinTransformer transformer;
    private static boolean booted;
    private static String version = "unknown";
    private static volatile Instrumentation instrumentation;
    private static volatile boolean extrasAvailable;
    private static final Set<String> SYNTHETIC_DEFINED = ConcurrentHashMap.newKeySet();
    private static final Map<String, Class<?>> SYNTHETIC_CLASSES = new ConcurrentHashMap<>();

    private MixinBridge() {
    }

    public static synchronized void boot() {
        boot("client");
    }

    /** Boots the engine with an explicit side ({@code client} or {@code server}). */
    public static synchronized void boot(String side) {
        if (booted) {
            return;
        }
        // The environment level comes from this option (default: the service minimum). Declare
        // JAVA_25 before the bootstrap reads it; a caller-supplied value is never overridden.
        if (System.getProperty("mixin.env.compatLevel") == null) {
            System.setProperty("mixin.env.compatLevel", "JAVA_25");
        }
        // Field initialisers must run on every instance, immediately after the super constructor.
        // The engine's DEFAULT heuristic places them after the *last* constructor invocation in the
        // target ctor; when a ctor creates instances of its own class that call sits inside a loop,
        // so the initialiser only runs for instances built through that loop. Measured on
        // net.minecraft.server.packs.repository.Pack (its 5-arg ctor builds child Packs):
        // parentsPredicate stayed null for a Pack without children and Fabric API's
        // fabric$parentsEnabled died with a NullPointerException at client init. SAFE mode injects
        // straight after super(), which is exactly Java's own semantics.
        if (System.getProperty("mixin.initialiserInjectionMode") == null) {
            System.setProperty("mixin.initialiserInjectionMode", "safe");
        }
        MixinBootstrap.init();
        version = MixinBootstrap.VERSION;
        // Safety net for an older engine shape: raise the level if the option was ignored.
        MixinEnvironment.CompatibilityLevel required = MixinEnvironment.CompatibilityLevel.JAVA_25;
        try {
            MixinEnvironment.CompatibilityLevel current = MixinEnvironment.getCompatibilityLevel();
            if (current == null || !current.isAtLeast(required)) {
                MixinEnvironment.setCompatibilityLevel(required);
            }
        } catch (Throwable t) {
            throw new IllegalStateException("mixin: cannot raise compatibility level to JAVA_25", t);
        }
        // The service initialises in PREINIT (MixinServiceAbstract default). Move to DEFAULT so
        // configs, mixin scanning and transforms all agree on one environment.
        try {
            java.lang.reflect.Method gotoPhase = MixinEnvironment.class
                    .getDeclaredMethod("gotoPhase", MixinEnvironment.Phase.class);
            gotoPhase.setAccessible(true);
            gotoPhase.invoke(null, MixinEnvironment.Phase.DEFAULT);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("mixin: cannot enter DEFAULT phase", e);
        }
        // A plain JVM has no platform agent to detect the side; declare it explicitly.
        env().setSide("server".equalsIgnoreCase(side) ? MixinEnvironment.Side.SERVER
                : MixinEnvironment.Side.CLIENT);
        booted = true;
        net.weaver.mixin.core.Marker.emit("MIXIN VERSION engine=sponge-mixin fork=fabric"
                + " version=" + net.weaver.mixin.core.Marker.text(version)
                + " compat=" + MixinEnvironment.getCompatibilityLevel().name()
                + " max=" + required.name()
                + " java=" + Runtime.version().feature());
    }

    /** The engine's transformer is created lazily, after configs are registered. */    private static synchronized IMixinTransformer transformer() {
        if (transformer != null) {
            return transformer;
        }
        MixinEnvironment env = env();
        Object active = env.getActiveTransformer();
        if (active instanceof IMixinTransformer) {
            transformer = (IMixinTransformer) active;
            return transformer;
        }
        try {
            Class<?> type = Class.forName("org.spongepowered.asm.mixin.transformer.MixinTransformer");
            Constructor<?> ctor = type.getDeclaredConstructor();
            ctor.setAccessible(true);
            transformer = (IMixinTransformer) ctor.newInstance();
            env.setActiveTransformer(transformer);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("mixin: no IMixinTransformer available", e);
        }
        return transformer;
    }

    public static boolean isBooted() {
        return booted;
    }

    public static String version() {
        return version;
    }

    /** True when the MixinExtras bootstrap ran: {@code @WrapOperation}/{@code @Local} are usable. */
    public static boolean extrasAvailable() {
        return extrasAvailable;
    }

    public static void extrasAvailable(boolean available) {
        extrasAvailable = available;
    }

    /** Highest compatibility level this service accepts, as a {@code JAVA_<n>} name. */
    public static String maxCompatibilityLevel() {
        return "JAVA_25";
    }

    /** Current environment level name, or {@code unknown} before boot. */
    public static String currentCompatibilityLevel() {
        try {
            return MixinEnvironment.getCompatibilityLevel().name();
        } catch (Throwable t) {
            return "unknown";
        }
    }

    /** True when the engine still holds this config (selected or pending). */
    public static boolean hasConfig(String configName) {
        try {
            Object processor = processor();
            for (String field : new String[]{"configs", "pendingConfigs"}) {
                java.util.List<Object> list = configs(processor, field);
                if (list == null) {
                    continue;
                }
                for (Object config : list) {
                    java.lang.reflect.Method getName = config.getClass().getDeclaredMethod("getName");
                    getName.setAccessible(true);
                    if (configName.equals(getName.invoke(config))) {
                        return true;
                    }
                }
            }
            for (org.spongepowered.asm.mixin.transformer.Config config : Mixins.getConfigs()) {
                if (configName.equals(config.getName())) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
            // an unknown engine shape cannot answer; report "not held" and let detach decide
        }
        return false;
    }

    /** Reads the engine version without booting it (safe from premain). */
    public static String detectVersion() {
        try {
            return (String) Class.forName("org.spongepowered.asm.launch.MixinBootstrap")
                    .getField("VERSION").get(null);
        } catch (ReflectiveOperationException e) {
            return "unknown";
        }
    }

    public static MixinEnvironment env() {
        return MixinEnvironment.getDefaultEnvironment();
    }

    /**
     * Registers a mixin config against the DEFAULT environment.
     *
     * MixinEnvironment#addConfiguration is deprecated and Mixins#addConfiguration(String) passes a
     * null environment (NPE inside MixinConfig.onLoad in 0.8.7), so the package-private
     * Mixins#addConfiguration(String, MixinEnvironment) is used.
     */
    public static void addConfig(String resourceName) {
        try {
            Method add = Mixins.class.getDeclaredMethod("addConfiguration", String.class, MixinEnvironment.class);
            add.setAccessible(true);
            add.invoke(null, resourceName, env());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("mixin: cannot register config " + resourceName, e);
        }
    }

    public static int configCount() {
        return env().getMixinConfigs().size();
    }

    /**
     * Forces the engine to select and prepare registered configs before the first real
     * transform, so a load-time measurement does not absorb one-off preparation cost.
     */
    public static void prepare() {
        try {
            byte[] probe = WeaverBytecodeProvider.readClassBytes(WeaverContainerHandle.class.getName());
            if (probe != null) {
                transformClass(WeaverContainerHandle.class.getName().replace('.', '/'), probe);
            }
        } catch (Exception e) {
            throw new IllegalStateException("mixin: prepare failed", e);
        }
    }

    /** Real transform of one class through the Mixin pipeline (our loader's transformClass hook). */
    public static byte[] transformClass(String internalName, byte[] bytes) {
        if (!booted) {
            throw new IllegalStateException("mixin: boot() not called");
        }
        // The engine keys its target map by binary (dotted) name: the ModLauncher path always
        // hands it a dotted name while the ClassNode inside keeps the internal name.
        String dotted = internalName.replace('/', '.');
        return transformer().transformClassBytes(dotted, dotted, bytes);
    }

    /** Backwards-compatible alias used by the first harness. */
    public static byte[] transform(String internalName, byte[] bytes) {
        return transformClass(internalName, bytes);
    }

    /** Our loader's findClass hook: resolve a mixin/config class without loading it twice. */
    public static Class<?> findClass(String name, boolean initialize) throws ClassNotFoundException {
        return new WeaverMixinService.WeaverClassProvider().findClass(name, initialize);
    }

    /** Our loader's ASM supply: the ClassNode for a class, read from our bytecode provider. */
    public static ClassNode asmNode(String name) throws Exception {
        return new WeaverBytecodeProvider().getClassNode(name);
    }

    public static void setInstrumentation(Instrumentation inst) {
        instrumentation = inst;
    }

    /** Target (internal name) to the synthetic classes waiting for it to be defined. */
    private static final java.util.Map<String, java.util.List<String>> PENDING_SYNTHETICS =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Queues the synthetic classes Mixin created for one target (a mixin's anonymous
     * or local classes, copied as {@code Target$Anonymous$id}).
     *
     * <p>The target is still being defined when this runs, and the classes must be
     * defined next to it, from its own protection domain (a signed game package
     * refuses a class from anywhere else). {@link #flushPendingSynthetics()} defines
     * them as soon as the target exists, through a lookup on it. Without this, a mod
     * mixin with an anonymous class fails with NoClassDefFoundError the first time
     * that code runs.
     *
     * @param targetInternal the target's internal name
     * @param loader         the loader defining the target
     * @return the number of classes queued
     */
    public static int publishTargetSynthetics(String targetInternal, ClassLoader loader) {
        if (!booted || targetInternal == null || instrumentation == null) {
            return 0;
        }
        String prefix = targetInternal + "$";
        java.util.List<String> names = new java.util.ArrayList<>();
        for (String internal : allSynthetics()) {
            if (internal.startsWith(prefix) && !isGeneratedDefined(internal)) {
                names.add(internal);
            }
        }
        if (names.isEmpty()) {
            return 0;
        }
        PENDING_SYNTHETICS.merge(targetInternal, names, (left, right) -> {
            java.util.List<String> merged = new java.util.ArrayList<>(left);
            merged.addAll(right);
            return merged;
        });
        return names.size();
    }

    /**
     * Defines queued synthetic classes whose target is now defined. Called for every
     * class the transformer sees, so it runs right after the target's own definition;
     * costs nothing while nothing is queued.
     */
    public static void flushPendingSynthetics() {
        if (PENDING_SYNTHETICS.isEmpty()) {
            return;
        }
        Instrumentation inst = instrumentation;
        if (inst == null) {
            return;
        }
        try {
            for (Class<?> loaded : inst.getAllLoadedClasses()) {
                if (PENDING_SYNTHETICS.isEmpty()) {
                    return;
                }
                java.util.List<String> names = PENDING_SYNTHETICS.remove(loaded.getName().replace('.', '/'));
                if (names == null) {
                    continue;
                }
                for (String internal : names) {
                    if (defineGenerated(internal.replace('/', '.'), loaded) != null) {
                        net.weaver.mixin.core.Marker.emit("MIXIN SYNTHETIC DEFINED class="
                                + internal.replace('/', '.') + " host=" + loaded.getName());
                    }
                }
            }
        } catch (Throwable failure) {
            net.weaver.mixin.core.Marker.emit("MIXIN SYNTHETIC FLUSH ok=false reason="
                    + net.weaver.mixin.core.Marker.oneLine(failure));
        }
    }

    /** Generates a Mixin synthetic/inner class by its internal name. */
    public static byte[] generateClass(String internalName) {
        try {
            byte[] bytes = transformer().generateClass(env(), internalName.replace('/', '.'));
            if (bytes == null) {
                net.weaver.mixin.core.Marker.emit("MIXIN SYNTHETIC GEN ok=false class="
                        + internalName.replace('/', '.') + " reason=generator-returned-null");
            }
            return bytes;
        } catch (Throwable t) {
            net.weaver.mixin.core.Marker.emit("MIXIN SYNTHETIC GEN ok=false class="
                    + internalName.replace('/', '.') + " reason="
                    + net.weaver.mixin.core.Marker.oneLine(t));
            return null;
        }
    }

    /**
     * Synthetic inner-class names registered by the engine, grouped by their outer class
     * (both internal form). The registry only grows while configs are prepared, so the cache is
     * rebuilt when its size changes. Synchronized to keep one map snapshot consistent.
     */
    private static volatile Map<String, java.util.List<String>> syntheticsByOuter = Map.of();
    private static volatile int syntheticStamp = -1;
    /** Synthetics retired by a config removal; never defined, even if the engine still lists them. */
    private static final Set<String> RETIRED_SYNTHETICS = ConcurrentHashMap.newKeySet();

    /** Every synthetic class name the engine currently registers (internal form). */
    public static Set<String> synthetics() {
        Set<String> out = new java.util.LinkedHashSet<>();
        for (java.util.List<String> names : syntheticsByOuter().values()) {
            out.addAll(names);
        }
        return out;
    }

    /**
     * Retires one synthetic class: a config that owned it is gone, so it must never be defined
     * into the running game. Already-defined classes keep their bytes (the JVM has no unload) and
     * the caller reports them as orphans.
     */
    public static boolean retireSynthetic(String internalName) {
        String internal = internalName.replace('.', '/');
        boolean added = RETIRED_SYNTHETICS.add(internal);
        if (added) {
            SYNTHETIC_DEFINED.remove(internal);
            SYNTHETIC_CLASSES.remove(internal);
            net.weaver.mixin.core.Marker.emit("MIXIN SYNTHETIC RETIRE class=" + internal
                    + " action=never-define");
        }
        return added;
    }

    public static boolean isRetiredSynthetic(String internalName) {
        return RETIRED_SYNTHETICS.contains(internalName.replace('.', '/'));
    }

    /** True when this generated class was already defined by the engine. */
    public static boolean isGeneratedDefined(String internalName) {
        return SYNTHETIC_CLASSES.containsKey(internalName.replace('.', '/'));
    }

    private static Map<String, java.util.List<String>> syntheticsByOuter() {
        try {
            Object tx = transformer();
            Field registryField = tx.getClass().getDeclaredField("syntheticClassRegistry");
            registryField.setAccessible(true);
            Object registry = registryField.get(tx);
            Field classesField = registry.getClass().getDeclaredField("classes");
            classesField.setAccessible(true);
            Object value = classesField.get(registry);
            if (!(value instanceof Map<?, ?> classes)) {
                return Map.of();
            }
            if (classes.size() != syntheticStamp) {
                Map<String, java.util.List<String>> rebuilt = new java.util.HashMap<>();
                Class<?> infoType = Class.forName("org.spongepowered.asm.service.ISyntheticClassInfo");
                Method getName = infoType.getMethod("getName");
                for (Object info : classes.values()) {
                    String internal = String.valueOf(getName.invoke(info)).replace('.', '/');
                    // Every generated class is named "<outer>$…$<hash>": the outer is the prefix
                    // before the first '$', whether Mixin calls the inner "$Anonymous$<hash>" or
                    // "$1SomeName$<hash>" (an anonymous class declared inside the mixin, measured:
                    // CreativeModeTabs$1CreativeModeTabPosition$0e98f…). Indexing only the first
                    // shape left the second undefined and the target died with NoClassDefFoundError
                    // the moment the code path ran.
                    int marker = internal.indexOf('$');
                    if (marker <= 0) {
                        continue;
                    }
                    rebuilt.computeIfAbsent(internal.substring(0, marker),
                            key -> new java.util.ArrayList<>()).add(internal);
                }
                for (java.util.List<String> names : rebuilt.values()) {
                    names.sort(String::compareTo);
                }
                syntheticsByOuter = rebuilt;
                syntheticStamp = classes.size();
            }
            return syntheticsByOuter;
        } catch (Throwable t) {
            net.weaver.mixin.core.Marker.emit("MIXIN SYNTHETIC INDEX FAIL reason="
                    + net.weaver.mixin.core.Marker.oneLine(t));
            return Map.of();
        }
    }

    /**
     * Defines the synthetic inner classes of one target that the loader just defined. A synthetic
     * is a nestmate of the outer class, so it is defined through a lookup on that class; this also
     * guarantees the bytes land in the outer's defining loader. The outer is never loaded here.
     */
    public static void defineSyntheticsFor(Class<?> outer) {
        if (outer == null || !booted) {
            return;
        }
        String outerInternal = outer.getName().replace('.', '/');
        java.util.List<String> names = syntheticsByOuter().get(outerInternal);
        if (names == null || names.isEmpty()) {
            return;
        }
        for (String internal : names) {
            defineSyntheticFor(outer, internal);
        }
    }

    /** Defines one synthetic class in the loader/nest of {@code outer}; idempotent. */
    public static Class<?> defineSyntheticFor(Class<?> outer, String internalName) {
        String internal = internalName.replace('.', '/');
        if (outer == null || !booted) {
            return null;
        }
        Class<?> existing = SYNTHETIC_CLASSES.get(internal);
        if (existing != null) {
            return existing;
        }
        if (isRetiredSynthetic(internal)) {
            return null;
        }
        synchronized (SYNTHETIC_DEFINED) {
            existing = SYNTHETIC_CLASSES.get(internal);
            if (existing != null) {
                return existing;
            }
            if (!SYNTHETIC_DEFINED.add(internal)) {
                return null;
            }
        }
        try {
            byte[] bytes = generateClass(internal);
            if (bytes == null) {
                SYNTHETIC_DEFINED.remove(internal);
                return null;
            }
            java.lang.invoke.MethodHandles.Lookup lookup =
                    java.lang.invoke.MethodHandles.privateLookupIn(outer,
                            java.lang.invoke.MethodHandles.lookup());
            Class<?> defined = lookup.defineClass(bytes);
            SYNTHETIC_CLASSES.put(internal, defined);
            net.weaver.mixin.core.Marker.emit("MIXIN SYNTHETIC ok=true class=" + internal
                    + " bytes=" + bytes.length + " loader=" + loaderName(outer.getClassLoader())
                    + " mode=post-define nest=true");
            return defined;
        } catch (Throwable t) {
            SYNTHETIC_DEFINED.remove(internal);
            net.weaver.mixin.core.Marker.emit("MIXIN SYNTHETIC FAIL class=" + internal
                    + " reason=" + net.weaver.mixin.core.Marker.oneLine(t));
            return null;
        }
    }

    /** Every synthetic class name the engine registers (internal form), anonymous or not. */
    public static Set<String> allSynthetics() {
        Set<String> out = new java.util.LinkedHashSet<>();
        try {
            Object tx = transformer();
            Field registryField = tx.getClass().getDeclaredField("syntheticClassRegistry");
            registryField.setAccessible(true);
            Object registry = registryField.get(tx);
            Field classesField = registry.getClass().getDeclaredField("classes");
            classesField.setAccessible(true);
            Object value = classesField.get(registry);
            if (!(value instanceof Map<?, ?> classes)) {
                return out;
            }
            Class<?> infoType = Class.forName("org.spongepowered.asm.service.ISyntheticClassInfo");
            Method getName = infoType.getMethod("getName");
            for (Object info : classes.values()) {
                out.add(String.valueOf(getName.invoke(info)).replace('.', '/'));
            }
        } catch (Throwable t) {
            net.weaver.mixin.core.Marker.emit("MIXIN SYNTHETIC INDEX FAIL reason="
                    + net.weaver.mixin.core.Marker.oneLine(t));
        }
        return out;
    }

    /** Defines Mixin's own generated helpers only (no caller loader known yet). */
    public static int defineEagerSynthetics() {
        return defineEagerSynthetics(null);
    }

    /**
     * Defines the generated classes that can be defined <em>correctly</em> right now.
     *
     * <p>Two families, two rules:
     * <ul>
     *   <li>Mixin's own helpers ({@code org/spongepowered/asm/synthetic/**}, e.g. {@code Args$N}
     *       from {@code @ModifyArgs}) live in a package with no real host class, so the package's
     *       definer class is used.</li>
     *   <li>A synthetic of a mixin target ({@code Target$Anonymous$N}) is only defined when the
     *       caller names the loader that is defining the target <em>and</em> the outer class
     *       resolves in exactly that loader. Defining it anywhere else creates a second copy in the
     *       wrong loader: the woven target then fails to link while the engine believes the
     *       synthetic exists (measured: AppleSkin's
     *       {@code ChiseledBookShelfBlockEntity$Anonymous$…} crash).</li>
     * </ul>
     */
    public static int defineEagerSynthetics(ClassLoader targetLoader) {
        if (!booted) {
            return 0;
        }
        int defined = 0;
        int skipped = 0;
        StringBuilder names = new StringBuilder();
        for (String internal : allSynthetics()) {
            int marker = internal.lastIndexOf('$');
            if (marker <= 0) {
                continue;
            }
            String outer = internal.substring(0, marker);
            boolean helper = outer.startsWith("org/spongepowered/asm/synthetic");
            if (!helper) {
                if (targetLoader == null) {
                    continue;
                }
                Class<?> host;
                try {
                    host = Class.forName(outer.replace('/', '.'), false, targetLoader);
                } catch (Throwable notDefinedHere) {
                    // The target is not defined in this loader (or not yet): the host's post-define
                    // hook owns it. Never guess another loader.
                    skipped++;
                    continue;
                }
                if (isGeneratedDefined(internal)) {
                    continue;
                }
                if (defineGenerated(internal.replace('/', '.'), host) != null) {
                    if (defined > 0) {
                        names.append(',');
                    }
                    names.append(internal);
                    defined++;
                }
                continue;
            }
            if (isGeneratedDefined(internal)) {
                continue;
            }
            if (defineGenerated(internal.replace('/', '.')) != null) {
                if (defined > 0) {
                    names.append(',');
                }
                names.append(internal);
                defined++;
            }
        }
        if (defined > 0) {
            net.weaver.mixin.core.Marker.emit("MIXIN SYNTHETIC EAGER ok=true defined=" + defined
                    + " names=" + names);
        }
        if (skipped > 0) {
            net.weaver.mixin.core.Marker.emit("MIXIN SYNTHETIC EAGER SKIP skipped=" + skipped
                    + " reason=host-not-defined-in-loader action=host-post-define-hook");
        }
        return defined;
    }

    /** True when this dotted class has generated synthetics waiting for its definition. */
    public static boolean hasSyntheticsFor(String dottedName) {
        if (!booted) {
            return false;
        }
        return syntheticsByOuter().containsKey(dottedName.replace('.', '/'));
    }

    /**
     * Defines one Mixin-generated class on demand ({@code @ModifyArgs} produces
     * {@code org/spongepowered/asm/synthetic/args/Args$N}).
     *
     * <p>ModLauncher defines those classes for its platform; a plain agent or our own loader has to
     * do it, otherwise every {@code @ModifyArgs} target dies with {@code NoClassDefFoundError}. The
     * class is defined in the nest of its outer class, which is what Mixin's generator assumes.
     */
    public static Class<?> defineGenerated(String dottedName) {
        return defineGenerated(dottedName, null);
    }

    /**
     * Same, with an explicit host class: the caller already resolved the outer class in the loader
     * that owns it, so the synthetic lands next to the class that will link against it.
     */
    public static Class<?> defineGenerated(String dottedName, Class<?> host) {
        if (!booted || dottedName == null || dottedName.indexOf('$') < 0) {
            return null;
        }
        String internal = dottedName.replace('.', '/');
        Class<?> existing = SYNTHETIC_CLASSES.get(internal);
        if (existing != null) {
            return existing;
        }
        synchronized (SYNTHETIC_DEFINED) {
            existing = SYNTHETIC_CLASSES.get(internal);
            if (existing != null) {
                return existing;
            }
            if (!SYNTHETIC_DEFINED.add(internal)) {
                return null;
            }
        }
        try {
            byte[] bytes = generateClass(internal);
            if (bytes == null) {
                SYNTHETIC_DEFINED.remove(internal);
                return null;
            }
            Class<?> defined;
            if (internal.startsWith("org/spongepowered/asm/synthetic/")) {
                // Mixin's own helpers live in a synthetic package with no real outer class; the
                // definer exists for exactly that package (see its javadoc).
                Class<?> definer = Class.forName(
                        "org.spongepowered.asm.synthetic.args.WeaverSyntheticDefiner", true,
                        WeaverMixinService.class.getClassLoader());
                defined = (Class<?>) definer.getMethod("define", byte[].class)
                        .invoke(null, (Object) bytes);
            } else {
                Class<?> owner = host;
                if (owner == null) {
                    String outer = internal.substring(0, internal.lastIndexOf('$'));
                    owner = Class.forName(outer.replace('/', '.'), false,
                            WeaverMixinService.class.getClassLoader());
                }
                java.lang.invoke.MethodHandles.Lookup lookup =
                        java.lang.invoke.MethodHandles.privateLookupIn(owner,
                                java.lang.invoke.MethodHandles.lookup());
                defined = lookup.defineClass(bytes);
            }
            SYNTHETIC_CLASSES.put(internal, defined);
            net.weaver.mixin.core.Marker.emit("MIXIN SYNTHETIC ok=true class=" + internal
                    + " bytes=" + bytes.length + " mode=generated-on-demand nest=true");
            return defined;
        } catch (Throwable t) {
            SYNTHETIC_DEFINED.remove(internal);
            net.weaver.mixin.core.Marker.emit("MIXIN SYNTHETIC FAIL class=" + internal
                    + " reason=" + net.weaver.mixin.core.Marker.oneLine(t)
                    + " action=class-not-defined");
            return null;
        }
    }

    /**
     * Explicit sweep used by the in-game host after the platform classes are already defined.
     * Only classes that are already loaded are considered; this method never defines a target
     * just to find its synthetics (that would load it before its mixins can be applied).
     */
    public static void defineSynthetics(ClassLoader loader) {
        if (!booted || loader == null) {
            return;
        }
        try {
            for (Map.Entry<String, java.util.List<String>> entry : syntheticsByOuter().entrySet()) {
                String dotted = entry.getKey().replace('/', '.');
                if (!net.weaver.mixin.core.MixinEngine.get().isLoaded(dotted)) {
                    continue;
                }
                Class<?> outer;
                try {
                    outer = Class.forName(dotted, false, loader);
                } catch (Throwable missing) {
                    continue;
                }
                for (String internal : entry.getValue()) {
                    defineSyntheticFor(outer, internal);
                }
            }
        } catch (Throwable t) {
            net.weaver.mixin.core.Marker.emit("MIXIN SYNTHETIC FAIL reason="
                    + net.weaver.mixin.core.Marker.oneLine(t));
        }
    }

    private static String loaderName(ClassLoader loader) {
        if (loader == null) {
            return "bootstrap";
        }
        String name = loader.getName();
        return name == null || name.isBlank() ? loader.getClass().getSimpleName() : name;
    }

    public static void markDefined(String internalName) {
        WeaverMixinService.markDefined(internalName);
    }

    private static Object processor() {
        Object transformer = transformer();
        try {
            java.lang.reflect.Field field = transformer.getClass().getDeclaredField("processor");
            field.setAccessible(true);
            return field.get(transformer);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("mixin: processor unavailable", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static java.util.List<Object> configs(Object processor, String fieldName) {
        try {
            java.lang.reflect.Field field = processor.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(processor);
            return value instanceof java.util.List ? (java.util.List<Object>) value : null;
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    /**
     * Forces config selection after a live {@code addConfiguration}.
     *
     * <p>0.8.7 only re-selects pending configs while no mixin has been applied yet
     * ({@code MixinProcessor.checkSelect}); the production engine needs an armed config to reach
     * classes defined after the boot, so it calls the same private {@code select} the engine calls
     * itself. Already-prepared configs are untouched (selection only visits new configs).
     */
    public static synchronized void forceSelect() {
        Object processor = processor();
        try {
            java.lang.reflect.Method select = processor.getClass()
                    .getDeclaredMethod("select", MixinEnvironment.class);
            select.setAccessible(true);
            select.invoke(processor, env());
        } catch (ReflectiveOperationException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IllegalStateException("mixin: force-select failed: " + cause, cause);
        }
    }

    /**
     * Clears the engine's process-wide error latch.
     *
     * <p>0.8.7 sets {@code MixinProcessor.errorState} on any failed apply and then refuses every
     * later class. The Weaver contract is per-config fail-safe, so the engine resets the latch
     * after returning original bytes; the failing config stays registered for its other targets.
     */
    public static synchronized void resetErrorState() {
        Object processor = processor();
        try {
            java.lang.reflect.Field field = processor.getClass().getDeclaredField("errorState");
            field.setAccessible(true);
            field.setBoolean(processor, false);
        } catch (ReflectiveOperationException ignored) {
            // a future engine shape without the latch needs no reset
        }
    }

    /** Target names of one prepared config, dotted, or an empty set when it is not selected. */
    public static java.util.Set<String> selectedTargets(String configName) {
        Object processor = processor();
        for (Object config : configs(processor, "configs")) {
            try {
                java.lang.reflect.Method getName = config.getClass().getDeclaredMethod("getName");
                getName.setAccessible(true);
                if (!configName.equals(getName.invoke(config))) {
                    continue;
                }
                java.lang.reflect.Method getTargets = config.getClass().getDeclaredMethod("getTargets");
                getTargets.setAccessible(true);
                Object value = getTargets.invoke(config);
                java.util.Set<String> out = new java.util.LinkedHashSet<String>();
                if (value instanceof java.util.Collection<?> collection) {
                    for (Object target : collection) {
                        out.add(String.valueOf(target).replace('/', '.'));
                    }
                }
                return out;
            } catch (ReflectiveOperationException ignored) {
                // keep scanning: a foreign config shape must not break the engine
            }
        }
        return java.util.Collections.emptySet();
    }

    /** Number of mixin classes in one prepared config, or 0 when it is not selected. */
    /**
     * The mixin classes Mixin actually prepared for a config, or {@code null} when that cannot
     * be read. Mixin drops a mixin it cannot prepare (for example a remappable shadow in a
     * multi-target mixin) without failing the config, so this can be smaller than the declared list.
     */
    public static java.util.Set<String> preparedMixinNames(String configName) {
        Object processor = processor();
        for (Object config : configs(processor, "configs")) {
            try {
                java.lang.reflect.Method getName = config.getClass().getDeclaredMethod("getName");
                getName.setAccessible(true);
                if (!configName.equals(getName.invoke(config))) {
                    continue;
                }
                java.lang.reflect.Field field = config.getClass().getDeclaredField("mixins");
                field.setAccessible(true);
                Object value = field.get(config);
                if (!(value instanceof java.util.Collection<?> prepared)) {
                    return null;
                }
                java.util.Set<String> names = new java.util.LinkedHashSet<>();
                for (Object info : prepared) {
                    java.lang.reflect.Method getClassName = info.getClass().getMethod("getClassName");
                    getClassName.setAccessible(true);
                    names.add(String.valueOf(getClassName.invoke(info)).replace('/', '.'));
                }
                return names;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    /** Drops Mixin's cached metadata of one class, so a retried transform starts clean. */
    public static void forgetClassInfo(String internalName) {
        try {
            Class<?> type = Class.forName("org.spongepowered.asm.mixin.transformer.ClassInfo");
            java.lang.reflect.Field field = type.getDeclaredField("cache");
            field.setAccessible(true);
            Object cache = field.get(null);
            if (cache instanceof java.util.Map<?, ?> map) {
                map.remove(internalName);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // best effort: the retry still runs
        }
    }

    public static int selectedMixinCount(String configName) {
        Object processor = processor();
        for (Object config : configs(processor, "configs")) {
            try {
                java.lang.reflect.Method getName = config.getClass().getDeclaredMethod("getName");
                getName.setAccessible(true);
                if (!configName.equals(getName.invoke(config))) {
                    continue;
                }
                java.lang.reflect.Method getClasses = config.getClass().getDeclaredMethod("getClasses");
                getClasses.setAccessible(true);
                Object value = getClasses.invoke(config);
                return value instanceof java.util.Collection<?> collection ? collection.size() : 0;
            } catch (ReflectiveOperationException ignored) {
                // keep scanning
            }
        }
        return 0;
    }

    /**
     * Removes one target from one prepared config and leaves that config armed for every other
     * target.
     *
     * <p>Mixin resolves a class against {@code MixinConfig.mixinMapping} (target -&gt; mixins);
     * deleting one key makes the config contribute nothing to that class only. This is the narrow
     * form of {@link #detachConfig(String)}: measured, detaching the whole config because a single
     * class failed removed fabric-item-api-v1 from the engine, so {@code Item} — defined later —
     * never received {@code FabricItem} and every call to
     * {@code Item.allowComponentsUpdateAnimation} died with {@code NoSuchMethodError} two seconds
     * after the world opened.
     */
    public static synchronized boolean detachTarget(String configName, String dotted) {
        Object config = selectedConfig(configName);
        if (config == null) {
            return false;
        }
        boolean removed = false;
        try {
            java.lang.reflect.Field field = config.getClass().getDeclaredField("mixinMapping");
            field.setAccessible(true);
            Object value = field.get(config);
            if (value instanceof java.util.Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                java.util.Map<Object, Object> mapping = (java.util.Map<Object, Object>) map;
                removed = mapping.remove(dotted) != null;
                if (!removed) {
                    removed = mapping.remove(dotted.replace('.', '/')) != null;
                }
            }
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
        try {
            java.lang.reflect.Field field = config.getClass().getDeclaredField("unhandledTargets");
            field.setAccessible(true);
            Object value = field.get(config);
            if (value instanceof java.util.Set<?> set) {
                @SuppressWarnings("unchecked")
                java.util.Set<Object> unhandled = (java.util.Set<Object>) set;
                unhandled.remove(dotted);
                unhandled.remove(dotted.replace('.', '/'));
            }
        } catch (ReflectiveOperationException ignored) {
            // warning bookkeeping only
        }
        return removed;
    }

    /** Selected or pending config object by its registered name, or {@code null}. */
    private static Object selectedConfig(String configName) {
        Object processor = processor();
        for (String field : new String[]{"configs", "pendingConfigs"}) {
            java.util.List<Object> list = configs(processor, field);
            if (list == null) {
                continue;
            }
            synchronized (list) {
                for (Object config : list) {
                    try {
                        java.lang.reflect.Method getName = config.getClass().getDeclaredMethod("getName");
                        getName.setAccessible(true);
                        if (configName.equals(getName.invoke(config))) {
                            return config;
                        }
                    } catch (ReflectiveOperationException ignored) {
                        // unknown config type: leave it alone
                    }
                }
            }
        }
        return null;
    }

    /**
     * Removes one config from the engine (selected and pending lists) and from the global registry
     * so no class defined later can be woven by it. Loaded classes keep their bytes; the caller
     * reports that as {@code MIXIN ORPHAN}.
     */
    public static synchronized boolean detachConfig(String configName) {
        boolean removed = false;
        Object processor = processor();
        for (String field : new String[]{"configs", "pendingConfigs"}) {
            java.util.List<Object> list = configs(processor, field);
            if (list == null) {
                continue;
            }
            synchronized (list) {
                java.util.Iterator<Object> iterator = list.iterator();
                while (iterator.hasNext()) {
                    Object config = iterator.next();
                    try {
                        java.lang.reflect.Method getName = config.getClass().getDeclaredMethod("getName");
                        getName.setAccessible(true);
                        if (configName.equals(getName.invoke(config))) {
                            iterator.remove();
                            removed = true;
                        }
                    } catch (ReflectiveOperationException ignored) {
                        // unknown config type: leave it alone
                    }
                }
            }
        }
        java.util.Iterator<org.spongepowered.asm.mixin.transformer.Config> globals =
                Mixins.getConfigs().iterator();
        while (globals.hasNext()) {
            org.spongepowered.asm.mixin.transformer.Config config = globals.next();
            if (configName.equals(config.getName())) {
                globals.remove();
                removed = true;
            }
        }
        return removed;
    }
}
