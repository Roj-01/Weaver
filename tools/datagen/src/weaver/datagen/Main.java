package weaver.datagen;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * CLI entry point.
 *
 * <pre>
 *   datagen.sh [--manifest datagen.json] [--out out] [--verify] [--quiet] [--fingerprint]
 *   datagen.sh --merge &lt;priority&gt;=&lt;dir&gt; [--merge ...] [--out out] [--quiet]
 * </pre>
 *
 * <p>Generates the tree, prints one {@code DATAGEN FILE <rel>} line per file and, last,
 * {@code DATAGEN ok=true files=<n> dir=<path>}. {@code --verify} re-reads everything from disk
 * and prints {@code DATAGEN verify ok=true files=<n> dir=<path>} only when every check passes.
 * {@code --fingerprint} adds {@code DATAGEN FINGERPRINT <hex>} after a successful generation or
 * verification.
 *
 * <p>{@code --merge} switches to merge mode: the given generated pack directories are combined
 * into {@code --out} by priority instead of generating from a manifest.
 *
 * <p>Exit codes: {@code 0} success · {@code 2} bad usage or rejected manifest (nothing written) ·
 * {@code 3} verification failed, or a merge reported problems/conflicts.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), true,
                StandardCharsets.UTF_8);
        System.setOut(out);
        int code = run(args, out, System.err);
        out.flush();
        System.exit(code);
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        Path manifestPath = Path.of("datagen.json");
        Path outDir = Path.of("out");
        boolean verify = false;
        boolean quiet = false;
        boolean fingerprint = false;
        Path gameJar = null;
        List<String> biomeMerges = new ArrayList<>();
        List<PackMerger.Layer> mergeLayers = new ArrayList<>();
        String namespace = "weaver";

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--manifest", "-m" -> {
                    if (++i >= args.length) {
                        err.println("datagen: --manifest needs a path");
                        return 2;
                    }
                    manifestPath = Path.of(args[i]);
                }
                case "--out", "-o" -> {
                    if (++i >= args.length) {
                        err.println("datagen: --out needs a path");
                        return 2;
                    }
                    outDir = Path.of(args[i]);
                }
                case "--game-jar", "-g" -> {
                    if (i + 1 >= args.length) {
                        err.println("datagen: --game-jar needs a path");
                        return 2;
                    }
                    gameJar = Path.of(args[++i]);
                }
                case "--biome-merge" -> {
                    if (i + 1 >= args.length) {
                        err.println("datagen: --biome-merge needs <biome>=<feature>@<step>");
                        return 2;
                    }
                    biomeMerges.add(args[++i]);
                }
                case "--merge" -> {
                    if (i + 1 >= args.length) {
                        err.println("datagen: --merge needs <priority>=<dir>");
                        return 2;
                    }
                    String spec = args[++i];
                    int eq = spec.indexOf('=');
                    if (eq <= 0 || eq == spec.length() - 1) {
                        err.println("datagen: --merge expects <priority>=<dir>, got '" + spec + "'");
                        return 2;
                    }
                    int priority;
                    try {
                        priority = Integer.parseInt(spec.substring(0, eq));
                    } catch (NumberFormatException e) {
                        err.println("datagen: --merge priority is not an integer: '"
                                + spec.substring(0, eq) + "'");
                        return 2;
                    }
                    Path layerDir = Path.of(spec.substring(eq + 1));
                    mergeLayers.add(new PackMerger.Layer(layerDir, priority,
                            slashes(layerDir.normalize())));
                }
                case "--verify" -> verify = true;
                case "--fingerprint" -> fingerprint = true;
                case "--quiet", "-q" -> quiet = true;
                case "--help", "-h" -> {
                    out.println("usage: datagen.sh [--manifest <datagen.json>] [--out <dir>]"
                            + " [--verify] [--quiet] [--fingerprint]");
                    out.println("       datagen.sh --merge <priority>=<dir> [--merge ...]"
                            + " [--out <dir>] [--quiet]");
                    out.println("  generates blockstates/models/item models/lang/recipes/"
                            + "loot tables/tags/advancements/registries for one Weaver module");
                    out.println("  --verify       re-parse every generated file and check its"
                            + " 26.3 keys");
                    out.println("  --fingerprint  print the SHA-256 fingerprint of the output tree");
                    out.println("  --merge P=DIR  merge a generated pack instead of generating"
                            + " (repeatable)");
                    return 0;
                }
                default -> {
                    err.println("datagen: unknown argument '" + args[i] + "' (try --help)");
                    return 2;
                }
            }
        }

        outDir = outDir.toAbsolutePath().normalize();
        manifestPath = manifestPath.toAbsolutePath().normalize();

        if (!mergeLayers.isEmpty()) {
            return runMerge(mergeLayers, outDir, quiet, out);
        }

        // --verify alone (no manifest to generate from) only re-checks an existing tree.
        boolean willGenerate = !verify || Files.exists(manifestPath);
        if (!willGenerate && !Files.isDirectory(outDir)) {
            err.println("DATAGEN verify ok=false reason=no-output dir=" + slashes(outDir));
            return 3;
        }

        int generated = 0;
        if (willGenerate) {
            Manifest manifest;
            try {
                manifest = Manifest.load(manifestPath);
            } catch (Manifest.Invalid e) {
                for (String error : e.errors()) {
                    out.println("DATAGEN REJECT " + error);
                }
                out.println("DATAGEN ok=false errors=" + e.errors().size()
                        + " manifest=" + slashes(manifestPath));
                return 2;
            } catch (IOException e) {
                out.println("DATAGEN ok=false reason=unreadable-manifest manifest="
                        + slashes(manifestPath) + " detail=" + oneLine(e));
                return 2;
            }

            Map<String, byte[]> files = Generator.generate(manifest);
            try {
                prepare(outDir, out);
                for (Map.Entry<String, byte[]> e : files.entrySet()) {
                    Path target = outDir.resolve(e.getKey());
                    Files.createDirectories(target.getParent());
                    Files.write(target, e.getValue());
                    if (!quiet) {
                        out.println("DATAGEN FILE " + e.getKey() + " bytes=" + e.getValue().length);
                    }
                }
                Files.writeString(outDir.resolve(".weaver-datagen"),
                        "generated by tools/weaver-datagen — safe to delete\n");
                generated = files.size();
            } catch (IOException e) {
                out.println("DATAGEN ok=false reason=write-failed detail=" + oneLine(e));
                return 2;
            }

            namespace = manifest.namespace();
            out.println("DATAGEN GEN namespace=" + manifest.namespace()
                    + " blocks=" + manifest.blocks().size()
                    + " items=" + manifest.items().size()
                    + " recipes=" + manifest.recipes().size()
                    + " tagKinds=" + manifest.tags().size());
        }

        // Biome merges run after the generated tree exists, so a module ships one pack that holds
        // both its own content and the biome files it had to override.
        int merged = 0;
        for (String spec : biomeMerges) {
            int at = spec.indexOf('@');
            int eq = spec.indexOf('=');
            if (eq <= 0 || at <= eq + 1) {
                out.println("DATAGEN BIOME ok=false spec=" + spec + " reason=expected-<biome>=<feature>@<step>");
                continue;
            }
            String biome = spec.substring(0, eq);
            String feature = spec.substring(eq + 1, at);
            String step = spec.substring(at + 1);
            BiomeMerge.Result result = BiomeMerge.merge(gameJar, namespace, biome, feature, step, outDir);
            if (result.ok()) {
                merged++;
                out.println("DATAGEN BIOME ok=true biome=" + biome + " feature=" + feature
                        + " step=" + step + " file=" + slashes(result.file()));
            } else {
                out.println("DATAGEN BIOME ok=false biome=" + biome + " feature=" + feature
                        + " step=" + step + " reason=" + result.reason());
            }
        }
        if (merged > 0) {
            generated += merged;
        }

        if (!verify) {
            out.println("DATAGEN ok=true files=" + generated + " dir=" + slashes(outDir));
            if (fingerprint && !printFingerprint(outDir, out)) {
                return 2;
            }
            return 0;
        }

        Verifier.Report report;
        try {
            report = Verifier.verify(outDir);
        } catch (IOException e) {
            out.println("DATAGEN verify ok=false reason=read-failed detail=" + oneLine(e));
            return 3;
        }
        for (String warning : report.warnings()) {
            out.println("DATAGEN WARN " + warning);
        }
        for (String problem : report.problems()) {
            out.println("DATAGEN FAIL " + problem);
        }
        if (!report.ok()) {
            out.println("DATAGEN verify ok=false files=" + report.files().size()
                    + " problems=" + report.problems().size() + " dir=" + slashes(outDir));
            return 3;
        }
        if (!quiet) {
            for (String file : report.files()) {
                out.println("DATAGEN CHECK " + file + " ok=true");
            }
        }
        out.println("DATAGEN verify ok=true files=" + report.files().size()
                + " dir=" + slashes(outDir));
        if (fingerprint && !printFingerprint(outDir, out)) {
            return 3;
        }
        return 0;
    }

    private static int runMerge(List<PackMerger.Layer> layers, Path outDir, boolean quiet,
                                PrintStream out) {
        for (PackMerger.Layer layer : layers) {
            out.println("DATAGEN MERGE layer=" + layer.id()
                    + " priority=" + layer.priority()
                    + " dir=" + slashes(layer.root().toAbsolutePath().normalize()));
        }
        PackMerger.Report report;
        try {
            report = PackMerger.merge(layers, outDir);
        } catch (IOException e) {
            out.println("DATAGEN MERGE ok=false reason=merge-failed detail=" + oneLine(e)
                    + " dir=" + slashes(outDir));
            return 2;
        }
        if (!quiet) {
            for (String file : report.files()) {
                out.println("DATAGEN MERGE FILE " + file);
            }
        }
        out.println("DATAGEN MERGE ok=" + report.ok()
                + " files=" + report.files().size()
                + " conflicts=" + report.conflicts().size()
                + " problems=" + report.problems().size()
                + " dir=" + slashes(outDir));
        for (PackMerger.Problem problem : report.problems()) {
            out.println("DATAGEN WARN " + problem);
        }
        for (String conflict : report.conflicts()) {
            out.println("DATAGEN CONFLICT " + conflict);
        }
        if (!report.ok()) {
            return 3;
        }
        out.println("DATAGEN MERGE FINGERPRINT " + report.fingerprint());
        return 0;
    }

    private static boolean printFingerprint(Path outDir, PrintStream out) {
        try {
            out.println("DATAGEN FINGERPRINT " + Verifier.fingerprint(outDir));
            return true;
        } catch (IOException e) {
            out.println("DATAGEN fingerprint ok=false reason=read-failed detail=" + oneLine(e));
            return false;
        }
    }

    /**
     * Deletes a previous run so the file count on the summary line is the file count on disk.
     * Only a directory this tool marked (or an empty one) is removed; anything else is left
     * alone and the run writes into it.
     */
    private static void prepare(Path outDir, PrintStream out) throws IOException {
        if (!Files.isDirectory(outDir)) {
            Files.createDirectories(outDir);
            return;
        }
        boolean marked = Files.exists(outDir.resolve(".weaver-datagen"));
        boolean empty;
        try (Stream<Path> list = Files.list(outDir)) {
            empty = list.findAny().isEmpty();
        }
        if (!marked && !empty) {
            out.println("DATAGEN WARN out-dir not created by this tool, leaving existing files:"
                    + " " + slashes(outDir));
            return;
        }
        List<Path> paths = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(outDir)) {
            walk.forEach(paths::add);
        }
        paths.sort(Comparator.reverseOrder());
        for (Path path : paths) {
            Files.deleteIfExists(path);
        }
    }

    private static String slashes(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static String oneLine(Throwable t) {
        String message = t.getMessage();
        return (message == null ? t.getClass().getSimpleName() : message).replace('\n', ' ')
                .replace('\r', ' ');
    }
}
