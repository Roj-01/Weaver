import net.weaver.core.ModLoader;
import net.weaver.core.jij.JijScanner;
import net.weaver.core.jij.NestedJar;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Loader-side proof for the toolchain fixture jar-in-jar layout. */
public final class LoaderProof {

    private static final List<String> REQUIRED_MODS = List.of(
            "toolchain-fixture",
            "fixture-nested",
            "fixture-lib",
            "fixture-transitive");

    private static final List<String> EXPECTED_NESTED = List.of(
            "META-INF/weaver/jars/fixture-lib-1.0.0.jar",
            "META-INF/weaver/jars/fixture-nested-1.0.0.jar",
            "META-INF/weaver/jars/fixture-transitive-1.0.0.jar");

    private static final Map<String, String> EXPECTED_NESTED_VERSIONS = Map.of(
            "fixture-lib", "1.0.0",
            "fixture-transitive", "1.0.0",
            "fixture-nested", "1.0.0");

    private LoaderProof() {
    }

    public static void main(String[] args) {
        try {
            run(args);
        } catch (Throwable failure) {
            fail("exception=" + failure.getClass().getName()
                    + " message=" + failure.getMessage());
        }
    }

    private static void run(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                    "usage: LoaderProof <main-jar> <jij-export-dir>");
        }
        Path mainJar = Path.of(args[0]).toAbsolutePath().normalize();
        Path exportDir = Path.of(args[1]).toAbsolutePath().normalize();
        if (!Files.isRegularFile(mainJar)) {
            throw new IllegalArgumentException("main jar is missing: " + mainJar);
        }
        if (!Files.isDirectory(exportDir)) {
            throw new IllegalArgumentException("jij export directory is missing: " + exportDir);
        }

        Path temp = Files.createTempDirectory("weaver-toolchain-fixture-");
        try {
            Files.copy(mainJar, temp.resolve(mainJar.getFileName()),
                    StandardCopyOption.REPLACE_EXISTING);

            List<Path> extracted = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(exportDir, "jij-*.jar")) {
                for (Path path : stream) {
                    extracted.add(path);
                }
            }
            extracted.sort(Comparator.comparing(
                    (Path path) -> path.getFileName().toString()));
            if (extracted.size() != EXPECTED_NESTED.size()) {
                throw new IllegalStateException("expected " + EXPECTED_NESTED.size()
                        + " jij jars in " + exportDir + " but found " + extracted.size());
            }
            for (Path path : extracted) {
                Files.copy(path, temp.resolve(path.getFileName()),
                        StandardCopyOption.REPLACE_EXISTING);
            }

            List<String> accepted;
            try (ModLoader loader = ModLoader.discover(temp, "server", System.out::println)) {
                accepted = loader.mods().stream()
                        .map(mod -> mod.metadata().id())
                        .toList();
            }
            for (String id : REQUIRED_MODS) {
                if (!accepted.contains(id)) {
                    throw new IllegalStateException("accepted mods are missing '" + id
                            + "': " + accepted);
                }
            }
            if (accepted.contains("fixture-optional")) {
                throw new IllegalStateException("fixture-optional must not be accepted: " + accepted);
            }

            List<NestedJar> nested = JijScanner.scan(mainJar);
            List<NestedJar> repeated = JijScanner.scan(mainJar);
            if (!nested.equals(repeated)) {
                throw new IllegalStateException("jij scan order is not deterministic");
            }
            if (nested.size() != EXPECTED_NESTED.size()) {
                throw new IllegalStateException("expected " + EXPECTED_NESTED.size()
                        + " nested jars but found " + nested.size());
            }
            List<String> actualOrder = new ArrayList<>();
            for (NestedJar jar : nested) {
                actualOrder.add(jar.entryName());
                String expectedVersion = EXPECTED_NESTED_VERSIONS.get(jar.id());
                if (expectedVersion == null) {
                    throw new IllegalStateException("unexpected nested mod id: " + jar.id());
                }
                if (!expectedVersion.equals(jar.version())) {
                    throw new IllegalStateException("nested mod " + jar.id()
                            + " has version " + jar.version() + ", expected " + expectedVersion);
                }
            }
            if (!actualOrder.equals(EXPECTED_NESTED)) {
                throw new IllegalStateException("nested jar order differs: actual="
                        + actualOrder + " expected=" + EXPECTED_NESTED);
            }

            System.out.println("TOOLCHAINFIXTURE LOADER ok=true mods="
                    + accepted.size() + " nested=" + nested.size());
        } finally {
            deleteRecursively(temp);
        }
    }

    private static void fail(String reason) {
        System.out.println("TOOLCHAINFIXTURE LOADER ok=false reason=" + reason);
        System.exit(1);
    }

    private static void deleteRecursively(Path dir) {
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // best-effort temp cleanup
                }
            });
        } catch (IOException ignored) {
            // best-effort temp cleanup
        }
    }
}
