package net.weaver.core.registry;

import net.weaver.api.registry.DataPackSnapshot;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Compatibility facade for reading one Weaver data-pack directory.
 *
 * <p>This class delegates to {@link DataPackRepository} with a single pack and
 * keeps the original {@link Report} shape. New integrations should read an
 * ordered set of packs with {@link DataPackRepository#load(List)} so pack
 * precedence and {@code pack.mcmeta} metadata are available. A single-pack
 * read through this method produces the same entries, tags, and problems as
 * before; file-size and count limits from the repository also apply.</p>
 *
 * <p>The expected layout under the pack root is:</p>
 * <pre>
 * data/&lt;packNamespace&gt;/weaver/registry/&lt;registryNamespace&gt;/&lt;registryPath&gt;/&lt;entryPath&gt;.json
 * data/&lt;packNamespace&gt;/weaver/tags/&lt;registryNamespace&gt;/&lt;registryPath&gt;/&lt;tagPath&gt;.json
 * </pre>
 *
 * <p>Only regular {@code .json} files are read. Symbolic links are not
 * followed, and paths that escape the pack root, contain {@code ..}, use
 * backslashes, or are absolute are rejected. Parse and layout problems are
 * collected in the report instead of being thrown; the snapshot is returned
 * even when some files fail. This class has no game imports.</p>
 */
public final class DataPackLoader
{
    /**
     * The result of reading one pack layer.
     *
     * @param snapshot the successfully parsed snapshot, never {@code null}
     * @param problems human-readable problems with pack-relative paths
     */
    public record Report(DataPackSnapshot snapshot, List<String> problems)
    {
        public Report
        {
            Objects.requireNonNull(snapshot, "snapshot");
            Objects.requireNonNull(problems, "problems");
            problems = List.copyOf(problems);
        }
    }

    private DataPackLoader()
    {
    }

    /**
     * Reads all Weaver data under {@code packRoot}.
     *
     * @param packRoot the data pack root containing {@code data/}, not
     *                 {@code null}
     * @param packId the source label stored in the snapshot, not {@code null}
     * @param priority the default priority for entries and tags
     * @return the snapshot and any problems, never {@code null}
     */
    public static Report read(Path packRoot, String packId, int priority)
    {
        Objects.requireNonNull(packRoot, "packRoot");
        Objects.requireNonNull(packId, "packId");
        final DataPackRepository.Report report =
                DataPackRepository.readSingle(packRoot, packId, priority);
        final DataPackSnapshot snapshot = report.snapshots().isEmpty()
                ? DataPackSnapshot.builder(packId, priority).build()
                : report.snapshots().get(0);
        return new Report(snapshot, report.problems());
    }
}
