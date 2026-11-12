package net.weaver.gradle.dependency;

import org.gradle.api.artifacts.ArtifactCollection;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolutionResult;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.artifacts.result.UnresolvedDependencyResult;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Resolves required, optional, and local Weaver mods and produces a
 * deterministic {@link ModResolution}.
 *
 * <p>Required dependencies are resolved transitively. Optional dependencies are
 * resolved leniently and only ever produce warnings when unresolved. Artifacts
 * without a {@code weaver.mod.json} descriptor are reported as plain libraries
 * rather than mods.
 *
 * <p>Version ranges are delegated to Gradle; the resolver only inspects the
 * requested versions in the resolution graph to report a {@code module-version}
 * conflict when distinct requests disagree with the selected version.
 */
public final class ModDependencyResolver {

    private final Configuration mods;
    private final Configuration optional;
    private final FileCollection localMods;
    private final boolean strict;
    private final Logger logger;
    private final List<ModConflict> fatalConflicts = new ArrayList<>();

    /**
     * Creates a resolver.
     *
     * @param mods the required {@code weaverMod} configuration, or null for none
     * @param optional the optional {@code weaverOptional} configuration, or null
     * @param localMods local jar files that are treated as required mods
     * @param strict whether version and missing-module conflicts are fatal
     * @param logger the logger used for diagnostics, or null for {@code System.out}
     */
    public ModDependencyResolver(Configuration mods, Configuration optional,
                                 FileCollection localMods, boolean strict, Logger logger) {
        this.mods = mods;
        this.optional = optional;
        this.localMods = localMods;
        this.strict = strict;
        this.logger = logger;
    }

    /**
     * Resolves the configured mods and prints stable diagnostic lines.
     *
     * <p>On a fatal resolution this method still returns the full resolution so
     * the caller can write the report before throwing. Callers should check
     * {@link #hasFatalConflicts()} afterwards.
     *
     * @return the deterministic resolution
     */
    public ModResolution resolve() {
        fatalConflicts.clear();
        Build build = new Build();

        ResolutionResult requiredResult = resolutionResult(mods, build);
        Map<String, SortedSet<String>> requiredRequested = requestedByModule(requiredResult);
        Map<String, String> requiredSelected = selectedByModule(requiredResult);
        addArtifacts(mods, false, requiredResult, directComponents(requiredResult),
                requiredRequested, componentModules(requiredResult),
                componentVersions(requiredResult), build);
        collectUnresolved(requiredResult, false, build);
        collectVersionConflicts(requiredRequested, requiredSelected, build);

        ResolutionResult optionalResult = resolutionResult(optional, build);
        Map<String, SortedSet<String>> optionalRequested = requestedByModule(optionalResult);
        Map<String, String> optionalSelected = selectedByModule(optionalResult);
        addArtifacts(optional, true, optionalResult, directComponents(optionalResult),
                optionalRequested, componentModules(optionalResult),
                componentVersions(optionalResult), build);
        collectUnresolved(optionalResult, true, build);
        collectVersionConflicts(optionalRequested, optionalSelected, build);

        addLocalMods(build);
        List<Candidate> kept = removeDuplicates(build);
        kept.sort(candidateOrder());

        List<ResolvedMod> mods = kept.stream()
                .map(Candidate::mod)
                .sorted(modOrder())
                .toList();
        List<ModConflict> conflicts = new ArrayList<>(new LinkedHashSet<>(build.conflicts));
        conflicts.sort(conflictOrder());
        List<String> warnings = build.warnings.stream().distinct().sorted().toList();
        List<File> libraries = build.libraries.stream().distinct()
                .sorted(Comparator.comparing(File::getAbsolutePath))
                .toList();
        fatalConflicts.addAll(new LinkedHashSet<>(build.fatal));

        ModResolution resolution = new ModResolution(mods, conflicts, warnings, libraries);
        print(kept, conflicts, warnings, libraries);
        return resolution;
    }

    /**
     * Returns whether the last resolution produced at least one fatal conflict.
     *
     * @return true when the build must fail
     */
    public boolean hasFatalConflicts() {
        return !fatalConflicts.isEmpty();
    }

