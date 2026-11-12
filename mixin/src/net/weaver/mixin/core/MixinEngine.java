package net.weaver.mixin.core;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.gson.JsonObject;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import net.weaver.api.WeaverProblems;
import net.weaver.mixin.MixinBridge;
import net.weaver.mixin.MixinRuntime;
import net.weaver.mixin.MixinStats;
import net.weaver.mixin.WeaverMarkerLogger;
import net.weaver.mixin.WeaverMixinService;
import net.weaver.mixin.spi.MappingService;

/**
 * Production Mixin service: one engine, many mods.
 *
 * <p>Responsibilities kept here: config registration scoped to a mod loader, target indexing,
 * refmap resolution, parent-config linking, applied/orphan bookkeeping, statistics and measured
 * timings. The transform itself is delegated to upstream Mixin 0.8.7 through {@link MixinBridge}.
 *
 * <p>{@link #addConfig} either arms the config completely or rejects it with a marker; a partially
 * armed config is never left in the engine (rollback + resource release).
 */
public final class MixinEngine implements MixinRuntime {

    /** Loaded-class oracle; the agent supplies {@code Instrumentation}, tests supply their own. */
    public interface LoadedProbe {
        boolean isLoaded(String dottedClassName);
    }

    private static final MixinEngine INSTANCE = new MixinEngine();

    /**
     * How many configs one class transform may shed before giving up. A class is normally owned by
     * one or two configs; the cap keeps a pathological setup from turning every load into a loop.
     */
    private static final int MAX_ISOLATIONS = Integer.getInteger("weaver.mixin.maxIsolations", 4);

    private final ResourceRegistry resources = new ResourceRegistry();
    private final Map<String, Registration> configs = new LinkedHashMap<>();
    private final Map<String, Set<String>> targetIndex = new LinkedHashMap<>();
    private final Map<String, Integer> classRefs = new LinkedHashMap<>();
    /** Synthetic name of an inlined parent config -> number of children that reference it. */
    private final Map<String, Integer> inlinedParents = new LinkedHashMap<>();
    /** Bytes of each inlined parent config, reused when a later child links to it. */
    private final Map<String, byte[]> inlinedParentBytes = new LinkedHashMap<>();
    private final Set<String> observed = ConcurrentHashMap.newKeySet();
    private final Set<String> appliedTargets = ConcurrentHashMap.newKeySet();
    private final Map<String, Integer> sitesByMod = new ConcurrentHashMap<>();
    private final Set<String> transformedClasses = ConcurrentHashMap.newKeySet();
    private final Map<String, Integer> failuresByConfig = new ConcurrentHashMap<>();
    /** config+target pairs already narrowed out of one class (see {@link #isolate}). */
    private final Set<String> isolatedTargets = ConcurrentHashMap.newKeySet();
    private final List<Double> addConfigMs = Collections.synchronizedList(new ArrayList<Double>());
    private final List<Double> transformMs = Collections.synchronizedList(new ArrayList<Double>());
    private final AtomicInteger failures = new AtomicInteger();
    private final ThreadLocal<String> transforming = new ThreadLocal<String>();

    /**
     * Lock-free hot path: the transformer asks "is this class interesting?" for every class the JVM
     * defines. Both sets are immutable snapshots rebuilt whenever the config set changes.
     */
    private volatile Set<String> targetNames = Set.of();
    private volatile Set<String> mixinNames = Set.of();

    private volatile boolean booted;
    private volatile double bootMs;
    private volatile ClassLoader gameLoader;
    private volatile LoadedProbe probe = name -> false;
    private volatile MappingService mappingService;
    private volatile String side = "client";
    /**
     * Class names a run asked to trace ({@code -Dweaver.mixin.traceTargets=a,b}). A traced class
     * reports the first time each loader hands it to the transformer, whether the engine considers
     * it interesting and which loader defined it. It is the only way to tell "the config never
     * indexed this target" apart from "the transformer never saw the definition"; both look like a
     * silent skip in the {@code MIXIN CONFIG} marker alone.
     */
    private final Set<String> traceTargets = parseTrace(System.getProperty("weaver.mixin.traceTargets"));
    private final Set<String> tracedSeen = ConcurrentHashMap.newKeySet();

    private MixinEngine() {
    }

    public static MixinEngine get() {
        return INSTANCE;
    }

    public ResourceRegistry resources() {
        return this.resources;
    }

    public void setLoadedProbe(LoadedProbe probe) {
        this.probe = probe == null ? name -> false : probe;
    }

    public void setMappingService(MappingService service) {
        this.mappingService = service;
    }

    public void side(String side) {
        this.side = side == null ? "client" : side;
    }

    public String side() {
        return this.side;
    }

    @Override
    public synchronized void boot(ClassLoader gameLoader) {
        if (this.booted) {
            return;
        }
        long t0 = System.nanoTime();
        this.gameLoader = gameLoader;
        this.resources.gameLoader(gameLoader);
        MixinBridge.boot(this.side);
        // The transformer is created lazily; MixinExtras registers its extensions into it and must
        // see an active transformer, so force its creation before the first config is prepared.
        try {
            MixinBridge.forceSelect();
        } catch (Throwable ignored) {
            // no configs yet: prepare() below still creates the transformer on first use
        }
        initExtras();
        this.booted = true;
        this.bootMs = (System.nanoTime() - t0) / 1_000_000.0;
        Marker.emit("MIXIN SERVICE ok=true impl=net.weaver.mixin.core.MixinEngine version="
                + Marker.text(MixinBridge.version()) + " side=" + this.side
                + " game-loader=" + Marker.text(loaderName(gameLoader)));
        Marker.emit("MIXIN BOOT ok=true version=" + Marker.text(MixinBridge.version())
                + " boot_ms=" + Marker.ms((long) (this.bootMs * 1_000_000))
                + " mode=load-time");
    }

    public boolean booted() {
        return this.booted;
    }

    /**
     * Fabric API modules use MixinExtras injectors. The bootstrap registers its extensions into the
     * Mixin environment and must run after the engine booted but before any config is prepared.
     * Reflection keeps the engine dependency optional for the proof harnesses.
     */
    private void initExtras() {
        try {
            Class<?> type = Class.forName("com.llamalad7.mixinextras.MixinExtrasBootstrap");
            Object version = type.getMethod("getVersion").invoke(null);
            type.getMethod("init").invoke(null);
            MixinBridge.extrasAvailable(true);
            Marker.emit("MIXIN EXTRAS ok=true version=" + Marker.text(String.valueOf(version))
                    + " mode=bootstrap");
        } catch (ClassNotFoundException e) {
            MixinBridge.extrasAvailable(false);
            Marker.emit("MIXIN EXTRAS absent action=extras-sugar-unavailable");
        } catch (Throwable t) {
            MixinBridge.extrasAvailable(false);
            Marker.emit("MIXIN EXTRAS FAIL reason=" + Marker.oneLine(t)
                    + " action=extras-sugar-unavailable");
        }
    }

    @Override
    public byte[] generateSynthetic(String internalName) {
        return MixinBridge.generateClass(internalName);
    }

    @Override
    public void defineSynthetics(ClassLoader loader) {
        MixinBridge.defineSynthetics(loader);
    }

    /**
     * Defines the synthetic inner classes generated for one already-defined target. The loader
     * calls this immediately after {@code defineClass} so the woven class never references a
     * synthetic that does not exist yet. It never defines the target itself: the target is a
     * normal class-loading decision and must pass the transfer/transform stage first.
     */
    public void defineSyntheticsFor(Class<?> target) {
        if (this.booted) {
            MixinBridge.defineSyntheticsFor(target);
        }
    }

    /** Emits the current target/mixin index (diagnostics). */
    public void emitIndex() {
        StringBuilder traced = new StringBuilder();
        int present = 0;
        for (String name : this.traceTargets) {
            boolean indexed = this.targetNames.contains(name) || this.mixinNames.contains(name);
            if (indexed) {
                present++;
            }
            if (traced.length() > 0) {
                traced.append(',');
            }
            traced.append(name).append('=').append(indexed ? "indexed" : "absent");
        }
        Marker.emit("MIXIN INDEX ok=true targets=" + this.targetNames.size()
                + " mixins=" + this.mixinNames.size()
                + (this.traceTargets.isEmpty() ? "" : " traced=" + traced + " traced-indexed=" + present));
    }

    /** True when the run asked for this class to be traced ({@code -Dweaver.mixin.traceTargets}). */
    public boolean traces(String dotted) {
        return !this.traceTargets.isEmpty() && this.traceTargets.contains(dotted);
    }

    /**
     * Emits the first sighting of one traced class per loader. Called by the transformer before its
     * interest check, so a class the engine does not know is still reported (that is the point):
     * the marker separates "never handed to the transformer" from "handed over, not a target".
     */
    public void traceSeen(String dotted, ClassLoader loader, boolean interesting) {
        if (!traces(dotted)) {
            return;
        }
        String key = dotted + '\u0000' + loaderName(loader);
        if (!this.tracedSeen.add(key)) {
            return;
        }
        Marker.emit("MIXIN TRACE target=" + dotted + " loader=" + Marker.text(loaderName(loader))
                + " interesting=" + interesting
                + " target-indexed=" + this.targetNames.contains(dotted)
                + " mixin-indexed=" + this.mixinNames.contains(dotted)
                + " configs=" + ownersOf(dotted).size()
                + " loaded=" + isLoaded(dotted));
    }

