package net.weaver.core;

import net.weaver.core.jij.JijExtractor;
import net.weaver.core.jij.JijScanner;
import net.weaver.core.jij.NestedJar;
import net.weaver.core.modops.ModDependencyGraph;
import net.weaver.core.modops.ModDiagnostic;
import net.weaver.core.modops.ModList;
import net.weaver.core.modops.ModListEntry;
import net.weaver.core.modops.ModPolicies;
import net.weaver.core.modops.ModPolicyException;
import net.weaver.core.modops.ModStatus;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

/** Discovers mod jars, validates dependencies, and orders them. */
public final class ModLoader implements Closeable {

    private final List<ModJar> mods;
    private final ModList report;

    private ModLoader(List<ModJar> mods, ModList report) {
        this.mods = List.copyOf(mods);
        this.report = report;
    }

    /**
     * Discovers mods using the policies from system properties.
     *
     * @param modsDir directory containing mod jars
     * @param side    process side, normally {@code client} or {@code server}
     * @param log     diagnostic sink
     * @return a loader owning the accepted jars
     */
    public static ModLoader discover(Path modsDir, String side, Consumer<String> log) {
        return discover(modsDir, side, log, ModPolicies.fromSystemProperties());
    }

    /**
     * Discovers mods with explicit class-space policies.
     *
     * @param modsDir  directory containing mod jars
     * @param side     process side, normally {@code client} or {@code server}
     * @param log      diagnostic sink
     * @param policies class-space policy configuration
     * @return a loader owning the accepted jars
     * @throws ModPolicyException when a fatal policy violation is found; open jars
     *                            are closed before the exception is thrown
     */
    public static ModLoader discover(Path modsDir, String side, Consumer<String> log,
                                     ModPolicies policies) {
        return discover(modsDir, side, log, policies, null);
    }