    /**
     * Returns the fatal conflicts from the last resolution.
     *
     * @return an immutable snapshot of the fatal conflicts
     */
    public List<ModConflict> getFatalConflicts() {
        return List.copyOf(fatalConflicts);
    }

    /**
     * Returns the number of fatal conflicts from the last resolution.
     *
     * @return the fatal conflict count
     */
    public int getFatalConflictCount() {
        return fatalConflicts.size();
    }

    /**
     * Writes the deterministic JSON dependency report.
     *
     * @param reportFile the destination file
     * @param resolution the resolution to write
     * @throws IOException if the file cannot be written
     */
    public void writeReport(File reportFile, ModResolution resolution) throws IOException {
        Objects.requireNonNull(reportFile, "reportFile");
        Objects.requireNonNull(resolution, "resolution");
        File absolute = reportFile.getAbsoluteFile();
        File parent = absolute.getParentFile();
        if (parent != null) {
            Files.createDirectories(parent.toPath());
        }

        List<ResolvedMod> mods = new ArrayList<>(resolution.mods());
        mods.sort(modOrder());
        List<ModConflict> conflicts = new ArrayList<>(resolution.conflicts());
        conflicts.sort(conflictOrder());
        List<String> warnings = new ArrayList<>(new TreeSet<>(resolution.warnings()));
        List<File> libraries = new ArrayList<>(resolution.libraries());
        libraries.sort(Comparator.comparing(File::getName).thenComparing(File::getPath));

        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"schemaVersion\": 1,\n");
        json.append("  \"mods\": [");
        if (!mods.isEmpty()) {
            json.append('\n');
            for (int i = 0; i < mods.size(); i++) {
                ResolvedMod mod = mods.get(i);
                json.append("    {\n");
                json.append("      \"id\": ").append(quote(mod.id())).append(",\n");
                json.append("      \"version\": ").append(quote(mod.version())).append(",\n");
                json.append("      \"coordinates\": ").append(quote(mod.coordinates())).append(",\n");
                json.append("      \"requested\": ").append(quote(mod.requested())).append(",\n");
                json.append("      \"file\": ").append(quote(mod.file().getName())).append(",\n");
                json.append("      \"transitive\": ").append(mod.transitive()).append(",\n");
                json.append("      \"optional\": ").append(mod.optional()).append('\n');
                json.append("    }");
                if (i + 1 < mods.size()) {
                    json.append(',');
                }
                json.append('\n');
            }
            json.append("  ");
        }
        json.append("],\n");

        json.append("  \"libraries\": [");
        appendStrings(json, libraries.stream().map(File::getName).toList());
        json.append("],\n");

        json.append("  \"conflicts\": [");
        if (!conflicts.isEmpty()) {
            json.append('\n');
            for (int i = 0; i < conflicts.size(); i++) {
                ModConflict conflict = conflicts.get(i);
                json.append("    {\n");
                json.append("      \"kind\": ").append(quote(conflict.kind())).append(",\n");
                json.append("      \"module\": ").append(quote(conflict.module())).append(",\n");
                json.append("      \"selected\": ").append(quote(conflict.selected())).append(",\n");
                json.append("      \"requested\": [");
                appendInlineStrings(json, conflict.requested());
                json.append("],\n");
                json.append("      \"message\": ").append(quote(conflict.message())).append('\n');
                json.append("    }");
                if (i + 1 < conflicts.size()) {
                    json.append(',');
                }
                json.append('\n');
            }
            json.append("  ");
        }
        json.append("],\n");

        json.append("  \"warnings\": [");
        appendStrings(json, warnings);
        json.append("]\n}\n");