    private static Set<String> parseTrace(String value) {
        Set<String> out = new LinkedHashSet<>();
        if (value == null || value.isBlank()) {
            return out;
        }
        for (String part : value.split(",")) {
            String name = part.trim();
            if (!name.isEmpty()) {
                out.add(name);
            }
        }
        return out;
    }

    @Override
    public void addConfig(String modId, String configName, byte[] config, ClassLoader modLoader) {
        try {
            armConfig(modId, configName, config, modLoader, "add");
        } catch (VirtualMachineError fatal) {
            if (!(fatal instanceof StackOverflowError)) {
                throw fatal;
            }
            reject(modId, configName, "unexpected:" + fatal, "unexpected-error");
        } catch (Throwable unexpected) {
            // One broken config (a garbage class file, a class compiled for a newer Java, a
            // plugin that throws) must cost only that config, never the boot.
            reject(modId, configName, "unexpected:" + unexpected, "unexpected-error");
        }
    }

    /**
     * Arms one config. Never leaves a half-registered config behind: every failure path releases
     * the resources, class bindings and target index entries it created.
     */
    private synchronized boolean armConfig(String modId, String configName, byte[] config,
                                           ClassLoader modLoader, String cause) {
        long start = System.nanoTime();
        if (!this.booted) {
            reject(modId, configName, "not-booted", "not-booted");
            return false;
        }
        if (modId == null || !modId.matches("[a-z0-9_.-]+")) {
            reject(modId, configName, "invalid-mod-id", "invalid-mod-id");
            return false;
        }
        if (configName == null || configName.isBlank() || config == null || modLoader == null) {
            reject(modId, configName, "invalid-arguments", "invalid-arguments");
            return false;
        }
        String key = key(modId, configName);
        if (this.configs.containsKey(key)) {
            reject(modId, configName, "duplicate-config", "duplicate-config");
            return false;
        }
        if (this.inlinedParents.containsKey(parsedName(modId, configName))) {
            // Another config already inherited from this file: Mixin holds it as an inlined
            // parent, so arming it again would register the same mixin classes twice.
            reject(modId, configName, "parent-already-inlined", "parent-already-inlined");
            return false;
        }

        ConfigParser.Parsed parsed;
        try {
            parsed = ConfigParser.parse(modId, configName, config, modLoader, this.side);
        } catch (ConfigParser.Rejected e) {
            if ("no-mixins-declared".equals(e.getMessage())) {
                // Fabric accepts a listed but empty config (ImmediatelyFast and Xaero ship one as
                // a platform hook); it is a no-op, not a failure.
                Marker.emit("MIXIN CONFIG SKIP ok=true mod=" + modId + " config=" + configName
                        + " reason=empty-config action=skipped");
                // A config can also be empty because its IMixinConfigPlugin supplies the mixins and
                // arms the real configs (ReplayMod). Opt-in: the plugin runs here, its added configs
                // are re-registered through this engine, and the declaring config itself is never
                // registered with Mixin — registering it changed prepare order and made the
                // attachment module fail to initialise (measured).
                // Default on: this is Fabric's own semantics. Opt-out exists only so a broken
                // third-party plugin can be isolated without a rebuild.
                if (!Boolean.getBoolean("weaver.mixin.skipConfigPlugins")) {
                    runConfigPlugin(modId, configName, config, modLoader);
                }
                return false;
            }
            reject(modId, configName, e.getMessage(), e.code());
            return false;
        } catch (RuntimeException | LinkageError e) {
            // ClassReader throws unchecked exceptions on a truncated class file or one newer
            // than ASM supports.
            reject(modId, configName, "unreadable:" + e, "unreadable");
            return false;
        }

        // ---- declared metadata: fail closed before the engine ever sees the config -------------
        try {
            validateVersion(parsed);
            validateCompatibility(parsed);
            validatePlugin(parsed, modLoader);
            validateInjectors(parsed, modLoader);
        } catch (ConfigParser.Rejected e) {
            reject(modId, configName, e.getMessage(), e.code());
            return false;
        } catch (RuntimeException | LinkageError e) {
            reject(modId, configName, "unreadable:" + e, "unreadable");
            return false;
        }
        if (parsed.duplicates() > 0) {
            Marker.emit("MIXIN CONFIG WARN mod=" + modId + " config=" + configName
                    + " reason=duplicate-entries collapsed=" + parsed.duplicates());
        }
        if (parsed.sideSkipped() > 0) {
            Marker.emit("MIXIN CONFIG SIDE mod=" + modId + " config=" + configName
                    + " side=" + this.side + " skipped=" + parsed.sideSkipped()
                    + " action=side-filtered");
        }

        if (!parsed.required()) {
            Set<String> missing = new LinkedHashSet<>();
            for (String target : parsed.targets()) {
                if (this.resources.classBytes(target) == null) {
                    missing.add(target);
                }
            }
            if (!missing.isEmpty()) {
                Marker.emit("MIXIN CONFIG SKIP ok=true mod=" + modId + " config=" + configName
                        + " reason=optional-target-absent targets=" + String.join(",", missing)
                        + " action=skipped");
                return false;
            }
        }

        // ---- feature census (evidence + fail-fast for MixinExtras) ------------------------------
        MixinFeatures.Census census;
        try {
            census = MixinFeatures.scan(parsed.mixinClasses(), modLoader, parsed.pluginClass() != null);
        } catch (RuntimeException | LinkageError e) {
            reject(modId, configName, "unreadable:" + e, "unreadable");
            return false;
        }
        if (census.extrasUsed() && !MixinBridge.extrasAvailable()) {
            reject(modId, configName, "extras-missing:" + census.extrasAnnotations(),
                    "extras-missing");
            return false;
        }

        // ---- parent chain + refmap --------------------------------------------------------------
        JsonObject json = parsed.json().deepCopy();
        // Mixin reads both side sections from the buffer regardless of what we armed; the other
        // side has to disappear or a client launch would arm server mixins (and vice versa).
        if (!ConfigParser.SIDE_BOTH.equals(parsed.side())) {
            json.remove("client".equals(parsed.side()) ? "server" : "client");
        }
        List<String> parentResources = new ArrayList<>();
        String parentLink = "none";
        if (parsed.parentName() != null) {
            try {
                parentLink = bindParent(modId, configName, parsed.parentName(), modLoader,
                        parentResources, new LinkedHashSet<String>(), parsed.side());
                json.addProperty("parent", parentLink);
            } catch (ConfigParser.Rejected e) {
                releaseShared(parentResources);
                reject(modId, configName, "parent:" + e.getMessage(), "parent-" + e.code());
                return false;
            }
        }
        RefMaps.Resolved refmap = RefMaps.resolve(parsed.syntheticName(), parsed.refmapName(),
                modLoader, this.mappingService);
        if (parsed.refmapName() == null) {
            Marker.emit("MIXIN REFMAP absent mod=" + modId + " config=" + configName
                    + " action=runtime-names warning=config-declares-no-refmap");
        } else if (refmap == null) {
            json.remove("refmap");
            Marker.emit("MIXIN REFMAP missing mod=" + modId + " config=" + configName
                    + " declared=" + Marker.text(parsed.refmapName())
                    + " action=runtime-names");
        } else {
            json.addProperty("refmap", refmap.resourceName());
            this.resources.bind(refmap.resourceName(), refmap.json());
            Marker.emit("MIXIN REFMAP ok=true mod=" + modId + " config=" + configName
                    + " resource=" + refmap.resourceName() + " mappings=" + refmap.mappings()
                    + " changed=" + refmap.changed()
                    + " bound=" + (refmap.remapped() ? "mapping-service" : "identity"));
        }

        // ---- live targets: a class the JVM already defined cannot be woven ----------------------
        Set<String> liveTargets = new LinkedHashSet<>();
        Set<String> droppedMixins = new LinkedHashSet<>();
        Set<String> keptMixins = new LinkedHashSet<>();
        for (String mixin : parsed.mixinClasses()) {
            Set<String> mixinTargets = parsed.targetsByMixin().getOrDefault(mixin, Set.of());
            boolean dropped = false;
            for (String target : mixinTargets) {
                if (isLoaded(target)) {
                    liveTargets.add(target);
                    dropped = true;
                }
            }
            if (dropped) {
                droppedMixins.add(mixin);
            } else {
                keptMixins.add(mixin);
            }
        }
        if (!droppedMixins.isEmpty()) {
            removeMixins(json, droppedMixins);
        }
        for (String target : liveTargets) {
            Marker.emit("MIXIN LIVE UNSUPPORTED target=" + target + " mod=" + modId
                    + " config=" + configName + " reason=class-already-loaded"
                    + " action=excluded-from-config");
        }

        byte[] rewritten = json.toString().getBytes(StandardCharsets.UTF_8);
        if (keptMixins.isEmpty()) {
            Registration shadow = new Registration(parsed, modLoader, rewritten, config, refmap);
            shadow.targets.addAll(parsed.targets());
            shadow.liveUnsupported.addAll(liveTargets);
            shadow.extraResources.addAll(parentResources);
            this.configs.put(key, shadow);
            refreshIndexes();
            long shadowNanos = System.nanoTime() - start;
            this.addConfigMs.add(shadowNanos / 1_000_000.0);
            Marker.emit("MIXIN CONFIG ok=true mod=" + modId + " config=" + configName
                    + " classes=0 prepared=0 dropped=" + droppedMixins.size()
                    + " targets=" + parsed.targets().size()
                    + " refmap=" + refmapLabel(parsed, refmap)
                    + " parent=" + parentLink
                    + " accessors=" + parsed.accessorMethods()
                    + " invokers=" + parsed.invokerMethods()
                    + " live-unsupported=" + liveTargets.size()
                    + " action=shadow-all-targets-loaded"
                    + " loader=" + Marker.text(loaderName(modLoader))
                    + " addconfig_ms=" + Marker.ms(shadowNanos));
            Marker.emit("MIXIN FEATURES ok=true mod=" + modId + " config=" + configName
                    + " " + census.fields());
            return true;
        }

        // ---- bind the config resource and claim the mixin classes -------------------------------
        this.resources.bind(parsed.syntheticName(), rewritten);
        for (String mixin : parsed.mixinClasses()) {
            ClassLoader previous = this.resources.loaderOf(mixin);
            if (previous != null && previous != modLoader) {
                cleanupFailed(parsed, parentResources, modLoader, refmap);
                reject(modId, configName, "mixin-class-claimed-by-another-mod:" + mixin,
                        "mixin-class-claimed");
                return false;
            }
            this.resources.bindClass(mixin, modLoader);
        }
        this.resources.addModLoader(modLoader);
        Marker.emit("MIXIN CLASSLOADER ok=true mod=" + modId + " config=" + configName
                + " loader=" + Marker.text(loaderName(modLoader))
                + " classes=" + parsed.mixinClasses().size() + " source=mod-loader");

        Registration registration = new Registration(parsed, modLoader, rewritten, config, refmap);
        registration.extraResources.addAll(parentResources);
        Set<String> keptTargets = new LinkedHashSet<>();
        for (String mixin : keptMixins) {
            keptTargets.addAll(parsed.targetsByMixin().getOrDefault(mixin, Set.of()));
        }
        Set<String> transformedBefore = new LinkedHashSet<>();
        for (String target : keptTargets) {
            if (this.transformedClasses.contains(target)) {
                transformedBefore.add(target);
            }
            this.targetIndex.computeIfAbsent(target, k -> new LinkedHashSet<String>()).add(key);
        }
        refreshIndexes();

        Set<String> engineTargets;
        Set<String> syntheticsBefore = safeSynthetics();
        ClassLoader previousTccl = Thread.currentThread().getContextClassLoader();
        try {
            // Mixin plugins commonly locate platform services through the thread context loader.
            // Pointing it at the declaring mod loader keeps class identity identical to the
            // classes the mixin itself is bound to (measured: Lithium/Sodium plugin NPE without it).
            Thread.currentThread().setContextClassLoader(modLoader);
            MixinBridge.addConfig(parsed.syntheticName());
            MixinBridge.forceSelect();
            engineTargets = MixinBridge.selectedTargets(parsed.syntheticName());
        } catch (Throwable t) {
            removeTargetIndex(key, keptTargets);
            cleanupFailed(parsed, parentResources, modLoader, refmap);
            String culprit = culpritMixin(parsed, keptMixins, t);
            int round = cause.startsWith("drop-retry-") ? Integer.parseInt(cause.substring(11)) : 0;
            byte[] reduced = culprit == null || keptMixins.size() < 2 || round >= 8
                    ? null : withoutMixin(config, culprit);
            if (reduced != null) {
                // One bad mixin must not cost the mod every other mixin in the config.
                Marker.emit("MIXIN CONFIG PARTIAL mod=" + modId + " config=" + configName
                        + " dropped=" + culprit + " reason=" + Marker.oneLine(t)
                        + " action=rearm-without-it");
                WeaverProblems.record(modId, "mixin dropped: " + culprit + " (" + Marker.oneLine(t) + ")");
                bestEffortSelect();
                Thread.currentThread().setContextClassLoader(previousTccl);
                return armConfig(modId, configName, reduced, modLoader, "drop-retry-" + (round + 1));
            }
            reject(modId, configName, "prepare:" + Marker.oneLine(t), "prepare-failed");
            bestEffortSelect();
            return false;
        } finally {
            Thread.currentThread().setContextClassLoader(previousTccl);
        }

        Set<String> targets = new LinkedHashSet<>(keptTargets);
        targets.addAll(engineTargets);

        // A config the engine silently disabled (throwing plugin, unknown required feature) must
        // not look armed: it selects zero mixins while declaring some.
        int selected = safeSelectedMixinCount(parsed.syntheticName());
        if (selected == 0) {
            String reason = parsed.pluginClass() != null
                    ? "plugin-failed:" + parsed.pluginClass()
                    : "prepare-empty";
            String code = parsed.pluginClass() != null ? "plugin-failed" : "prepare-empty";
            removeTargetIndex(key, keptTargets);
            cleanupFailed(parsed, parentResources, modLoader, refmap);
            reject(modId, configName, reason, code);
            bestEffortSelect();
            return false;
        }
        Set<String> prepared = MixinBridge.preparedMixinNames(parsed.syntheticName());
        if (prepared != null) {
            Set<String> missing = new LinkedHashSet<>();
            for (String mixin : keptMixins) {
                if (!prepared.contains(mixin.replace('/', '.'))) {
                    missing.add(mixin);
                }
            }
            if (!missing.isEmpty()) {
                selected = prepared.size();
                Marker.emit("MIXIN CONFIG PARTIAL mod=" + modId + " config=" + configName
                        + " prepared=" + prepared.size() + " declared=" + keptMixins.size()
                        + " not_prepared=" + String.join(",", missing)
                        + " action=config-armed-without-them (see the engine error above)");
                WeaverProblems.record(modId, "mixins not applied: " + String.join(", ", missing));
            }
        }
        registration.synthetics.addAll(diff(safeSynthetics(), syntheticsBefore));
        try {
            MixinBridge.defineEagerSynthetics();
        } catch (Throwable t) {
            Marker.emit("MIXIN SYNTHETIC EAGER ok=false reason=" + Marker.oneLine(t));
        }
        registration.targets.addAll(targets);
        registration.liveUnsupported.addAll(liveTargets);

        Set<String> early = new LinkedHashSet<>();
        this.configs.put(key, registration);
        for (String target : targets) {
            this.targetIndex.computeIfAbsent(target, k -> new LinkedHashSet<String>()).add(key);
        }
        for (String mixin : parsed.mixinClasses()) {
            this.classRefs.merge(mixin, 1, Integer::sum);
        }
        refreshIndexes();
        for (String target : keptTargets) {
            if (!transformedBefore.contains(target) && this.transformedClasses.contains(target)) {
                early.add(target);
            }
        }
        for (String target : early) {
            recordApplied(registration, target, 1);
            Marker.emit("MIXIN APPLIED-EARLY ok=true mod=" + modId + " config=" + configName
                    + " target=" + target + " action=registered-after-prepare");
        }

        long nanos = System.nanoTime() - start;
        this.addConfigMs.add(nanos / 1_000_000.0);
        Marker.emit("MIXIN CONFIG ok=true mod=" + modId + " config=" + configName
                + " classes=" + keptMixins.size()
                + " prepared=" + selected
                + " dropped=" + droppedMixins.size()
                + " targets=" + targets.size()
                + " refmap=" + refmapLabel(parsed, refmap)
                + " parent=" + parentLink
                + " accessors=" + parsed.accessorMethods()
                + " invokers=" + parsed.invokerMethods()
                + " live-unsupported=" + liveTargets.size()
                + " side=" + this.side
                + " min_version=" + Marker.text(parsed.minVersion() == null ? "-" : parsed.minVersion())
                + " compat=" + Marker.text(parsed.compatibilityLevel() == null
                        ? MixinBridge.currentCompatibilityLevel() : parsed.compatibilityLevel())
                + " priority=" + (parsed.priority() == null ? "-" : parsed.priority())
                + " plugin=" + Marker.text(parsed.pluginClass() == null ? "-" : parsed.pluginClass())
                + " synthetics=" + registration.synthetics.size()
                + " cause=" + cause
                + " loader=" + Marker.text(loaderName(modLoader))
                + " addconfig_ms=" + Marker.ms(nanos));
        Marker.emit("MIXIN FEATURES ok=true mod=" + modId + " config=" + configName
                + " " + census.fields());
        WeaverMarkerLogger.noteConfig(modId);
        return true;
    }