    /**
     * Discovers mods and, when {@code jijDir} is not {@code null}, extracts and
     * loads nested mod jars before ordering.
     *
     * <p>Nested jars are read from each top-level jar's
     * {@code META-INF/weaver/jars/} layout (or its {@code META-INF/weaver/jij.json}
     * manifest) and extracted into a deterministic per-jar directory below
     * {@code jijDir} (the parent's sanitized name plus a short hash of its
     * absolute path). Only parents that were read and match the process side
     * are extracted, and a nested jar is dropped when its parent is not
     * accepted. Extracted jars participate in dependency validation,
     * duplicate-id detection, and load ordering like top-level jars. When two
     * jars declare the same id, the highest version wins and a top-level jar
     * wins a tie with a nested one. Extraction is performed once per
     * discovery; a missing or invalid nested layout is reported and does not
     * fail discovery.</p>
     *
     * @param modsDir  directory containing mod jars
     * @param side     process side, normally {@code client} or {@code server}
     * @param log      diagnostic sink
     * @param policies class-space policy configuration
     * @param jijDir   extraction root for nested jars, or {@code null} to keep
     *                 nested jars external
     * @return a loader owning the accepted jars
     * @throws ModPolicyException when a fatal policy violation is found
     */
    public static ModLoader discover(Path modsDir, String side, Consumer<String> log,
                                     ModPolicies policies, Path jijDir) {
        if (modsDir == null || !Files.isDirectory(modsDir)) {
            log.accept("[weaver] MODS DIR missing path=" + modsDir);
            return new ModLoader(List.of(), new ModList(side, List.of(), List.of()));
        }
        List<Path> jars = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsDir, "*.jar")) {
            for (Path path : stream) {
                jars.add(path);
            }
        } catch (IOException e) {
            log.accept("[weaver] MODS DIR FAILED path=" + modsDir + " reason=" + e);
            return new ModLoader(List.of(), new ModList(side, List.of(), List.of()));
        }
        jars.sort(Comparator.comparing(path -> path.getFileName().toString()));

        Discovery discovery = new Discovery(side, log);
        List<Candidate> topLevel = new ArrayList<>();
        for (Path path : jars) {
            Candidate candidate = discovery.read(path, null);
            if (candidate != null) {
                topLevel.add(candidate);
            }
        }
        List<Candidate> candidates = new ArrayList<>(topLevel);
        if (jijDir != null) {
            Set<Path> loadedParents = new LinkedHashSet<>();
            for (Candidate candidate : topLevel) {
                loadedParents.add(candidate.path());
            }
            for (Path jar : jars) {
                if (!loadedParents.contains(jar)) {
                    logSkippedNested(jar, log);
                    continue;
                }
                for (Path extracted : extractNested(jar, jijDir, log)) {
                    Candidate candidate = discovery.read(extracted, jar);
                    if (candidate != null) {
                        candidates.add(candidate);
                    }
                }
            }
        }

        List<Candidate> winners = discovery.resolveDuplicates(candidates);
        List<ModJar> accepted = validate(winners, log, discovery.diagnostics, discovery.entries,
                discovery.bundledBy);
        checkClassPolicies(accepted, log, discovery.diagnostics, policies);

        Map<Path, Integer> loadOrderByPath = new LinkedHashMap<>();
        for (int index = 0; index < accepted.size(); index++) {
            loadOrderByPath.put(accepted.get(index).path(), index);
        }
        List<ModListEntry> entries = new ArrayList<>();
        for (Path path : discovery.order) {
            ModListEntry entry = discovery.entries.get(path);
            if (entry == null) {
                continue;
            }
            Integer loadOrder = loadOrderByPath.get(path);
            if (loadOrder != null && entry.status() == ModStatus.LOADED) {
                entry = entry.withLoadOrder(loadOrder);
            }
            entries.add(entry);
        }
        ModList report = new ModList(side, entries, discovery.diagnostics);
        return new ModLoader(accepted, report);
    }

    /** One readable, side-compatible jar: a top-level jar ({@code parent == null}) or a nested one. */
    private record Candidate(ModJar mod, Path path, Path parent) {
        String id() {
            return mod.metadata().id();
        }

        String file() {
            return path.getFileName().toString();
        }
    }

    /** Mutable discovery state: report entries keyed by jar path, in discovery order. */
    private static final class Discovery {
        private final String side;
        private final Consumer<String> log;
        private final List<Path> order = new ArrayList<>();
        private final Map<Path, ModListEntry> entries = new LinkedHashMap<>();
        private final List<ModDiagnostic> diagnostics = new ArrayList<>();

        private Discovery(String side, Consumer<String> log) {
            this.side = side;
            this.log = log;
        }

        /** Reads one jar; returns {@code null} when it fails to read or targets the other side. */
        private Candidate read(Path path, Path parent) {
            String file = path.getFileName().toString();
            order.add(path);
            ModJar mod = null;
            try {
                mod = ModJar.read(path);
                ModMetadata metadata = mod.metadata();
                ModListEntry entry = entryOf(metadata, file);
                if (!"both".equals(metadata.side()) && !side.equals(metadata.side())) {
                    log.accept("[weaver] MOD SKIP id=" + metadata.id() + " side=" + metadata.side()
                            + " process-side=" + side);
                    diagnostics.add(new ModDiagnostic(ModDiagnostic.Severity.WARN,
                            ModDiagnostic.Type.WRONG_SIDE, metadata.id(),
                            "side=" + metadata.side() + " process-side=" + side));
                    entries.put(path, entry.withStatus(ModStatus.WRONG_SIDE,
                            "declared side=" + metadata.side() + " process-side=" + side));
                    closeQuietly(mod);
                    return null;
                }
                entries.put(path, entry);
                return new Candidate(mod, path, parent);
            } catch (IOException | RuntimeException | LinkageError | StackOverflowError e) {
                if (mod != null) {
                    closeQuietly(mod);
                }
                log.accept("[weaver] MOD READ FAILED path=" + file + " reason=" + e);
                diagnostics.add(new ModDiagnostic(ModDiagnostic.Severity.WARN,
                        ModDiagnostic.Type.READ_FAILED, file,
                        "path=" + file + " reason=" + e));
                entries.put(path, readFailedEntry(file, e));
                return null;
            }
        }

        /**
         * Keeps one jar per mod id: the highest version wins, a top-level jar
         * beats a nested jar of the same version, and otherwise the first jar
         * in discovery order wins. Nested jars whose parent did not survive are
         * dropped, which can in turn change the winner of their id, so the
         * selection repeats until it is stable.
         */
        /** Every parent jar that bundled a nested mod id, winners and losers alike. */
        final Map<String, Set<Path>> bundledBy = new HashMap<>();

        private List<Candidate> resolveDuplicates(List<Candidate> candidates) {
            for (Candidate candidate : candidates) {
                if (candidate.parent() != null) {
                    bundledBy.computeIfAbsent(candidate.id(), key -> new LinkedHashSet<>()).add(candidate.parent());
                }
            }
            List<Candidate> active = new ArrayList<>(candidates);
            Map<String, Candidate> winners;
            while (true) {
                winners = pickWinners(active);
                Set<Path> kept = new LinkedHashSet<>();
                for (Candidate winner : winners.values()) {
                    kept.add(winner.path());
                }
                List<Candidate> orphans = new ArrayList<>();
                for (Candidate candidate : active) {
                    if (candidate.parent() != null && !kept.contains(candidate.parent())) {
                        orphans.add(candidate);
                    }
                }
                if (orphans.isEmpty()) {
                    break;
                }
                for (Candidate orphan : orphans) {
                    String parentFile = orphan.parent().getFileName().toString();
                    log.accept("[weaver] MOD SKIP id=" + orphan.id()
                            + " reason=parent-not-loaded parent=" + parentFile);
                    diagnostics.add(new ModDiagnostic(ModDiagnostic.Severity.WARN,
                            ModDiagnostic.Type.MISSING_DEPENDENCY, orphan.id(),
                            "parent=" + parentFile + " reason=parent-not-loaded"));
                    entries.computeIfPresent(orphan.path(), (key, entry) -> entry.withStatus(
                            ModStatus.REJECTED_DEPENDENCY, "parent=" + parentFile + " not loaded"));
                    closeQuietly(orphan.mod());
                    active.remove(orphan);
                }
            }
            List<Candidate> result = new ArrayList<>();
            for (Candidate candidate : active) {
                Candidate winner = winners.get(candidate.id());
                if (winner == candidate) {
                    result.add(candidate);
                    ModMetadata metadata = candidate.mod().metadata();
                    log.accept("[weaver] MOD READ id=" + metadata.id() + " version=" + metadata.version()
                            + " main=" + metadata.mainClass() + " mixins=" + metadata.mixins().size()
                            + " at=" + (metadata.accessTransformers().isEmpty() ? "default" : metadata.accessTransformers())
                            + " deps=" + metadata.dependencies() + " optional=" + metadata.optional()
                            + " breaks=" + metadata.breaks());
                    continue;
                }
                String file = candidate.file();
                log.accept("[weaver] MOD SKIP id=" + candidate.id()
                        + " reason=duplicate-id path=" + file);
                diagnostics.add(new ModDiagnostic(ModDiagnostic.Severity.WARN,
                        ModDiagnostic.Type.DUPLICATE_ID, candidate.id(),
                        "path=" + file + " version=" + candidate.mod().metadata().version()
                                + " kept=" + winner.file()
                                + " kept-version=" + winner.mod().metadata().version()));
                entries.computeIfPresent(candidate.path(), (key, entry) -> entry.withStatus(
                        ModStatus.DUPLICATE_ID, "duplicate of " + winner.file()));
                closeQuietly(candidate.mod());
            }
            return result;
        }

        private static Map<String, Candidate> pickWinners(List<Candidate> candidates) {
            Map<String, Candidate> winners = new LinkedHashMap<>();
            for (Candidate candidate : candidates) {
                Candidate best = winners.get(candidate.id());
                if (best == null || beats(candidate, best)) {
                    winners.put(candidate.id(), candidate);
                }
            }
            return winners;
        }

        private static boolean beats(Candidate challenger, Candidate best) {
            int comparison = VersionRange.compare(challenger.mod().metadata().version(),
                    best.mod().metadata().version());
            if (comparison != 0) {
                return comparison > 0;
            }
            return challenger.parent() == null && best.parent() != null;
        }
    }

    private static void logSkippedNested(Path jar, Consumer<String> log) {
        try {
            List<NestedJar> nested = JijScanner.scan(jar);
            if (!nested.isEmpty()) {
                log.accept("[weaver] MOD JIJ SKIP parent=" + jar.getFileName()
                        + " nested=" + nested.size() + " reason=parent-not-loaded");
            }
        } catch (IOException | RuntimeException ignored) {
            // the parent already failed to read and was reported
        }
    }

    private static List<Path> extractNested(Path jar, Path jijDir, Consumer<String> log) {
        try {
            List<NestedJar> nested = JijScanner.scan(jar);
            if (nested.isEmpty()) {
                return List.of();
            }
            String file = jar.getFileName().toString();
            String stem = file.endsWith(".jar")
                    ? file.substring(0, file.length() - 4) : file;
            Path target = jijDir.resolve(sanitizeFile(stem) + "-" + shortHash(jar));
            List<Path> extracted = JijExtractor.extract(jar, target, log);
            log.accept("[weaver] MOD JIJ parent=" + file + " nested=" + nested.size()
                    + " extracted=" + extracted.size() + " dir=" + target);
            return extracted;
        } catch (IOException | RuntimeException | LinkageError | StackOverflowError failure) {
            log.accept("[weaver] MOD JIJ FAILED path=" + jar.getFileName()
                    + " reason=" + failure);
            return List.of();
        }
    }

    private static String sanitizeFile(String value) {
        String safe = value.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("^-+|-+$", "");
        return safe.isEmpty() ? "mod" : safe;
    }

    /** Eight hex digits of the SHA-256 of the parent's absolute path, so two parents never share a directory. */
    static String shortHash(Path jar) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(
                    jar.toAbsolutePath().normalize().toString()
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest, 0, 4);
        } catch (java.security.NoSuchAlgorithmException impossible) {
            return Integer.toHexString(jar.toAbsolutePath().normalize().toString().hashCode());
        }
    }

    private static void closeQuietly(ModJar mod) {
        try {
            mod.close();
        } catch (IOException ignored) {
            // rejected candidate
        }
    }

    private static ModListEntry entryOf(ModMetadata metadata, String file) {
        return new ModListEntry(metadata.id(), metadata.version(), metadata.name(),
                metadata.side(), metadata.minecraft(), metadata.mainClass(),
                metadata.dependencies(), metadata.optional(), metadata.breaks(),
                metadata.mixins(), metadata.accessTransformers(), -1,
                ModStatus.LOADED, "", file);
    }

    private static ModListEntry readFailedEntry(String file, Throwable failure) {
        String id = file.endsWith(".jar") ? file.substring(0, file.length() - 4) : file;
        return new ModListEntry(id, "unknown", id, "unknown", "unknown", "",
                Map.of(), Map.of(), Map.of(), List.of(), List.of(), -1,
                ModStatus.READ_FAILED, "reason=" + failure, file);
    }

    /**
     * Reports class entries shipped by more than one accepted mod and checks
     * class-space policies. The shared class space is first-wins: the first
     * accepted mod on the classpath supplies the class, later copies are
     * ignored silently by the JVM.
     */
    private static void checkClassPolicies(List<ModJar> mods, Consumer<String> log,
                                           List<ModDiagnostic> diagnostics,
                                           ModPolicies policies) {
        Map<String, Set<String>> owners = new TreeMap<>();
        for (ModJar mod : mods) {
            String id = mod.metadata().id();
            for (String entry : mod.classEntries()) {
                owners.computeIfAbsent(entry, key -> new LinkedHashSet<>()).add(id);
            }
        }
        boolean fatalDuplicate = policies.duplicateClasses() == ModPolicies.Mode.FATAL;
        List<ModDiagnostic> failures = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : owners.entrySet()) {
            if (entry.getValue().size() < 2) {
                continue;
            }
            List<String> ids = new ArrayList<>(entry.getValue());
            List<String> sorted = new ArrayList<>(ids);
            sorted.sort(Comparator.naturalOrder());
            String detail = "mods=" + String.join(",", sorted) + " first=" + ids.get(0);
            log.accept("[weaver] MOD CLASS CONFLICT class=" + entry.getKey()
                    + " " + detail + (fatalDuplicate ? " action=fatal" : ""));
            ModDiagnostic diagnostic = new ModDiagnostic(
                    fatalDuplicate ? ModDiagnostic.Severity.FATAL : ModDiagnostic.Severity.WARN,
                    ModDiagnostic.Type.DUPLICATE_CLASS, entry.getKey(), detail);
            diagnostics.add(diagnostic);
            if (fatalDuplicate) {
                failures.add(diagnostic);
            }
        }

        boolean fatalShadow = policies.apiShadow() == ModPolicies.Mode.FATAL;
        for (ModJar mod : mods) {
            String id = mod.metadata().id();
            for (String classEntry : mod.classEntries()) {
                if (!classEntry.startsWith("net/weaver/api/")) {
                    continue;
                }
                log.accept("[weaver] MOD API SHADOW class=" + classEntry + " mod=" + id
                        + " action=" + (fatalShadow ? "fatal" : "warn"));
                ModDiagnostic diagnostic = new ModDiagnostic(
                        fatalShadow ? ModDiagnostic.Severity.FATAL : ModDiagnostic.Severity.WARN,
                        ModDiagnostic.Type.API_SHADOW, classEntry, "mod=" + id);
                diagnostics.add(diagnostic);
                if (fatalShadow) {
                    failures.add(diagnostic);
                }
            }
        }

        if (!failures.isEmpty()) {
            for (ModJar mod : mods) {
                try {
                    mod.close();
                } catch (IOException ignored) {
                    // policy abort closes every accepted jar
                }
            }
            throw new ModPolicyException(failures);
        }
    }

    /**
     * Validates breaks and required dependencies of the duplicate-free
     * candidates.
     *
     * <p>Rejection propagates: a mod whose required dependency was itself
     * rejected is rejected as a missing dependency, and a nested jar whose
     * parent was rejected is dropped. Validation repeats until no further mod
     * is rejected. A dependency or breaks entry whose range cannot be parsed
     * rejects the declaring mod instead of being matched exactly.</p>
     */
    private static List<ModJar> validate(List<Candidate> candidates,
                                         Consumer<String> log, List<ModDiagnostic> diagnostics,
                                         Map<Path, ModListEntry> entries,
                                         Map<String, Set<Path>> bundledBy) {
        List<ModJar> read = new ArrayList<>();
        Map<String, ModJar> byId = new LinkedHashMap<>();
        Map<ModJar, String> parentIds = new HashMap<>();
        Map<Path, String> idsByPath = new HashMap<>();
        for (Candidate candidate : candidates) {
            read.add(candidate.mod());
            byId.put(candidate.id(), candidate.mod());
            idsByPath.put(candidate.path(), candidate.id());
        }
        for (Candidate candidate : candidates) {
            if (candidate.parent() != null && idsByPath.containsKey(candidate.parent())) {
                parentIds.put(candidate.mod(), idsByPath.get(candidate.parent()));
            }
        }
        Set<String> rejected = new LinkedHashSet<>();
        for (ModJar mod : read) {
            ModMetadata metadata = mod.metadata();
            String game = net.weaver.api.WeaverApi.GAME_VERSION;
            String wanted = metadata.minecraft();
            if (wanted != null && !wanted.isBlank() && VersionRange.isValid(wanted)
                    && !VersionRange.matches(game, wanted)) {
                // Built for another game version: its mixins would fail at some later class load.
                boolean allowed = Boolean.getBoolean("weaver.ignoreMinecraftVersion");
                log.accept("[weaver] MOD GAME VERSION MISMATCH id=" + metadata.id() + " wants=" + wanted
                        + " game=" + game + " action=" + (allowed ? "loaded (weaver.ignoreMinecraftVersion=true)"
                        : "rejected (override: -Dweaver.ignoreMinecraftVersion=true)"));
                diagnostics.add(new ModDiagnostic(ModDiagnostic.Severity.WARN,
                        ModDiagnostic.Type.VERSION_MISMATCH, metadata.id(),
                        "minecraft=" + wanted + " game=" + game));
                if (!allowed) {
                    rejected.add(metadata.id());
                    mark(entries, mod, ModStatus.REJECTED_VERSION, "minecraft=" + wanted + " game=" + game);
                    continue;
                }
            }
            String invalid = invalidRange(metadata.dependencies());
            if (invalid != null) {
                String range = metadata.dependencies().get(invalid);
                String problem = VersionRange.problem(range);
                log.accept("[weaver] MOD DEPENDENCY RANGE INVALID id=" + metadata.id()
                        + " dependency=" + invalid + " range=" + range + " reason=" + problem);
                diagnostics.add(new ModDiagnostic(ModDiagnostic.Severity.WARN,
                        ModDiagnostic.Type.VERSION_MISMATCH, metadata.id(),
                        "dependency=" + invalid + " range=" + range + " invalid-range=" + problem));
                rejected.add(metadata.id());
                mark(entries, mod, ModStatus.REJECTED_VERSION,
                        "dependency=" + invalid + " range=" + range + " invalid-range=" + problem);
                continue;
            }
            invalid = invalidRange(metadata.breaks());
            if (invalid != null) {
                String range = metadata.breaks().get(invalid);
                String problem = VersionRange.problem(range);
                log.accept("[weaver] MOD BREAKS RANGE INVALID id=" + metadata.id()
                        + " target=" + invalid + " range=" + range + " reason=" + problem);
                diagnostics.add(new ModDiagnostic(ModDiagnostic.Severity.WARN,
                        ModDiagnostic.Type.BREAKS, metadata.id(),
                        "target=" + invalid + " range=" + range + " invalid-range=" + problem));
                rejected.add(metadata.id());
                mark(entries, mod, ModStatus.REJECTED_BREAKS,
                        "target=" + invalid + " range=" + range + " invalid-range=" + problem);
                continue;
            }
            for (Map.Entry<String, String> entry : metadata.breaks().entrySet()) {
                ModJar target = byId.get(entry.getKey());
                if (target != null && VersionRange.matches(target.metadata().version(), entry.getValue())) {
                    log.accept("[weaver] MOD BREAKS id=" + metadata.id() + " target=" + entry.getKey()
                            + " target-version=" + target.metadata().version() + " range=" + entry.getValue());
                    diagnostics.add(new ModDiagnostic(ModDiagnostic.Severity.WARN,
                            ModDiagnostic.Type.BREAKS, metadata.id(),
                            "target=" + entry.getKey() + " target-version="
                                    + target.metadata().version() + " range=" + entry.getValue()));
                    rejected.add(metadata.id());
                    mark(entries, mod, ModStatus.REJECTED_BREAKS,
                            "target=" + entry.getKey() + " target-version="
                                    + target.metadata().version() + " range=" + entry.getValue());
                    break;
                }
            }
        }

        boolean changed = true;
        while (changed) {
            changed = false;
            for (ModJar mod : read) {
                ModMetadata metadata = mod.metadata();
                if (rejected.contains(metadata.id())) {
                    continue;
                }
                String parentId = parentIds.get(mod);
                if (parentId != null && rejected.contains(parentId)) {
                    // The library another healthy mod bundled too stays: only its first
                    // bundler was rejected.
                    String other = healthyBundler(bundledBy.get(metadata.id()), idsByPath, rejected);
                    if (other != null) {
                        log.accept("[weaver] MOD JIJ KEPT id=" + metadata.id() + " rejected-parent=" + parentId
                                + " kept-for=" + other);
                        parentIds.put(mod, other);
                        continue;
                    }
                    log.accept("[weaver] MOD SKIP id=" + metadata.id()
                            + " reason=parent-rejected parent=" + parentId);
                    diagnostics.add(new ModDiagnostic(ModDiagnostic.Severity.WARN,
                            ModDiagnostic.Type.MISSING_DEPENDENCY, metadata.id(),
                            "parent=" + parentId + " reason=parent-rejected"));
                    rejected.add(metadata.id());
                    changed = true;
                    mark(entries, mod, ModStatus.REJECTED_DEPENDENCY,
                            "parent=" + parentId + " rejected");
                    continue;
                }
                for (Map.Entry<String, String> entry : metadata.dependencies().entrySet()) {
                    ModJar target = byId.get(entry.getKey());
                    if (target == null || rejected.contains(entry.getKey())) {
                        String reason = target == null ? "" : " reason=dependency-rejected";
                        log.accept("[weaver] MOD DEPENDENCY MISSING id=" + metadata.id()
                                + " dependency=" + entry.getKey() + " range=" + entry.getValue()
                                + reason);
                        diagnostics.add(new ModDiagnostic(ModDiagnostic.Severity.WARN,
                                ModDiagnostic.Type.MISSING_DEPENDENCY, metadata.id(),
                                "dependency=" + entry.getKey() + " range=" + entry.getValue()
                                        + reason));
                        rejected.add(metadata.id());
                        changed = true;
                        mark(entries, mod, ModStatus.REJECTED_DEPENDENCY,
                                "dependency=" + entry.getKey() + " range=" + entry.getValue()
                                        + reason);
                        break;
                    }
                    if (!VersionRange.matches(target.metadata().version(), entry.getValue())) {
                        log.accept("[weaver] MOD DEPENDENCY VERSION id=" + metadata.id()
                                + " dependency=" + entry.getKey() + " required=" + entry.getValue()
                                + " actual=" + target.metadata().version());
                        diagnostics.add(new ModDiagnostic(ModDiagnostic.Severity.WARN,
                                ModDiagnostic.Type.VERSION_MISMATCH, metadata.id(),
                                "dependency=" + entry.getKey() + " required=" + entry.getValue()
                                        + " actual=" + target.metadata().version()));
                        rejected.add(metadata.id());
                        changed = true;
                        mark(entries, mod, ModStatus.REJECTED_VERSION,
                                "dependency=" + entry.getKey() + " required=" + entry.getValue()
                                        + " actual=" + target.metadata().version());
                        break;
                    }
                }
            }
        }

        for (ModJar mod : read) {
            ModMetadata metadata = mod.metadata();
            if (rejected.contains(metadata.id())) {
                continue;
            }
            for (Map.Entry<String, String> entry : metadata.optional().entrySet()) {
                ModJar target = rejected.contains(entry.getKey()) ? null : byId.get(entry.getKey());
                if (!VersionRange.isValid(entry.getValue())) {
                    log.accept("[weaver] MOD OPTIONAL VERSION id=" + metadata.id()
                            + " optional=" + entry.getKey() + " range=" + entry.getValue()
                            + " invalid-range=" + VersionRange.problem(entry.getValue()));
                    diagnostics.add(new ModDiagnostic(ModDiagnostic.Severity.WARN,
                            ModDiagnostic.Type.OPTIONAL_VERSION, metadata.id(),
                            "optional=" + entry.getKey() + " range=" + entry.getValue()
                                    + " invalid-range=" + VersionRange.problem(entry.getValue())));
                    continue;
                }
                if (target != null && !VersionRange.matches(target.metadata().version(), entry.getValue())) {
                    log.accept("[weaver] MOD OPTIONAL VERSION id=" + metadata.id()
                            + " optional=" + entry.getKey() + " range=" + entry.getValue()
                            + " actual=" + target.metadata().version());
                    diagnostics.add(new ModDiagnostic(ModDiagnostic.Severity.WARN,
                            ModDiagnostic.Type.OPTIONAL_VERSION, metadata.id(),
                            "optional=" + entry.getKey() + " range=" + entry.getValue()
                                    + " actual=" + target.metadata().version()));
                }
            }
        }
        List<ModJar> accepted = new ArrayList<>();
        for (ModJar mod : read) {
            if (rejected.contains(mod.metadata().id())) {
                try {
                    mod.close();
                } catch (IOException ignored) {
                    // rejected at discovery
                }
            } else {
                accepted.add(mod);
            }
        }
        return order(accepted, log, diagnostics);
    }

    private static String invalidRange(Map<String, String> ranges) {
        for (Map.Entry<String, String> entry : ranges.entrySet()) {
            if (!VersionRange.isValid(entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static String healthyBundler(Set<Path> bundlers, Map<Path, String> idsByPath, Set<String> rejected) {
        if (bundlers == null) {
            return null;
        }
        for (Path bundler : bundlers) {
            String id = idsByPath.get(bundler);
            if (id != null && !rejected.contains(id)) {
                return id;
            }
        }
        return null;
    }

    private static void mark(Map<Path, ModListEntry> entries, ModJar mod,
                             ModStatus status, String detail) {
        ModListEntry entry = entries.get(mod.path());
        if (entry != null) {
            entries.put(mod.path(), entry.withStatus(status, detail));
        }
        if (status != ModStatus.LOADED) {
            net.weaver.api.WeaverProblems.record(mod.metadata().id(), "not loaded: " + status + " " + detail);
        }
    }

    private static List<ModJar> order(List<ModJar> mods, Consumer<String> log,
                                      List<ModDiagnostic> diagnostics) {
        Map<String, ModJar> byId = new LinkedHashMap<>();
        for (ModJar mod : mods) {
            byId.put(mod.metadata().id(), mod);
        }
        Map<String, Integer> indegree = new HashMap<>();
        Map<String, List<String>> dependents = new HashMap<>();
        for (ModJar mod : mods) {
            indegree.put(mod.metadata().id(), 0);
        }
        for (ModJar mod : mods) {
            for (Map.Entry<String, String> entry : mod.metadata().dependencies().entrySet()) {
                addEdge(mod.metadata().id(), entry.getKey(), byId, indegree, dependents);
            }
            for (Map.Entry<String, String> entry : mod.metadata().optional().entrySet()) {
                ModJar target = byId.get(entry.getKey());
                if (target != null && VersionRange.matches(target.metadata().version(), entry.getValue())) {
                    addEdge(mod.metadata().id(), entry.getKey(), byId, indegree, dependents);
                }
            }
        }
        ArrayDeque<String> ready = new ArrayDeque<>();
        indegree.entrySet().stream()
                .filter(entry -> entry.getValue() == 0)
                .map(Map.Entry::getKey)
                .sorted()
                .forEach(ready::add);
        List<ModJar> ordered = new ArrayList<>();
        while (!ready.isEmpty()) {
            String id = ready.removeFirst();
            ordered.add(byId.get(id));
            List<String> next = dependents.getOrDefault(id, List.of());
            next.stream().sorted().forEach(dependent -> {
                int remaining = indegree.merge(dependent, -1, Integer::sum);
                if (remaining == 0) {
                    ready.addLast(dependent);
                }
            });
        }
        if (ordered.size() != mods.size()) {
            List<ModJar> remaining = new ArrayList<>();
            for (ModJar mod : mods) {
                if (!ordered.contains(mod)) {
                    remaining.add(mod);
                }
            }
            remaining.sort(Comparator.comparing(mod -> mod.metadata().id()));
            log.accept("[weaver] MOD ORDER cycle=true remaining=" + remaining.size()
                    + " action=filename-order");
            List<ModListEntry> entries = new ArrayList<>();
            for (ModJar mod : mods) {
                entries.add(entryOf(mod.metadata(), mod.path().getFileName().toString()));
            }
            for (List<String> cycle : new ModDependencyGraph(entries).cycles()) {
                diagnostics.add(new ModDiagnostic(ModDiagnostic.Severity.WARN,
                        ModDiagnostic.Type.CYCLE, String.join(",", cycle),
                        "cycle ids=" + cycle + " action=filename-order"));
            }
            ordered.addAll(remaining);
        }
        return ordered;
    }

    private static void addEdge(String id, String dependency, Map<String, ModJar> byId,
                                Map<String, Integer> indegree, Map<String, List<String>> dependents) {
        if (!byId.containsKey(dependency)) {
            return;
        }
        indegree.merge(id, 1, Integer::sum);
        dependents.computeIfAbsent(dependency, key -> new ArrayList<>()).add(id);
    }

    public List<ModJar> mods() {
        return mods;
    }

    /**
     * Returns the structured report for the last discovery run.
     *
     * <p>The report includes accepted and rejected candidates, diagnostics,
     * and load positions. It is immutable and safe to keep after this loader is
     * closed.
     *
     * @return immutable mod-list report
     */
    public ModList report() {
        return report;
    }

    public String accessTransformers() {
        StringBuilder text = new StringBuilder();
        for (ModJar mod : mods) {
            if (!mod.accessTransformers().isBlank()) {
                text.append(mod.accessTransformers()).append('\n');
            }
        }
        return text.toString();
    }

    @Override
    public void close() {
        for (ModJar mod : mods) {
            try {
                mod.close();
            } catch (IOException ignored) {
                // shutdown only
            }
        }
    }
}