        Files.writeString(absolute.toPath(), json.toString(), StandardCharsets.UTF_8);
    }

    private ResolutionResult resolutionResult(Configuration configuration, Build build) {
        if (configuration == null) {
            return null;
        }
        try {
            return configuration.getIncoming().getResolutionResult();
        } catch (RuntimeException e) {
            build.warnings.add("weaver: cannot resolve " + configuration.getName()
                    + ": " + oneLine(e.getMessage()));
            return null;
        }
    }

    private void addArtifacts(Configuration configuration, boolean optionalFlag, ResolutionResult result,
                              Set<ComponentIdentifier> directComponents,
                              Map<String, SortedSet<String>> requested,
                              Map<ComponentIdentifier, String> componentModules,
                              Map<ComponentIdentifier, String> componentVersions, Build build) {
        if (configuration == null) {
            return;
        }
        for (ResolvedArtifactResult artifact : artifacts(configuration, build)) {
            File file = artifact.getFile();
            ComponentIdentifier component = artifact.getId().getComponentIdentifier();
            String module = componentModules.get(component);
            String selectedVersion = componentVersions.getOrDefault(component, "");
            if (component instanceof ModuleComponentIdentifier moduleId) {
                module = moduleId.getGroup() + ":" + moduleId.getModule();
                selectedVersion = moduleId.getVersion();
            }
            String coordinates;
            if (module != null) {
                coordinates = module + (selectedVersion.isBlank() ? "" : ":" + selectedVersion);
            } else if (component instanceof ProjectComponentIdentifier project) {
                coordinates = "project:" + project.getProjectPath();
            } else {
                coordinates = "local:" + file.getName();
            }
            ModDescriptor descriptor;
            try {
                descriptor = ModDescriptor.read(file);
            } catch (IOException e) {
                build.warnings.add("weaver: " + coordinates + " has no readable weaver.mod.json ("
                        + oneLine(e.getMessage()) + "); treating it as a library");
                build.libraries.add(file);
                continue;
            } catch (IllegalArgumentException e) {
                addInvalid(build, coordinates, file, e);
                continue;
            }
            String requestedValue = requestedValue(module, requested, descriptor.version());
            String depVersion = selectedVersion.isBlank() ? descriptor.version() : selectedVersion;
            boolean opaque = !(component instanceof ModuleComponentIdentifier)
                    && !(component instanceof ProjectComponentIdentifier);
            boolean transitive = !opaque && !directComponents.contains(component);
            ResolvedMod mod = new ResolvedMod(descriptor.id(), descriptor.version(), file, coordinates,
                    requestedValue, transitive, optionalFlag);
            build.candidates.add(new Candidate(mod, depVersion));
        }
    }

    private List<ResolvedArtifactResult> artifacts(Configuration configuration, Build build) {
        try {
            ArtifactCollection collection = configuration.getIncoming()
                    .artifactView(view -> view.setLenient(true))
                    .getArtifacts();
            for (Throwable failure : collection.getFailures()) {
                build.warnings.add("weaver: artifact resolution failed for "
                        + configuration.getName() + ": " + oneLine(failure.getMessage()));
            }
            List<ResolvedArtifactResult> artifacts = new ArrayList<>(collection.getArtifacts());
            artifacts.sort(Comparator.comparing(artifact -> artifact.getFile().getAbsolutePath()));
            return artifacts;
        } catch (RuntimeException e) {
            build.warnings.add("weaver: cannot read artifacts for " + configuration.getName()
                    + ": " + oneLine(e.getMessage()));
            return List.of();
        }
    }

    private void addLocalMods(Build build) {
        for (File file : localFiles(build)) {
            ModDescriptor descriptor;
            try {
                descriptor = ModDescriptor.read(file);
            } catch (IOException e) {
                build.warnings.add("weaver: local " + file.getName()
                        + " has no readable weaver.mod.json (" + oneLine(e.getMessage())
                        + "); treating it as a library");
                build.libraries.add(file);
                continue;
            } catch (IllegalArgumentException e) {
                addInvalid(build, "local:" + file.getName(), file, e);
                continue;
            }
            String version = descriptor.version();
            ResolvedMod mod = new ResolvedMod(descriptor.id(), version, file,
                    "local:" + file.getName(), version, false, false);
            build.candidates.add(new Candidate(mod, version));
        }
    }

    private List<File> localFiles(Build build) {
        if (localMods == null) {
            return List.of();
        }
        List<File> ordered = new ArrayList<>(localMods.getFiles());
        ordered.sort(Comparator.comparing(File::getAbsolutePath));
        Set<File> unique = new LinkedHashSet<>();
        for (File file : ordered) {
            if (file.isDirectory()) {
                try (Stream<Path> stream = Files.walk(file.toPath())) {
                    List<Path> jars = stream.filter(Files::isRegularFile)
                            .filter(path -> path.getFileName().toString().endsWith(".jar"))
                            .sorted(Comparator.comparing(Path::toString))
                            .toList();
                    for (Path jar : jars) {
                        unique.add(canonical(jar.toFile()));
                    }
                } catch (IOException e) {
                    build.warnings.add("weaver: cannot read local mod directory " + file.getName()
                            + ": " + oneLine(e.getMessage()));
                }
            } else if (file.isFile()) {
                unique.add(canonical(file));
            } else {
                build.warnings.add("weaver: local mod file " + file.getName() + " does not exist");
            }
        }
        return new ArrayList<>(unique);
    }

    private void collectUnresolved(ResolutionResult result, boolean optionalFlag, Build build) {
        if (result == null) {
            return;
        }
        for (DependencyResult dependency : result.getAllDependencies()) {
            if (!(dependency instanceof UnresolvedDependencyResult unresolved)) {
                continue;
            }
            ComponentSelector selector = unresolved.getRequested();
            String module;
            String requestedVersion = "";
            if (selector instanceof ModuleComponentSelector moduleSelector) {
                module = moduleSelector.getGroup() + ":" + moduleSelector.getModule();
                requestedVersion = moduleSelector.getVersion() == null ? "" : moduleSelector.getVersion();
            } else {
                module = selector == null ? "<unknown>" : selector.getDisplayName();
            }
            String failure = unresolved.getFailure() == null
                    ? "" : ": " + oneLine(unresolved.getFailure().getMessage());
            String message = "unresolved " + (optionalFlag ? "optional" : "required")
                    + " module " + module
                    + (requestedVersion.isBlank() ? "" : ":" + requestedVersion) + failure;
            if (optionalFlag) {
                build.warnings.add("weaver: " + message);
            } else {
                List<String> requested = requestedVersion.isBlank()
                        ? List.of() : List.of(requestedVersion);
                ModConflict conflict = new ModConflict("missing", module, "", requested, message);
                build.conflicts.add(conflict);
                if (strict) {
                    build.fatal.add(conflict);
                } else {
                    build.warnings.add("weaver: " + message);
                }
            }
        }
    }

    private void collectVersionConflicts(Map<String, SortedSet<String>> requested,
                                         Map<String, String> selected, Build build) {
        for (Map.Entry<String, SortedSet<String>> entry : requested.entrySet()) {
            if (entry.getValue().size() < 2) {
                continue;
            }
            String chosen = selected.get(entry.getKey());
            if (chosen == null) {
                continue;
            }
            List<String> versions = new ArrayList<>(entry.getValue());
            boolean differs = versions.stream().anyMatch(version -> !version.equals(chosen));
            if (!differs) {
                continue;
            }
            String message = "requested " + formatList(versions) + " selected " + chosen;
            ModConflict conflict = new ModConflict("module-version", entry.getKey(), chosen, versions, message);
            build.conflicts.add(conflict);
            if (strict) {
                build.fatal.add(conflict);
            } else {
                build.warnings.add("weaver: " + entry.getKey() + ": " + message);
            }
        }
    }

    private List<Candidate> removeDuplicates(Build build) {
        Map<String, List<Candidate>> groups = new TreeMap<>();
        for (Candidate candidate : build.candidates) {
            groups.computeIfAbsent(candidate.mod().id(), key -> new ArrayList<>()).add(candidate);
        }
        List<Candidate> kept = new ArrayList<>();
        for (Map.Entry<String, List<Candidate>> entry : groups.entrySet()) {
            List<Candidate> group = entry.getValue();
            group.sort(candidateOrder());
            SortedSet<String> versions = new TreeSet<>();
            for (Candidate candidate : group) {
                versions.add(candidate.mod().version());
            }
            if (group.size() == 1) {
                kept.add(group.get(0));
            } else if (versions.size() == 1) {
                Candidate keep = group.get(0);
                String coordinates = group.stream()
                        .map(candidate -> candidate.mod().coordinates())
                        .distinct()
                        .collect(Collectors.joining(", "));
                build.warnings.add("weaver: duplicate mod id '" + entry.getKey() + "' version "
                        + versions.first() + " from " + coordinates + "; keeping "
                        + keep.mod().coordinates());
                kept.add(keep);
            } else {
                kept.addAll(group);
                String files = group.stream()
                        .map(candidate -> candidate.mod().file().getName()
                                + " (" + candidate.mod().version() + ")")
                        .collect(Collectors.joining(", "));
                ModConflict conflict = new ModConflict("mod-id", entry.getKey(), "",
                        new ArrayList<>(versions),
                        "duplicate mod id '" + entry.getKey() + "' declared by " + files);
                build.conflicts.add(conflict);
                build.fatal.add(conflict);
            }
        }
        return kept;
    }

    private void addInvalid(Build build, String module, File file, IllegalArgumentException error) {
        ModConflict conflict = new ModConflict("invalid", module, "", List.of(),
                "invalid descriptor in " + file.getName() + ": " + oneLine(error.getMessage()));
        build.conflicts.add(conflict);
        build.fatal.add(conflict);
    }

    private void print(List<Candidate> candidates, List<ModConflict> conflicts,
                       List<String> warnings, List<File> libraries) {
        for (Candidate candidate : candidates) {
            ResolvedMod mod = candidate.mod();
            emit("WEAVER DEP module=" + mod.coordinates()
                    + " requested=" + mod.requested()
                    + " selected=" + candidate.selected()
                    + " id=" + mod.id()
                    + " version=" + mod.version()
                    + " file=" + mod.file().getName()
                    + " transitive=" + mod.transitive()
                    + " optional=" + mod.optional());
        }
        for (String warning : warnings) {
            warn(warning);
        }
        for (ModConflict conflict : conflicts) {
            emit("WEAVER DEP CONFLICT kind=" + conflict.kind()
                    + " module=" + conflict.module()
                    + " selected=" + conflict.selected()
                    + " requested=" + formatList(conflict.requested())
                    + " message=" + oneLine(conflict.message()));
        }
        if (fatalConflicts.isEmpty()) {
            long optionalCount = candidates.stream().filter(candidate -> candidate.mod().optional()).count();
            emit("WEAVER DEPS ok=true mods=" + candidates.size()
                    + " optional=" + optionalCount
                    + " libraries=" + libraries.size()
                    + " conflicts=" + conflicts.size());
        }
    }

    private void emit(String line) {
        if (logger != null) {
            logger.lifecycle(line);
        } else {
            System.out.println(line);
        }
    }

    private void warn(String line) {
        if (logger != null) {
            logger.warn(line);
        } else {
            System.out.println(line);
        }
    }

    private static Set<ComponentIdentifier> directComponents(ResolutionResult result) {
        Set<ComponentIdentifier> out = new HashSet<>();
        if (result == null) {
            return out;
        }
        for (DependencyResult dependency : result.getRoot().getDependencies()) {
            if (dependency instanceof ResolvedDependencyResult resolved) {
                out.add(resolved.getSelected().getId());
            }
        }
        return out;
    }

    private static Map<ComponentIdentifier, String> componentModules(ResolutionResult result) {
        Map<ComponentIdentifier, String> out = new LinkedHashMap<>();
        if (result == null) {
            return out;
        }
        for (ResolvedComponentResult component : result.getAllComponents()) {
            ModuleVersionIdentifier id = component.getModuleVersion();
            if (id != null && id.getGroup() != null && !id.getName().isBlank()) {
                out.put(component.getId(), id.getGroup() + ":" + id.getName());
            }
        }
        return out;
    }

    private static Map<ComponentIdentifier, String> componentVersions(ResolutionResult result) {
        Map<ComponentIdentifier, String> out = new LinkedHashMap<>();
        if (result == null) {
            return out;
        }
        for (ResolvedComponentResult component : result.getAllComponents()) {
            ModuleVersionIdentifier id = component.getModuleVersion();
            if (id != null && id.getVersion() != null) {
                out.put(component.getId(), id.getVersion());
            }
        }
        return out;
    }

    private static Map<String, String> selectedByModule(ResolutionResult result) {
        Map<String, String> selected = new TreeMap<>();
        if (result == null) {
            return selected;
        }
        for (ResolvedComponentResult component : result.getAllComponents()) {
            ModuleVersionIdentifier id = component.getModuleVersion();
            if (id != null && id.getGroup() != null && !id.getName().isBlank()) {
                selected.put(id.getGroup() + ":" + id.getName(), id.getVersion());
            }
        }
        return selected;
    }

    private static Map<String, SortedSet<String>> requestedByModule(ResolutionResult result) {
        Map<String, SortedSet<String>> requested = new TreeMap<>();
        if (result == null) {
            return requested;
        }
        for (DependencyResult dependency : result.getAllDependencies()) {
            if (dependency.isConstraint()) {
                continue;
            }
            ComponentSelector selector = dependency.getRequested();
            if (selector instanceof ModuleComponentSelector module) {
                addRequested(requested, module.getGroup() + ":" + module.getModule(),
                        module.getVersion());
            }
        }
        if (requested.isEmpty()) {
            for (ResolvedComponentResult component : result.getAllComponents()) {
                ModuleVersionIdentifier id = component.getModuleVersion();
                if (id != null && id.getGroup() != null && !id.getName().isBlank()) {
                    addRequested(requested, id.getGroup() + ":" + id.getName(), id.getVersion());
                }
            }
        }
        return requested;
    }

    private static void addRequested(Map<String, SortedSet<String>> requested,
                                     String module, String version) {
        if (module == null || version == null || version.isBlank()) {
            return;
        }
        requested.computeIfAbsent(module, key -> new TreeSet<>()).add(version);
    }

    private static String requestedValue(String module, Map<String, SortedSet<String>> requested,
                                         String fallback) {
        if (module != null) {
            SortedSet<String> versions = requested.get(module);
            if (versions != null && !versions.isEmpty()) {
                return String.join(",", versions);
            }
        }
        return fallback == null || fallback.isBlank() ? "-" : fallback;
    }

    private static File canonical(File file) {
        try {
            return file.getCanonicalFile();
        } catch (IOException e) {
            return file.getAbsoluteFile();
        }
    }

    private static Comparator<ResolvedMod> modOrder() {
        return Comparator.comparing(ResolvedMod::id)
                .thenComparing(ResolvedMod::version)
                .thenComparing(mod -> mod.file().getName())
                .thenComparing(ResolvedMod::coordinates);
    }

    private static Comparator<Candidate> candidateOrder() {
        return Comparator.comparing((Candidate candidate) -> candidate.mod().id())
                .thenComparing(candidate -> candidate.mod().version())
                .thenComparing(candidate -> candidate.mod().optional())
                .thenComparing(candidate -> candidate.mod().file().getName())
                .thenComparing(candidate -> candidate.mod().coordinates());
    }

    private static Comparator<ModConflict> conflictOrder() {
        return Comparator.comparing(ModConflict::kind)
                .thenComparing(ModConflict::module)
                .thenComparing(ModConflict::selected)
                .thenComparing(conflict -> String.join(",", conflict.requested()))
                .thenComparing(ModConflict::message);
    }

    private static String formatList(List<String> values) {
        return values.stream().collect(Collectors.joining(",", "[", "]"));
    }

    private static void appendStrings(StringBuilder json, List<String> values) {
        if (values.isEmpty()) {
            return;
        }
        json.append('\n');
        for (int i = 0; i < values.size(); i++) {
            json.append("    ").append(quote(values.get(i)));
            if (i + 1 < values.size()) {
                json.append(',');
            }
            json.append('\n');
        }
        json.append("  ");
    }

    private static void appendInlineStrings(StringBuilder json, List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                json.append(", ");
            }
            json.append(quote(values.get(i)));
        }
    }

    private static String quote(String value) {
        String text = value == null ? "" : value;
        StringBuilder out = new StringBuilder(text.length() + 2);
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            switch (ch) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        out.append(String.format("\\u%04x", (int) ch));
                    } else {
                        out.append(ch);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    private static String oneLine(String value) {
        return value == null ? "" : value.replace('\r', ' ').replace('\n', ' ').trim();
    }

    private record Candidate(ResolvedMod mod, String selected) {
    }

    private static final class Build {
        private final List<Candidate> candidates = new ArrayList<>();
        private final List<ModConflict> conflicts = new ArrayList<>();
        private final List<ModConflict> fatal = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private final List<File> libraries = new ArrayList<>();
    }
}