    /** Plugin classes already executed, so a plugin-armed config cannot recurse forever. */
    private static final java.util.Set<String> PLUGINS_RUN = new java.util.HashSet<>();

    /**
     * Runs a config's {@code IMixinConfigPlugin} and registers the configs it adds.
     *
     * <p>Fabric runs the plugin when the config is registered, and a plugin may arm further configs
     * through {@code Mixins.addConfiguration} (ReplayMod's non-ModMenu launcher does exactly that).
     * Weaver owns the config list, so a config added straight to Mixin would never be prepared by
     * this engine: the plugin runs here and every config it added is re-registered through
     * {@link #addConfig}, keeping one list and one prepare order.
     */
    private void runConfigPlugin(String modId, String configName, byte[] config, ClassLoader modLoader) {
        String pluginName = ConfigParser.declaredPlugin(config);
        if (pluginName == null || pluginName.isBlank()) {
            return;
        }
        String key = pluginName + "@" + configName;
        synchronized (PLUGINS_RUN) {
            if (!PLUGINS_RUN.add(key)) {
                return;
            }
        }
        java.util.Set<String> before = new java.util.LinkedHashSet<>();
        for (org.spongepowered.asm.mixin.transformer.Config known : org.spongepowered.asm.mixin.Mixins.getConfigs()) {
            before.add(known.getName());
        }
        Object plugin;
        try {
            Class<?> type = Class.forName(pluginName, true, modLoader);
            plugin = type.getDeclaredConstructor().newInstance();
            try {
                type.getMethod("onLoad", String.class).invoke(plugin, configName);
            } catch (NoSuchMethodException ignored) {
                // optional in the interface
            }
            Object mixins = null;
            try {
                mixins = type.getMethod("getMixins").invoke(plugin);
            } catch (NoSuchMethodException ignored) {
                // not a config plugin after all
            }
            Marker.emit("MIXIN PLUGIN ok=true mod=" + modId + " config=" + configName
                    + " plugin=" + pluginName
                    + " mixins=" + (mixins instanceof java.util.Collection<?> c ? c.size() : "none"));
        } catch (Throwable t) {
            Throwable cause = t instanceof java.lang.reflect.InvocationTargetException ite
                    && ite.getCause() != null ? ite.getCause() : t;
            Marker.emit("MIXIN PLUGIN ok=false mod=" + modId + " config=" + configName
                    + " plugin=" + pluginName + " reason=" + Marker.text(cause.toString()));
            return;
        }
        // Snapshot first: addConfig() registers a config with Mixin, so iterating the live list
        // while adding stops after a few entries (measured: 3 of ReplayMod's 8 armed, and the
        // missing recording config left an accessor interface off IntegratedServer, which crashed
        // the client on every render frame).
        java.util.List<String> added = new java.util.ArrayList<>();
        for (org.spongepowered.asm.mixin.transformer.Config known : org.spongepowered.asm.mixin.Mixins.getConfigs()) {
            if (!before.contains(known.getName())) {
                added.add(known.getName());
            }
        }
        for (String name : added) {
            byte[] bytes = ResourceRegistry.resource(modLoader, name);
            if (bytes == null) {
                Marker.emit("MIXIN PLUGIN ADD skip=true mod=" + modId + " config=" + name
                        + " reason=resource-not-found");
                continue;
            }
            Marker.emit("MIXIN PLUGIN ADD ok=true mod=" + modId + " config=" + name
                    + " source=plugin source-config=" + configName);
            addConfig(modId, name, bytes, modLoader);
        }
    }

