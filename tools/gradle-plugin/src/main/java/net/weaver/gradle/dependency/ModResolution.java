package net.weaver.gradle.dependency;

import java.io.File;
import java.util.List;

/**
 * The deterministic result of resolving a mod dependency graph.
 *
 * <p>Lists are immutable, sorted, and never null. Optional mods are included in
 * {@link #mods()} with {@link ResolvedMod#optional()} set to {@code true}; the
 * plugin excludes them from the default embed set.
 *
 * @param mods the resolved mods, sorted by id, version, and file name
 * @param conflicts the detected conflicts, sorted by kind, module, and message
 * @param warnings the non-fatal diagnostics, sorted and deduplicated
 * @param libraries artifacts without a descriptor, sorted by path
 */
public record ModResolution(
        List<ResolvedMod> mods,
        List<ModConflict> conflicts,
        List<String> warnings,
        List<File> libraries) {

    /** Creates a resolution with immutable, non-null lists. */
    public ModResolution {
        mods = mods == null ? List.of() : List.copyOf(mods);
        conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        libraries = libraries == null ? List.of() : List.copyOf(libraries);
    }
}
