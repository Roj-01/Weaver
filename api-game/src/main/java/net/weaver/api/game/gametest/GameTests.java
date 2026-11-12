package net.weaver.api.game.gametest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.flag.FeatureFlagSet;

/**
 * Game tests for mods, run by vanilla's game test framework.
 *
 * <pre>{@code
 * GameTests.register(Identifier.fromNamespaceAndPath("examplemod", "ruby_block_places"), helper -> {
 *     helper.setBlock(0, 0, 0, RUBY_BLOCK);
 *     helper.assertBlockPresent(RUBY_BLOCK, 0, 0, 0);
 *     helper.succeed();
 * });
 * }</pre>
 *
 * <p>Call it from {@code onInitialize}: it registers the function in vanilla's
 * test function registry and generates the matching {@code test_instance}
 * entry, so no data files are needed. {@link #test} sets the structure, time
 * limit, and whether a failure fails the run. Tests run with the game test
 * server ({@code net.minecraft.gametest.Main}), which the Gradle plugin's
 * {@code weaverGameTest} task starts with the Weaver agent; any server can
 * also run them with the vanilla {@code /test} command.</p>
 */
public final class GameTests {
    /** Vanilla's 1x1x1 empty structure, the default test area. */
    public static final Identifier EMPTY_STRUCTURE = Identifier.withDefaultNamespace("empty");
    /** Vanilla's default test environment. */
    public static final Identifier DEFAULT_ENVIRONMENT = Identifier.withDefaultNamespace("default");

    /** One registered test. */
    public record Definition(Identifier id, Identifier structure, Identifier environment, int maxTicks,
                             int setupTicks, boolean required) {
    }

    private static final List<Definition> DEFINITIONS = new ArrayList<>();
    private static volatile Path generatedPack;

    private GameTests() {
    }

    /** Registers a required test in the empty structure with a 100-tick limit. */
    public static void register(Identifier id, Consumer<GameTestHelper> test) {
        test(id).register(test);
    }

    /** Starts a test with options. */
    public static Builder test(Identifier id) {
        return new Builder(Objects.requireNonNull(id, "id"));
    }

    /** The registered tests, in registration order. */
    public static synchronized List<Definition> definitions() {
        return List.copyOf(DEFINITIONS);
    }

    /** Options for one test. */
    public static final class Builder {
        private final Identifier id;
        private Identifier structure = EMPTY_STRUCTURE;
        private Identifier environment = DEFAULT_ENVIRONMENT;
        private int maxTicks = 100;
        private int setupTicks = 0;
        private boolean required = true;

        private Builder(Identifier id) {
            this.id = id;
        }

        /** The structure placed for the test ({@code data/<ns>/structure/<path>.nbt}). */
        public Builder structure(Identifier value) {
            structure = Objects.requireNonNull(value, "structure");
            return this;
        }

        /** The test environment (game rules, time, weather), default {@code minecraft:default}. */
        public Builder environment(Identifier value) {
            environment = Objects.requireNonNull(value, "environment");
            return this;
        }

        /** Ticks before the test times out and fails. */
        public Builder maxTicks(int value) {
            if (value <= 0) {
                throw new IllegalArgumentException("maxTicks must be positive");
            }
            maxTicks = value;
            return this;
        }

        /** Ticks to wait after placing the structure before the test starts. */
        public Builder setupTicks(int value) {
            if (value < 0) {
                throw new IllegalArgumentException("setupTicks must not be negative");
            }
            setupTicks = value;
            return this;
        }

        /** Whether a failure fails the run (default {@code true}). */
        public Builder required(boolean value) {
            required = value;
            return this;
        }

        /**
         * Registers the test. Must run before the game freezes its built-in
         * registries, which is during mod initialization.
         *
         * @throws IllegalStateException when called too late or twice for one id
         */
        public void register(Consumer<GameTestHelper> test) {
            Objects.requireNonNull(test, "test");
            synchronized (GameTests.class) {
                for (Definition existing : DEFINITIONS) {
                    if (existing.id().equals(id)) {
                        throw new IllegalStateException("game test already registered: " + id);
                    }
                }
                try {
                    Registry.register(BuiltInRegistries.TEST_FUNCTION, id, test);
                } catch (IllegalStateException frozen) {
                    throw new IllegalStateException("game test " + id
                            + " registered too late; register tests in onInitialize", frozen);
                }
                DEFINITIONS.add(new Definition(id, structure, environment, maxTicks, setupTicks, required));
            }
        }
    }

    /**
     * The generated data pack holding a {@code test_instance} for every
     * registered test, or empty when there are none. Called when the server
     * builds its pack list.
     */
    public static Optional<Pack> pack(PackType type) {
        List<Definition> tests = definitions();
        if (type != PackType.SERVER_DATA || tests.isEmpty()) {
            return Optional.empty();
        }
        Path root;
        try {
            root = writePack(tests);
        } catch (IOException failure) {
            System.out.println("[weaver] GAME TESTS pack not written reason=" + failure);
            return Optional.empty();
        }
        PackLocationInfo location = new PackLocationInfo("weaver/gametests", Component.literal("Weaver game tests"),
                PackSource.BUILT_IN, Optional.empty());
        Pack.Metadata metadata = new Pack.Metadata(Component.literal("Test instances for mod game tests"),
                PackCompatibility.COMPATIBLE, FeatureFlagSet.of(), List.of());
        return Optional.of(new Pack(location, new PathPackResources.PathResourcesSupplier(root), metadata,
                new PackSelectionConfig(true, Pack.Position.TOP, false)));
    }

    private static synchronized Path writePack(List<Definition> tests) throws IOException {
        Path root = generatedPack;
        if (root == null) {
            root = Files.createTempDirectory("weaver-gametests");
            deleteOnExit(root);
            generatedPack = root;
        }
        for (Definition test : tests) {
            Path file = root.resolve("data").resolve(test.id().getNamespace()).resolve("test_instance")
                    .resolve(test.id().getPath() + ".json");
            Files.createDirectories(file.getParent());
            Files.writeString(file, json(test), StandardCharsets.UTF_8);
        }
        return root;
    }

    /** File.deleteOnExit cannot remove a non-empty folder, so delete the tree in a shutdown hook. */
    private static void deleteOnExit(Path root) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            } catch (IOException | RuntimeException ignored) {
                // Best effort: the folder is only test instance files.
            }
        }, "weaver-gametests-cleanup"));
    }

    static String json(Definition test) {
        return "{\n"
                + "  \"type\": \"minecraft:function\",\n"
                + "  \"function\": \"" + test.id() + "\",\n"
                + "  \"structure\": \"" + test.structure() + "\",\n"
                + "  \"environment\": \"" + test.environment() + "\",\n"
                + "  \"max_ticks\": " + test.maxTicks() + ",\n"
                + "  \"setup_ticks\": " + test.setupTicks() + ",\n"
                + "  \"required\": " + test.required() + "\n"
                + "}\n";
    }
}