    private static String refmapLabel(ConfigParser.Parsed parsed, RefMaps.Resolved refmap) {
        if (refmap != null) {
            return refmap.resourceName();
        }
        return parsed.refmapName() == null ? "none" : "missing";
    }

    // ---- declared-metadata validation -----------------------------------------------------------

    private void validateVersion(ConfigParser.Parsed parsed) throws ConfigParser.Rejected {
        String min = parsed.minVersion();
        if (min == null || min.isBlank()) {
            return;
        }
        String engine = MixinBridge.detectVersion();
        int comparison = compareVersions(engine, min);
        if (comparison < 0) {
            throw new ConfigParser.Rejected("min-version:" + min + ">engine:" + engine, "min-version");
        }
    }

    private void validateCompatibility(ConfigParser.Parsed parsed) throws ConfigParser.Rejected {
        String declared = parsed.compatibilityLevel();
        if (declared == null || declared.isBlank()) {
            return;
        }
        int declaredClass = javaVersion(declared);
        int maxClass = javaVersion(MixinBridge.maxCompatibilityLevel());
        if (declaredClass < 0) {
            throw new ConfigParser.Rejected("compatibility-level-invalid:" + declared,
                    "compatibility-level-invalid");
        }
        if (maxClass > 0 && declaredClass > maxClass) {
            throw new ConfigParser.Rejected("compatibility-level:" + declared + ">max:"
                    + MixinBridge.maxCompatibilityLevel(), "compatibility-level");
        }
    }

    private void validatePlugin(ConfigParser.Parsed parsed, ClassLoader modLoader)
            throws ConfigParser.Rejected {
        String plugin = parsed.pluginClass();
        if (plugin == null || plugin.isBlank()) {
            return;
        }
        // The declaring mod loader owns the plugin class; the registry is not consulted first
        // because this config has not been armed yet (its loader is not registered until later).
        byte[] bytes = ResourceRegistry.resource(modLoader, plugin.replace('.', '/') + ".class");
        if (bytes == null) {
            bytes = this.resources.classBytes(plugin);
        }
        if (bytes == null) {
            throw new ConfigParser.Rejected("plugin-missing:" + plugin, "plugin-missing");
        }
        if (!implementsInterface(plugin, "org/spongepowered/asm/mixin/extensibility/IMixinConfigPlugin",
                modLoader, 0)) {
            throw new ConfigParser.Rejected("plugin-invalid:" + plugin, "plugin-invalid");
        }
    }

    private void validateInjectors(ConfigParser.Parsed parsed, ClassLoader modLoader)
            throws ConfigParser.Rejected {
        String injectors = parsed.injectorConfig();
        if (injectors == null || injectors.isBlank()) {
            return;
        }
        if (ResourceRegistry.resource(modLoader, injectors) == null) {
            throw new ConfigParser.Rejected("injectors-missing:" + injectors, "injectors-missing");
        }
    }

    /** Bounded superclass/interface walk: true when the class implements the given internal name. */
    private boolean implementsInterface(String binaryName, String internalInterface,
                                        ClassLoader loader, int depth) {
        if (binaryName == null || depth > 12) {
            return false;
        }
        org.objectweb.asm.tree.ClassNode node = ConfigParser.readNode(binaryName, loader);
        if (node == null) {
            byte[] bytes = ResourceRegistry.resource(loader,
                    binaryName.replace('.', '/') + ".class");
            if (bytes == null) {
                bytes = this.resources.classBytes(binaryName);
            }
            if (bytes == null) {
                return false;
            }
            node = new org.objectweb.asm.tree.ClassNode(org.objectweb.asm.Opcodes.ASM9);
            new org.objectweb.asm.ClassReader(bytes).accept(node, org.objectweb.asm.ClassReader.SKIP_CODE);
        }
        if (node.interfaces != null) {
            for (String iface : node.interfaces) {
                if (internalInterface.equals(iface)
                        || implementsInterface(iface.replace('/', '.'), internalInterface, loader,
                        depth + 1)) {
                    return true;
                }
            }
        }
        return node.superName != null
                && implementsInterface(node.superName.replace('/', '.'), internalInterface, loader,
                depth + 1);
    }

    /** Version compare on digit runs: {@code 0.8.7} vs {@code 0.8} → 1. */
    static int compareVersions(String left, String right) {
        int[] a = versionParts(left);
        int[] b = versionParts(right);
        int length = Math.max(a.length, b.length);
        for (int i = 0; i < length; i++) {
            int ai = i < a.length ? a[i] : 0;
            int bi = i < b.length ? b[i] : 0;
            if (ai != bi) {
                return ai < bi ? -1 : 1;
            }
        }
        return 0;
    }

    private static int[] versionParts(String value) {
        if (value == null) {
            return new int[0];
        }
        String digits = value.replaceAll("[^0-9.]", ".");
        List<Integer> parts = new ArrayList<>();
        for (String part : digits.split("\\.")) {
            if (!part.isEmpty()) {
                try {
                    parts.add(Integer.parseInt(part));
                } catch (NumberFormatException ignored) {
                    // a non-numeric segment carries no version information
                }
            }
        }
        int[] out = new int[parts.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = parts.get(i);
        }
        return out;
    }

    /** {@code JAVA_21} → 21; {@code JAVA_8} → 8; unknown → -1. */
    private static int javaVersion(String level) {
        String upper = level.toUpperCase(Locale.ROOT);
        int underscore = upper.lastIndexOf('_');
        String number = underscore >= 0 ? upper.substring(underscore + 1) : upper;
        try {
            return Integer.parseInt(number);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String parsedName(String modId, String configName) {
        return "weaver/" + modId + "/" + configName;
    }

    // ---- parent configs -------------------------------------------------------------------------

    /**
     * Makes one Mixin {@code parent} config readable by the engine.
     *
     * <p>Mixin resolves {@code parent} by resource name, not by our synthetic per-mod name, so a
     * child config that inherits settings from {@code common.mixins.json} fails with
     * "resource was invalid or could not be read" (measured on Shulker Box Tooltip). When the
     * parent is already armed we point at its synthetic name; otherwise the parent is bound (and
     * its own parent/refmap chain rewritten) so the engine can initialise it inline.
     *
     * <p>Every bound parent resource is reference counted: several children may inherit the same
     * parent, and removing one child must not unbind it for the others.
     */
    private String bindParent(String modId, String childConfig, String parentName,
                              ClassLoader modLoader, List<String> bound, Set<String> chain,
                              String side)
            throws ConfigParser.Rejected {
        Registration existing = this.configs.get(key(modId, parentName));
        if (existing != null) {
            Marker.emit("MIXIN PARENT ok=true mod=" + modId + " config=" + childConfig
                    + " parent=" + parentName + " resource=" + existing.parsed.syntheticName()
                    + " action=linked");
            return existing.parsed.syntheticName();
        }
        String synthetic = parsedName(modId, parentName);
        if (!chain.add(parentName)) {
            throw new ConfigParser.Rejected("parent-cycle:" + String.join("->", chain),
                    "parent-cycle");
        }
        if (this.resources.isBound(synthetic)) {
            byte[] cached = this.inlinedParentBytes.get(synthetic);
            if (cached != null) {
                this.resources.retain(synthetic, cached);
            }
            this.inlinedParents.merge(synthetic, 1, Integer::sum);
            bound.add(synthetic);
            Marker.emit("MIXIN PARENT ok=true mod=" + modId + " config=" + childConfig
                    + " parent=" + parentName + " resource=" + synthetic + " action=cached"
                    + " owners=" + this.resources.owners(synthetic));
            return synthetic;
        }
        byte[] bytes = ResourceRegistry.resource(modLoader, parentName);
        if (bytes == null) {
            throw new ConfigParser.Rejected("parent-missing:" + parentName, "parent-missing");
        }
        JsonObject parent;
        try {
            com.google.gson.JsonElement root = com.google.gson.JsonParser.parseString(
                    new String(bytes, StandardCharsets.UTF_8));
            if (!root.isJsonObject()) {
                throw new ConfigParser.Rejected("parent-not-an-object:" + parentName,
                        "parent-not-an-object");
            }
            parent = root.getAsJsonObject();
        } catch (ConfigParser.Rejected e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ConfigParser.Rejected("parent-json:" + parentName, "parent-json");
        }
        String grandParent = ConfigParser.field(bytes, "parent");
        if (grandParent != null && !grandParent.isBlank()) {
            parent.addProperty("parent", bindParent(modId, parentName, grandParent, modLoader,
                    bound, chain, side));
        }
        String declaredRefmap = ConfigParser.field(bytes, "refmap");
        if (declaredRefmap != null && !declaredRefmap.isBlank()) {
            RefMaps.Resolved resolved = RefMaps.resolve(synthetic, declaredRefmap, modLoader,
                    this.mappingService);
            if (resolved == null) {
                parent.remove("refmap");
            } else {
                parent.addProperty("refmap", resolved.resourceName());
                this.resources.retain(resolved.resourceName(), resolved.json());
                bound.add(resolved.resourceName());
            }
        }
        byte[] rewritten = parent.toString().getBytes(StandardCharsets.UTF_8);
        this.resources.retain(synthetic, rewritten);
        this.inlinedParentBytes.put(synthetic, rewritten);
        this.inlinedParents.merge(synthetic, 1, Integer::sum);
        bound.add(synthetic);
        Marker.emit("MIXIN PARENT ok=true mod=" + modId + " config=" + childConfig
                + " parent=" + parentName + " resource=" + synthetic + " action=inline"
                + " side=" + side);
        return synthetic;
    }

    private void releaseShared(List<String> resources) {
        for (String resource : resources) {
            boolean unbound = this.resources.release(resource);
            Integer remaining = this.inlinedParents.computeIfPresent(resource,
                    (name, count) -> count <= 1 ? null : count - 1);
            if (remaining == null) {
                this.inlinedParentBytes.remove(resource);
            }
            if (unbound) {
                Marker.emit("MIXIN PARENT RELEASE resource=" + resource + " owners=0 action=unbound");
            }
        }
    }

    // ---- bookkeeping ----------------------------------------------------------------------------

    private void refreshIndexes() {
        this.targetNames = Set.copyOf(this.targetIndex.keySet());
        this.mixinNames = Set.copyOf(this.classRefs.keySet());
    }

    private static Set<String> safeSynthetics() {
        try {
            return MixinBridge.synthetics();
        } catch (Throwable t) {
            return Set.of();
        }
    }

    private static int safeSelectedMixinCount(String synthetic) {
        try {
            return MixinBridge.selectedMixinCount(synthetic);
        } catch (Throwable t) {
            return 0;
        }
    }

    private static Set<String> diff(Set<String> after, Set<String> before) {
        Set<String> out = new LinkedHashSet<>(after);
        out.removeAll(before);
        return out;
    }

    private void removeTargetIndex(String key, Set<String> targets) {
        synchronized (this) {
            for (String target : targets) {
                Set<String> owners = this.targetIndex.get(target);
                if (owners == null) {
                    continue;
                }
                owners.remove(key);
                if (owners.isEmpty()) {
                    this.targetIndex.remove(target);
                }
            }
            refreshIndexes();
        }
    }

    private static void removeMixins(JsonObject json, Set<String> dropped) {
        String pkg = json.has("package") && json.get("package").isJsonPrimitive()
                ? json.get("package").getAsString() : null;
        for (String section : new String[]{"mixins", "client", "server"}) {
            if (!json.has(section) || !json.get(section).isJsonArray()) {
                continue;
            }
            com.google.gson.JsonArray kept = new com.google.gson.JsonArray();
            for (com.google.gson.JsonElement item : json.getAsJsonArray(section)) {
                String simple = item.getAsString();
                String qualified = pkg == null || pkg.isBlank() || simple.startsWith(pkg + ".")
                        ? simple : pkg + "." + simple;
                if (!dropped.contains(qualified)) {
                    kept.add(item);
                }
            }
            json.add(section, kept);
        }
    }

    private void cleanupFailed(ConfigParser.Parsed parsed, List<String> extraResources,
                               ClassLoader modLoader, RefMaps.Resolved refmap) {
        try {
            MixinBridge.detachConfig(parsed.syntheticName());
        } catch (Throwable ignored) {
            // best effort: the config never reached our registry
        }
        this.resources.unbind(parsed.syntheticName());
        if (refmap != null) {
            this.resources.unbind(refmap.resourceName());
        }
        this.resources.unbind(parsed.syntheticName() + ".refmap.json");
        releaseShared(extraResources);
        unbindClassesIfUnused(parsed);
        releaseLoaderIfUnused(modLoader);
        try {
            MixinBridge.resetErrorState();
        } catch (Throwable ignored) {
            // best effort: the failed config is already detached
        }
    }

    private synchronized void releaseLoaderIfUnused(ClassLoader loader) {
        for (Registration registration : this.configs.values()) {
            if (registration.loader == loader) {
                return;
            }
        }
        this.resources.removeModLoader(loader);
    }

    private void unbindClassesIfUnused(ConfigParser.Parsed parsed) {
        for (String mixin : parsed.mixinClasses()) {
            if (this.classRefs.getOrDefault(mixin, 0) == 0) {
                this.resources.unbindClass(mixin);
            }
        }
    }

    private void bestEffortSelect() {
        try {
            MixinBridge.forceSelect();
        } catch (Throwable ignored) {
            // the failed config is already detached; remaining configs prepare on next add/transform
        }
    }

    private void reject(String modId, String configName, String reason, String code) {
        WeaverProblems.record(modId, "mixin config " + configName + " rejected: " + reason);
        this.failures.incrementAndGet();
        Marker.emit("MIXIN CONFIG REJECT mod=" + Marker.text(modId) + " config="
                + Marker.text(configName) + " reason=" + Marker.text(reason)
                + " code=" + code + " action=skipped");
    }

    /**
     * Detaches one registration from the engine, then releases its resources. When the engine
     * detach fails the registration is left completely untouched (rollback) — removing our
     * bookkeeping while the engine still holds the config would make the next class load fail.
     */
    private synchronized boolean detach(String modId, String configName, Registration registration,
                                        String cause) {
        String key = key(modId, configName);
        boolean detachedEngine;
        ClassLoader previousTccl = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(registration.loader);
            detachedEngine = MixinBridge.detachConfig(registration.parsed.syntheticName());
        } catch (Throwable t) {
            detachedEngine = false;
            Marker.emit("MIXIN REMOVE ok=false mod=" + modId + " config=" + configName
                    + " reason=detach:" + Marker.oneLine(t)
                    + " cause=" + cause + " action=rolled-back");
            this.failures.incrementAndGet();
            return false;
        } finally {
            Thread.currentThread().setContextClassLoader(previousTccl);
        }
        if (!detachedEngine && MixinBridge.hasConfig(registration.parsed.syntheticName())) {
            Marker.emit("MIXIN REMOVE ok=false mod=" + modId + " config=" + configName
                    + " reason=engine-still-holds-config"
                    + " cause=" + cause + " action=rolled-back");
            this.failures.incrementAndGet();
            return false;
        }
        this.configs.remove(key);
        this.resources.unbind(registration.parsed.syntheticName());
        if (registration.refmap != null) {
            this.resources.unbind(registration.refmap.resourceName());
        }
        releaseShared(registration.extraResources);
        for (String mixin : registration.parsed.mixinClasses()) {
            int remaining = this.classRefs.getOrDefault(mixin, 1) - 1;
            if (remaining <= 0) {
                this.classRefs.remove(mixin);
                this.resources.unbindClass(mixin);
            } else {
                this.classRefs.put(mixin, remaining);
            }
        }
        boolean loaderStillUsed = false;
        for (Registration other : this.configs.values()) {
            if (other.loader == registration.loader) {
                loaderStillUsed = true;
                break;
            }
        }
        if (!loaderStillUsed) {
            this.resources.removeModLoader(registration.loader);
        }
        int orphaned = 0;
        for (String target : registration.targets) {
            Set<String> owners = this.targetIndex.get(target);
            if (owners != null) {
                owners.remove(key);
                if (owners.isEmpty()) {
                    this.targetIndex.remove(target);
                }
            }
            if (registration.applied.contains(target)) {
                orphaned++;
                Marker.emit("MIXIN ORPHAN mod=" + modId + " config=" + configName
                        + " reason=class-already-loaded target=" + target
                        + " action=bytes-remain");
            }
        }
        int retired = 0;
        for (String synthetic : registration.synthetics) {
            if (MixinBridge.retireSynthetic(synthetic)) {
                retired++;
            }
        }
        refreshIndexes();
        Marker.emit("MIXIN REMOVE ok=true mod=" + modId + " config=" + configName
                + " engine-detached=" + detachedEngine + " cause=" + cause
                + " targets=" + registration.targets.size()
                + " orphaned=" + orphaned + " synthetics-retired=" + retired);
        return true;
    }

    @Override
    public boolean isApplied(String modId, String targetClass) {
        String dotted = targetClass == null ? "" : targetClass.replace('/', '.');
        return this.appliedTargets.contains(modId + "\u0000" + dotted);
    }

    /** True when at least one active config declares this target. Lock-free hot path. */
    public boolean hasTargets(String dotted) {
        return this.targetNames.contains(dotted);
    }

    /**
     * True when this class is itself a mixin declared by an active config. Accessor/invoker mixins
     * must reach the engine too: their method bodies are stubs that Mixin rewrites when the mixin
     * class is defined (measured: {@code ClientboundCustomPayloadPacketAccessor.getMaxPayloadSize}
     * stayed a "Implemented via mixin" stub when the transformer filtered it out as a non-target).
     */
    public boolean isMixinClass(String dottedOrInternal) {
        String dotted = dottedOrInternal.replace('/', '.');
        return this.mixinNames.contains(dotted);
    }

    public Set<String> targetsOf(String modId) {
        Set<String> out = new LinkedHashSet<>();
        synchronized (this) {
            for (Registration registration : this.configs.values()) {
                if (registration.parsed.modId().equals(modId)) {
                    out.addAll(registration.targets);
                }
            }
        }
        return out;
    }

    public String loaderName(ClassLoader loader) {
        if (loader == null) {
            return "bootstrap";
        }
        String name = loader.getName();
        return name == null || name.isBlank() ? loader.getClass().getSimpleName() : name;
    }

    /** Called by the transformer for every class it sees, before any decision. */
    public void noteLoaded(String internalName) {
        this.observed.add(internalName);
        this.observed.add(internalName.replace('/', '.'));
        WeaverMixinService.markDefined(internalName);
        MixinBridge.flushPendingSynthetics();
    }

    public boolean isLoaded(String dottedOrInternal) {
        // Only the JVM's own answer counts here. "Seen by a transformer" is not "already defined":
        // the chain touches a class before it is defined, so treating an observation as loaded made
        // a config armed a moment later drop every one of its targets (Balm's LivingEntity mixin was
        // excluded because NeoForge's own config had just been armed against the same class).
        String dotted = dottedOrInternal.replace('/', '.');
        try {
            return this.probe.isLoaded(dotted);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Woven bytes for one target, or the input bytes unchanged. Never throws: every fault is a
     * marker plus original bytes, so the classloader can always define the class.
     */
    public byte[] transform(String internalName, byte[] bytes, ClassLoader loader) {
        String dotted = internalName.replace('/', '.');
        if (!this.booted || bytes == null
                || (!hasTargets(dotted) && !isMixinClass(dotted))) {
            return bytes;
        }
        if (this.transforming.get() != null) {
            // Nested definition on the same thread. The transfer engine is not re-entrant for
            // mixin application (Mixin 0.8.x ReEntranceLock), so the class keeps its original
            // bytes, but a target must never be dropped silently.
            this.failures.incrementAndGet();
            Marker.emit("MIXIN TRANSFORM FAIL target=" + dotted
                    + " reason=reentrant-definition outer=" + this.transforming.get()
                    + " owners=" + ownersOf(dotted) + " action=original-bytes");
            for (String key : ownersOf(dotted)) {
                Registration registration = registrationOf(key);
                if (registration != null) {
                    WeaverProblems.record(registration.parsed.modId(), "mixins not applied to " + dotted
                            + " (it was loaded while " + this.transforming.get() + " was being transformed)");
                }
            }
            return bytes;
        }
        this.transforming.set(dotted);
        long start = System.nanoTime();
        Recorder recorder = new Recorder();
        Audit.Recorder previous = Audit.begin(recorder);
        ClassLoader previousTccl = Thread.currentThread().getContextClassLoader();
        int engineErrorsBefore = WeaverMarkerLogger.errorCount();
        try {
            // Plugin hooks run inside transform too; the class's defining loader is the flat view
            // that also owns the mod classes (app loader in-game, guest loader offline).
            if (loader != null) {
                Thread.currentThread().setContextClassLoader(loader);
            }
            dumpIfAsked(internalName, bytes, "-input.class");
            byte[] woven = null;
            // Mixin applies every mixin of a class in one pass and aborts the whole class when one
            // config's injector fails. Keeping the original bytes there silently drops the mixins
            // of every other config on the same class (measured: a failed @WrapOperation in
            // fabric-networking-api-v1 removed fabric-data-attachment-api-v1's accessor bridge, so
            // the module entrypoint died on NoSuchMethodError). The contract is per config, so a
            // failure the engine can attribute to one config detaches that config and retries the
            // class for the rest.
            for (int attempt = 0; ; attempt++) {
                try {
                    woven = MixinBridge.transformClass(internalName, bytes);
                    break;
                } catch (Throwable failure) {
                    Registration blamed = attempt < MAX_ISOLATIONS ? blame(dotted, failure) : null;
                    if (blamed == null || !isolate(dotted, blamed, failure)) {
                        throw failure;
                    }
                    // A clean retry: nothing from the failed attempt may count or linger.
                    recorder.reset();
                    MixinBridge.forgetClassInfo(internalName);
                }
            }
            boolean changed = woven != null && !java.util.Arrays.equals(woven, bytes);
            int engineErrors = WeaverMarkerLogger.errorCount() - engineErrorsBefore;
            if (!changed) {
                if (engineErrors > 0) {
                    // The engine caught an apply failure itself and handed back the original
                    // bytes. Reporting that as "no applicable mixin" would be a silent drop.
                    failTransform(dotted, "engine-apply-failed",
                            WeaverMarkerLogger.lastError());
                    return bytes;
                }
                Marker.emit("MIXIN SKIP target=" + dotted + " reason=no-applicable-mixin"
                        + " configs=" + ownersOf(dotted).size());
                return bytes;
            }
            long nanos = System.nanoTime() - start;
            this.transformMs.add(nanos / 1_000_000.0);
            this.transformedClasses.add(dotted);
            int sites = attribute(dotted, recorder, changed, engineErrors);
            auditHandlers(dotted, woven);
            // Mixin generates helper classes while it applies a mixin (@ModifyArgs builds
            // org/spongepowered/asm/synthetic/args/Args$N). Nothing defines them for us, and the
            // woven class links against them, so define them now — the class was just written and
            // is not linked yet.
            int generated = 0;
            try {
                // The class being transformed is not defined yet, so its own synthetics are left to
                // the host's post-define hook; this call covers Mixin's helpers and any target that
                // is already defined in this loader.
                generated = MixinBridge.defineEagerSynthetics(loader);
            } catch (Throwable t) {
                Marker.emit("MIXIN SYNTHETIC EAGER ok=false reason=" + Marker.oneLine(t));
            }
            generated += MixinBridge.publishTargetSynthetics(internalName, loader);
            Marker.emit("MIXIN TRANSFORM ok=true target=" + dotted + " sites=" + sites
                    + " bytes=" + bytes.length + "->" + woven.length
                    + " transform_ms=" + Marker.ms(nanos) + " mode=load-time"
                    + " synthetics=" + generated
                    + (engineErrors > 0 ? " engine_errors=" + engineErrors : ""));
            String dump = System.getProperty("weaver.mixin.dumpDir");
            if (dump != null && !dump.isBlank()) {
                try {
                    java.nio.file.Path dir = java.nio.file.Path.of(dump);
                    java.nio.file.Files.createDirectories(dir);
                    java.nio.file.Files.write(dir.resolve(internalName.replace('/', '.') + "-woven.class"), woven);
                } catch (Throwable ignored) {
                    // diagnostics only
                }
            }
            return woven;
        } catch (Throwable t) {
            failTransform(dotted, "transform:" + Marker.oneLine(t), null);
            return bytes;
        } finally {
            Thread.currentThread().setContextClassLoader(previousTccl);
            Audit.end(previous);
            this.transforming.remove();
        }
    }

    /**
     * Returns the single config Mixin blamed for a failed apply, or {@code null} when the failure
     * cannot be pinned on one config. Mixin names the offending config in its message
     * ({@code in weaver/<mod>/<config>.mixins.json:<Mixin> …}), and that name is exactly our
     * synthetic config name, so the match is exact rather than heuristic. A class owned by one
     * config only is never isolated: there would be nothing left to weave.
     */
    /** The one declared mixin a prepare failure names, or null when it names none or several. */
    private static String culpritMixin(ConfigParser.Parsed parsed, Set<String> mixins, Throwable failure) {
        String text = describe(failure);
        Set<String> frames = stackClasses(failure);
        String found = null;
        for (String mixin : mixins) {
            String dotted = mixin.replace('/', '.');
            String simple = dotted.substring(dotted.lastIndexOf('.') + 1);
            if (frames.contains(dotted) || text.contains(dotted) || text.contains(":" + simple + " ")
                    || text.contains(":" + simple + "\n") || text.contains(simple + ".class")) {
                if (found != null && !found.equals(mixin)) {
                    return null;
                }
                found = mixin;
            }
        }
        return found;
    }

    /** The config JSON without one mixin, or null when it cannot be rewritten. */
    private static byte[] withoutMixin(byte[] config, String mixin) {
        try {
            JsonObject json = com.google.gson.JsonParser.parseString(
                    new String(config, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            String pkg = json.has("package") ? json.get("package").getAsString() : "";
            String dotted = mixin.replace('/', '.');
            boolean removed = false;
            for (String section : new String[] {"mixins", "client", "server"}) {
                if (!json.has(section) || !json.get(section).isJsonArray()) {
                    continue;
                }
                com.google.gson.JsonArray kept = new com.google.gson.JsonArray();
                for (com.google.gson.JsonElement entry : json.getAsJsonArray(section)) {
                    String name = entry.isJsonPrimitive() ? entry.getAsString() : null;
                    if (name != null && (dotted.equals(pkg + "." + name) || dotted.equals(name))) {
                        removed = true;
                        continue;
                    }
                    kept.add(entry);
                }
                json.add(section, kept);
            }
            return removed ? json.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8) : null;
        } catch (RuntimeException unreadable) {
            return null;
        }
    }

    private Registration blame(String dotted, Throwable failure) {
        String text = describe(failure);
        Set<String> owners = ownersOf(dotted);
        for (String key : owners) {
            Registration registration = registrationOf(key);
            if (registration != null && text.contains(registration.parsed.syntheticName())) {
                return registration;
            }
        }
        // An Error (a plugin's NoClassDefFoundError, an engine critical error) often names
        // no config: look for the mixin or plugin classes in the messages and stack frames.
        Set<String> frames = stackClasses(failure);
        for (String key : owners) {
            Registration registration = registrationOf(key);
            if (registration == null) {
                continue;
            }
            String plugin = registration.parsed.pluginClass();
            if (plugin != null && (frames.contains(plugin) || text.contains(plugin))) {
                return registration;
            }
            for (String mixin : registration.parsed.mixinClasses()) {
                String dottedMixin = mixin.replace('/', '.');
                if (frames.contains(dottedMixin) || text.contains(dottedMixin)) {
                    return registration;
                }
            }
        }
        // Last resort: nothing names a config. Isolate a third-party config (one per retry)
        // rather than load the class with no mixins at all, which would strip Weaver's own
        // hooks and every other mod's accessors from it.
        for (String key : owners) {
            Registration registration = registrationOf(key);
            if (registration != null && !registration.parsed.modId().startsWith("weaver-")) {
                Marker.emit("MIXIN BLAME GUESS target=" + dotted + " mod=" + registration.parsed.modId()
                        + " config=" + registration.parsed.configName()
                        + " reason=failure-names-no-config");
                return registration;
            }
        }
        return null;
    }

    private static Set<String> stackClasses(Throwable failure) {
        Set<String> classes = new LinkedHashSet<>();
        Set<Throwable> seen = Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable, Boolean>());
        for (Throwable current = failure; current != null && seen.add(current); current = current.getCause()) {
            for (StackTraceElement frame : current.getStackTrace()) {
                classes.add(frame.getClassName());
            }
        }
        return classes;
    }

    /** Walks the cause chain and joins every message: Mixin wraps the injection error twice. */
    private static String describe(Throwable failure) {
        StringBuilder out = new StringBuilder();
        Set<Throwable> seen = Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable, Boolean>());
        for (Throwable current = failure; current != null && seen.add(current);
                current = current.getCause()) {
            if (current.getMessage() != null) {
                out.append(current.getMessage()).append('\n');
            }
        }
        return out.toString();
    }

    /**
     * Isolates one config from one target and lets the caller retry the same class.
     *
     * <p>The original behaviour detached the whole config, which silently cost every class defined
     * <em>after</em> the failing one: measured, one bad method in
     * {@code DataComponentMapBuilderMixin} removed fabric-item-api-v1 from the engine, so
     * {@code Item} — defined a few hundred log lines later — never received {@code FabricItem}, and
     * the first tick died with {@code NoSuchMethodError: Item.allowComponentsUpdateAnimation} two
     * seconds after the world opened. The contract is per target: drop this config for this class
     * only, keep every other target armed, and fall back to the whole-config detach only when the
     * engine cannot be narrowed.
     */
    private boolean isolate(String dotted, Registration registration, Throwable failure) {
        String modId = registration.parsed.modId();
        String configName = registration.parsed.configName();
        boolean narrowed = false;
        try {
            narrowed = MixinBridge.detachTarget(registration.parsed.syntheticName(), dotted);
        } catch (Throwable t) {
            narrowed = false;
        }
        boolean detached = false;
        if (!narrowed) {
            detached = detach(modId, configName, registration, "apply-failure");
        } else {
            this.isolatedTargets.add(key(modId, configName) + "\u0000" + dotted);
        }
        Marker.emit("MIXIN CONFIG ISOLATE ok=" + (narrowed || detached) + " mod=" + modId
                + " config=" + configName
                + " target=" + dotted + " reason=apply-failure"
                + " error=" + Marker.text(Marker.oneLine(failure))
                + " action=" + (narrowed ? "detached-target-retry"
                        : detached ? "detached-config-retry" : "retry-unchanged"));
        if (!narrowed && !detached) {
            return false;
        }
        MixinBridge.resetErrorState();
        return true;
    }

    /** Writes the bytes the engine is about to (or did) wrap when {@code -Dweaver.mixin.dumpDir} is set. */
    private static void dumpIfAsked(String internalName, byte[] bytes, String suffix) {
        String dump = System.getProperty("weaver.mixin.dumpDir");
        if (dump == null || dump.isBlank() || bytes == null) {
            return;
        }
        try {
            java.nio.file.Path dir = java.nio.file.Path.of(dump);
            java.nio.file.Files.createDirectories(dir);
            java.nio.file.Files.write(dir.resolve(internalName.replace('/', '.') + suffix), bytes);
        } catch (Throwable ignored) {
            // diagnostics only
        }
    }

    /** Records one failed transform and attributes it to the configs that own the target. */
    private void failTransform(String dotted, String reason, String engineMessage) {
        this.failures.incrementAndGet();
        boolean recovered = false;
        try {
            MixinBridge.resetErrorState();
            recovered = true;
        } catch (Throwable ignored) {
            // reset is best effort; the class still loads with original bytes
        }
        Set<String> owners = ownersOf(dotted);
        Marker.emit("MIXIN FAILSAFE target=" + dotted + " reason=" + Marker.text(reason)
                + (engineMessage == null ? "" : " engine=" + Marker.text(engineMessage))
                + " configs=" + owners.size()
                + " action=original-bytes error-state-reset=" + recovered);
        if (owners.isEmpty()) {
            return;
        }
        for (String key : owners) {
            int count = this.failuresByConfig.merge(key, 1, Integer::sum);
            Registration registration;
            synchronized (this) {
                registration = this.configs.get(key);
            }
            if (registration == null) {
                continue;
            }
            Marker.emit("MIXIN TRANSFORM FAIL mod=" + registration.parsed.modId()
                    + " config=" + registration.parsed.configName()
                    + " target=" + dotted + " failures=" + count + " action=retry-later");
        }
    }

    private int attribute(String dotted, Recorder recorder, boolean changed, int engineErrors) {
        Map<String, Integer> perConfig = recorder.applies();
        int total = 0;
        if (perConfig.isEmpty() && changed) {
            if (engineErrors > 0) {
                // The engine logged an apply failure while weaving this class: crediting every
                // owner would claim mixins that never landed.
                Marker.emit("MIXIN APPLIED UNATTRIBUTED target=" + dotted
                        + " engine_errors=" + engineErrors + " action=owner-unknown");
                return 1;
            }
            for (String ownerKey : ownersOf(dotted)) {
                Registration registration = registrationOf(ownerKey);
                if (registration == null) {
                    continue;
                }
                recordApplied(registration, dotted, 1);
                total++;
            }
            return Math.max(total, 1);
        }
        for (Map.Entry<String, Integer> entry : perConfig.entrySet()) {
            Registration registration = registrationBySynthetic(entry.getKey());
            if (registration == null) {
                continue;
            }
            int count = Math.max(entry.getValue(), 1);
            recordApplied(registration, dotted, count);
            total += count;
        }
        return Math.max(total, 1);
    }

    /**
     * Compares the injection handlers a config declares with the callbacks that actually landed in
     * the woven class.
     *
     * <p>Mixin reports a missed injection only when it is fatal: an engine-tolerated
     * ({@code require = 0}) handler that matches nothing is dropped without a word, which is how a
     * mod can look fully armed while one of its hooks never binds (measured: jgui's
     * {@code captureContext} against NeoForge's patched {@code Gui}). Every handler that applies
     * leaves a {@code handler$…} method behind, so counting them is a cheap, always-on check that
     * does not depend on Mixin's log level.
     */
    private void auditHandlers(String dotted, byte[] woven) {
        try {
            List<String> expected = new ArrayList<>();
            String mod = null;
            for (String key : ownersOf(dotted)) {
                Registration registration;
                synchronized (this) {
                    registration = this.configs.get(key);
                }
                if (registration == null) {
                    continue;
                }
                Map<String, Set<String>> byMixin = registration.parsed.targetsByMixin();
                if (byMixin == null) {
                    continue;
                }
                int defaultRequire = shippedDefaultRequire(registration.originalBytes);
                for (Map.Entry<String, Set<String>> entry : byMixin.entrySet()) {
                    if (entry.getValue() == null || !entry.getValue().contains(dotted)) {
                        continue;
                    }
                    for (MixinFeatures.Handler handler
                            : MixinFeatures.handlerList(entry.getKey(), registration.loader)) {
                        int require = handler.require() >= 0 ? handler.require() : defaultRequire;
                        if (require > 0) {
                            expected.add(handler.name());
                        }
                    }
                }
                if (mod == null && registration.parsed.modId() != null) {
                    mod = registration.parsed.modId();
                }
            }
            if (expected.isEmpty()) {
                return;
            }
            Set<String> boundMethods = invokedOwnMethods(woven);
            List<String> missing = new ArrayList<>();
            for (String handler : expected) {
                if (!hasCallback(boundMethods, handler)) {
                    missing.add(handler);
                }
            }
            if (!missing.isEmpty()) {
                WeaverMarkerLogger.recordAbsentHandlers(mod, dotted, expected.size(), missing);
            }
            if (Boolean.getBoolean("weaver.mixin.audit")) {
                net.weaver.mixin.core.Marker.emit("MIXIN AUDIT target=" + dotted
                        + " mod=" + (mod == null ? "?" : mod)
                        + " expected=" + expected.size()
                        + " missing=" + missing.size()
                        + (missing.isEmpty() ? "" : " handlers=" + String.join(",", missing)));
            }
        } catch (Throwable t) {
            // Never fail a transform, but never hide the audit's own failure either: an audit that
            // dies silently is the same defect it exists to report.
            try {
                net.weaver.mixin.core.Marker.emit("MIXIN AUDIT FAIL target=" + dotted
                        + " reason=" + net.weaver.mixin.core.Marker.oneLine(t));
            } catch (Throwable ignored) {
                // nothing left to do
            }
        }
    }

    /** {@code injectors.defaultRequire} of a config (Mixin's own default is 1). */
    private static int defaultRequire(JsonObject config) {
        try {
            if (config != null && config.has("injectors") && config.get("injectors").isJsonObject()) {
                JsonObject injectors = config.getAsJsonObject("injectors");
                if (injectors.has("defaultRequire")) {
                    return injectors.get("defaultRequire").getAsInt();
                }
            }
        } catch (Throwable ignored) {
            // a malformed value keeps Mixin's default
        }
        return 1;
    }

    /**
     * The {@code defaultRequire} the mod <em>shipped</em>, read from the original config bytes.
     * The armed config may have been rewritten to {@code require = 0} for tolerance, and reading the
     * expectation from the rewritten bytes would make every inherited-require handler invisible to
     * the audit — the exact blindness this audit exists to remove.
     */
    private static int shippedDefaultRequire(byte[] originalConfig) {
        if (originalConfig == null || originalConfig.length == 0) {
            return 1;
        }
        try {
            String text = new String(originalConfig, java.nio.charset.StandardCharsets.UTF_8);
            java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("\"defaultRequire\"\\s*:\\s*(\\d+)").matcher(text);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        } catch (Throwable ignored) {
            // fall through to Mixin's default
        }
        return 1;
    }

    /** True when the woven class carries Mixin's callback for a handler method. */
    private static boolean hasCallback(Set<String> wovenMethods, String handler) {
        String suffix = "$" + handler;
        for (String name : wovenMethods) {
            if (name.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Methods of the woven class that are actually <em>called</em> from inside it.
     *
     * <p>A callback method alone proves nothing: under {@code require = 0} Mixin still copies the
     * {@code handler$…} method into the target and simply never inserts a call to it (measured on
     * jgui's {@code captureContext} — the method exists, no caller does). Bound handlers are the
     * ones some method invokes, so the audit keys on invocations, not on declarations.
     */
    private static Set<String> invokedOwnMethods(byte[] woven) {
        final Set<String> invoked = new LinkedHashSet<>();
        new ClassReader(woven).accept(new ClassVisitor(Opcodes.ASM9) {
            private String owner;

            @Override
            public void visit(int version, int access, String name, String signature,
                              String superName, String[] interfaces) {
                this.owner = name;
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int opcode, String callOwner, String callName,
                                                String callDescriptor, boolean isInterface) {
                        if (callOwner != null && callOwner.equals(owner)) {
                            invoked.add(callName);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return invoked;
    }

    private void recordApplied(Registration registration, String dotted, int sites) {
        registration.applied.add(dotted);
        String modId = registration.parsed.modId();
        this.appliedTargets.add(modId + "\u0000" + dotted);
        this.sitesByMod.merge(modId, sites, Integer::sum);
        Marker.emit("MIXIN APPLIED ok=true mod=" + modId + " config=" + registration.parsed.configName()
                + " target=" + dotted + " sites=" + sites + " mode=load-time");
    }

    private Registration registrationBySynthetic(String synthetic) {
        synchronized (this) {
            for (Registration registration : this.configs.values()) {
                if (registration.parsed.syntheticName().equals(synthetic)) {
                    return registration;
                }
            }
        }
        return null;
    }

    private synchronized Registration registrationOf(String key) {
        return this.configs.get(key);
    }

    private Set<String> ownersOf(String dotted) {
        synchronized (this) {
            Set<String> owners = this.targetIndex.get(dotted);
            if (owners == null || this.isolatedTargets.isEmpty()) {
                return owners == null ? Collections.emptySet() : new LinkedHashSet<>(owners);
            }
            Set<String> out = new LinkedHashSet<>();
            for (String owner : owners) {
                if (!this.isolatedTargets.contains(owner + "\u0000" + dotted)) {
                    out.add(owner);
                }
            }
            return out;
        }
    }

    @Override
    public MixinStats stats() {
        int configCount;
        synchronized (this) {
            configCount = this.configs.size();
        }
        int classes = this.transformedClasses.size();
        int sites = 0;
        for (int value : this.sitesByMod.values()) {
            sites += value;
        }
        return new MixinStats(configCount, classes, sites, this.failures.get(), this.bootMs,
                median(this.addConfigMs), max(this.addConfigMs),
                median(this.transformMs), max(this.transformMs));
    }

    /** Emits one aggregate line so a log alone carries the measured numbers. */
    public void emitStats() {
        MixinStats stats = stats();
        int pendingConfigs;
        synchronized (this) {
            pendingConfigs = this.inlinedParents.size();
        }
        Marker.emit("MIXIN STATS configs=" + stats.configs() + " classes=" + stats.classes()
                + " sites=" + stats.sites() + " failures=" + stats.failures()
                + " boot_ms=" + ms(stats.bootMillis())
                + " addconfig_ms_median=" + ms(stats.addConfigMedian())
                + " addconfig_ms_max=" + ms(stats.addConfigMax())
                + " transform_ms_median=" + ms(stats.transformMedian())
                + " transform_ms_max=" + ms(stats.transformMax())
                + " engine_errors=" + WeaverMarkerLogger.totalErrorCount()
                + " inlined_parents=" + pendingConfigs
                + " byte_cache=" + this.resources.cacheSize());
    }

    private static String ms(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static double median(List<Double> values) {
        List<Double> copy;
        synchronized (values) {
            copy = new ArrayList<>(values);
        }
        if (copy.isEmpty()) {
            return 0.0;
        }
        Collections.sort(copy);
        int n = copy.size();
        return n % 2 == 1 ? copy.get(n / 2) : (copy.get(n / 2 - 1) + copy.get(n / 2)) / 2.0;
    }

    private static double max(List<Double> values) {
        List<Double> copy;
        synchronized (values) {
            copy = new ArrayList<>(values);
        }
        double max = 0.0;
        for (double value : copy) {
            max = Math.max(max, value);
        }
        return max;
    }

    private static String key(String modId, String configName) {
        return modId + "\u0000" + configName;
    }

    /** One armed config plus its bookkeeping. */
    private static final class Registration {
        final ConfigParser.Parsed parsed;
        final ClassLoader loader;
        final byte[] configJson;
        /** The bytes the mod shipped: re-armed verbatim when a replacement fails. */
        final byte[] originalBytes;
        final RefMaps.Resolved refmap;
        final Set<String> targets = new LinkedHashSet<>();
        final Set<String> applied = ConcurrentHashMap.newKeySet();
        final Set<String> liveUnsupported = ConcurrentHashMap.newKeySet();
        final List<String> extraResources = new ArrayList<>();
        final Set<String> synthetics = new LinkedHashSet<>();

        Registration(ConfigParser.Parsed parsed, ClassLoader loader, byte[] configJson,
                     byte[] originalBytes, RefMaps.Resolved refmap) {
            this.parsed = parsed;
            this.loader = loader;
            this.configJson = configJson;
            this.originalBytes = originalBytes;
            this.refmap = refmap;
        }
    }

    /** Audit events for exactly one transform call. */
    private static final class Recorder implements Audit.Recorder {
        private final Map<String, Integer> applies = new LinkedHashMap<>();
        private boolean postProcessed;
        private final List<String> generated = new ArrayList<>();

        void reset() {
            this.applies.clear();
            this.generated.clear();
            this.postProcessed = false;
        }

        @Override
        public void apply(String targetName, String mixinRef) {
            int colon = mixinRef.indexOf(':');
            String configName = colon > 0 ? mixinRef.substring(0, colon) : mixinRef;
            this.applies.merge(configName, 1, Integer::sum);
        }

        @Override
        public void postProcess(String className) {
            this.postProcessed = true;
        }

        @Override
        public void generate(String configName, String className) {
            this.generated.add(configName + ":" + className);
        }

        Map<String, Integer> applies() {
            return this.applies;
        }
    }
}
