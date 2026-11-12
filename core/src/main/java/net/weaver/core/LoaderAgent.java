package net.weaver.core;

import net.weaver.api.WeaverApi;
import net.weaver.api.registry.RegistryManager;
import net.weaver.api.spi.GameEventFactory;
import net.weaver.core.at.AccessTransformer;
import net.weaver.core.at.AccessTransformerTransformer;
import net.weaver.core.hook.GameEventFactoryHolder;
import net.weaver.core.hook.GameHookTransformer;
import net.weaver.core.lifecycle.ClientInitTransform;
import net.weaver.core.lifecycle.RegistryTransform;
import net.weaver.core.lifecycle.ServerInitTransform;
import net.weaver.core.modops.ModDiagnostic;
import net.weaver.core.modops.ModList;
import net.weaver.core.modops.ModPolicies;
import net.weaver.core.modops.ModPolicyException;
import net.weaver.core.modops.ModReportWriter;
import net.weaver.core.network.NetworkBridgeInstall;
import net.weaver.core.registry.MinecraftRegistryBackend;
import net.weaver.core.version.VersionChecks;
import net.weaver.mixin.MixinBridge;
import net.weaver.mixin.core.MixinEngine;
import net.weaver.mixin.core.WeaverMixinTransformer;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Java agent entry point. */
public final class LoaderAgent {

    private static ModLoader loader;
    private static ClassSpace space;
    private static MixinEngine engine;

    private LoaderAgent() {
    }

    /**
     * Returns the class space created by the agent, or {@code null} when the
     * agent has not completed class-space setup.
     *
     * <p>{@link SharedMain} uses this to load the game main class from the
     * shared loader.
     *
     * @return the installed class space, or {@code null}
     */
    public static ClassSpace classSpace() {
        return space;
    }

    public static void premain(String args, Instrumentation instrumentation) {
        PrintWriter markers = null;
        try {
            Map<String, String> options = parse(args);
            String side = options.getOrDefault("side",
                    System.getProperty("weaver.side", "client"));
            String modsValue = options.getOrDefault("modsDir",
                    options.getOrDefault("modulesDir",
                            System.getProperty("weaver.modsDir", "mods")));
            Path modsDir = Path.of(modsValue);
            Path configDir = Path.of(options.getOrDefault("configDir",
                    System.getProperty("weaver.configDir", "weaver/config")));
            Path dataDir = Path.of(options.getOrDefault("dataDir",
                    System.getProperty("weaver.dataDir", "weaver/data")));
            String markersFile = options.getOrDefault("markersFile",
                    System.getProperty("weaver.markersFile", ""));
            if (!markersFile.isBlank()) {
                // The markers file is a diagnostic copy of the log: failing to open it must
                // not stop the game.
                try {
                    Path path = Path.of(markersFile);
                    if (path.toAbsolutePath().getParent() != null) {
                        Files.createDirectories(path.toAbsolutePath().getParent());
                    }
                    markers = new PrintWriter(Files.newBufferedWriter(path,
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND));
                } catch (IOException | RuntimeException unavailable) {
                    System.out.println("[weaver] MARKERS FILE UNAVAILABLE path=" + markersFile
                            + " reason=" + unavailable + " action=log-only");
                }
            }
            PrintWriter sink = markers;
            Consumer<String> log = line -> {
                System.out.println(line);
                if (sink != null) {
                    sink.println(line);
                    sink.flush();
                }
            };
            net.weaver.core.command.PermissionStoreSupport.install(
                    dataDir.resolve("permissions.json"), log);

            boolean integratedServer = Boolean.parseBoolean(options.getOrDefault("integratedServer",
                    "client".equals(side) ? "true" : "false"));
            log.accept("[weaver] AGENT premain side=" + side + " modsDir=" + modsDir
                    + " integratedServer=" + integratedServer);
            loader = ModLoader.discover(modsDir, side, log,
                    ModPolicies.fromSystemProperties(), dataDir.resolve("jij"));
            if (net.weaver.core.watch.ModWatchService.isEnabled()) {
                net.weaver.core.watch.ModWatchService.shared().start(modsDir, log);
            }
            writeModList(options, loader.report(), log);
            net.weaver.api.WeaverMods.install(loader.mods().stream()
                    .map(jar -> new net.weaver.api.WeaverMods.Mod(jar.metadata().id(), jar.metadata().version(),
                            jar.metadata().name(), jar.metadata().side(), jar.metadata().description(),
                            jar.metadata().authors(), jar.metadata().license(), jar.metadata().homepage(),
                            jar.metadata().compatibleVersions()))
                    .toList());
            net.weaver.api.network.ModFingerprints.install(Weaver.VERSION, fingerprints(loader.mods(), log));
            space = ClassSpace.create(instrumentation, side, loader.mods(), log);
            if (net.weaver.core.dev.HotSwapService.isEnabled()) {
                // config files reload when edited, as everything else does in a dev run
                if (System.getProperty("weaver.config.watch") == null) {
                    System.setProperty("weaver.config.watch", "true");
                }
                // Before the other transformers: a class first loaded from new bytes still passes through them.
                ClassSpace devSpace = space;
                net.weaver.core.dev.HotSwapService.start(instrumentation, space.modLoader(),
                        name -> MixinEngine.get().isMixinClass(name),
                        jar -> {
                            if (devSpace.modLoader() instanceof SharedModClassLoader shared) {
                                shared.addJar(jar.toUri().toURL());
                            } else {
                                instrumentation.appendToSystemClassLoaderSearch(
                                        net.weaver.core.dev.HotSwapService.jarFile(jar));
                            }
                        },
                        devGame(space.gameLoader(), log), dataDir, log);
                // a jar added to or replaced in the mods folder: restart into the same place
                if (!"off".equalsIgnoreCase(System.getProperty(net.weaver.core.dev.HotSwapService.RESTART_PROPERTY, "auto"))) {
                    if (!net.weaver.core.watch.ModWatchService.isEnabled()) {
                        net.weaver.core.watch.ModWatchService.shared().start(modsDir, log);
                    }
                    net.weaver.core.dev.HotSwapService.Game game = devGame(space.gameLoader(), log);
                    net.weaver.api.event.EventBus.GAME.addListener(net.weaver.api.event.events.ModsChangedEvent.class,
                            event -> {
                                log.accept("[weaver] HOTSWAP RESTART requested why=mods folder changed ("
                                        + event.changes().size() + " jar(s))");
                                game.restart("the mods folder changed");
                            });
                }
            }
            LifecycleHooks.install(loader.mods(), space.modLoader(), side, integratedServer,
                    configDir, dataDir, log);
            try
            {
                RegistryManager.install(new MinecraftRegistryBackend(space.gameLoader()), log);
            }
            catch (Throwable failure)
            {
                log.accept("[weaver] REGISTRY BACKEND INSTALL FAILED reason=" + failure);
            }

            AccessTransformer accessTransformer = AccessTransformer.parse(loader.accessTransformers());
            if (accessTransformer.hasRules()) {
                instrumentation.addTransformer(new AccessTransformerTransformer(accessTransformer), false);
                log.accept("[weaver] AT rules=" + accessTransformer.size());
            }

            installEnumExtensions(instrumentation, loader, space.modLoader(), log);

            engine = MixinEngine.get();
            engine.side(side);
            engine.setLoadedProbe(dotted -> {
                for (Class<?> loaded : instrumentation.getAllLoadedClasses()) {
                    if (loaded.getName().equals(dotted)) {
                        return true;
                    }
                }
                return false;
            });
            engine.boot(space.gameLoader());
            if ("client".equals(side)) {
                installClientFeatureMixins(space.gameLoader(), log);
            }
            installGameEventMixins(space.gameLoader(), log);
            boolean networkBridgeArmed = NetworkBridgeInstall.arm(space.gameLoader(),
                    (name, bytes) -> engine.addConfig("weaver-network", name, bytes,
                            space.gameLoader()), log);
            MixinBridge.setInstrumentation(instrumentation);
            instrumentation.addTransformer(new WeaverMixinTransformer(engine), false);

            installLateTransformers(instrumentation, space.gameLoader(), side, log);
            // Mod mixin configs are registered before anything below loads game classes
            // (version checks, the typed event factory, network and capability activation);
            // a config added after its target class loaded is excluded as live-unsupported.
            // They follow the transformer install so classes that a config plugin loads
            // still pass through the hook and lifecycle transformers.
            int configs = registerModMixinConfigs(loader.mods(), space.modLoader(), log);
            ClassLoadAudit.log(instrumentation, log, "before-all-transformers");

            logPatch(space.gameLoader(), log);
            VersionChecks.Result versions = VersionChecks.check(space.gameLoader(),
                    WeaverApi.GAME_VERSION);
            log.accept("[weaver] " + versions.message());
            installGameEventFactory(space.gameLoader(), log);
            NetworkBridgeInstall.activate(space.gameLoader(), networkBridgeArmed,
                    advertisedMods(loader.mods(), log), log);
            ModPacksInstall.install(space.gameLoader(), loader.mods(), log);
            if (net.weaver.core.dev.HotSwapService.isEnabled()) {
                installDevResources(space.gameLoader(), dataDir, log);
            }
            net.weaver.core.capability.CapabilitySyncInstall.install(space.gameLoader(), log);
            net.weaver.core.config.ConfigSync.install(log);

            engine.emitIndex();
            engine.emitStats();
            ClassLoadAudit.log(instrumentation, log, "after-all-transformers");
            log.accept("[weaver] AGENT READY side=" + side + " mods=" + loader.mods().size()
                    + " configs=" + configs + " class-space=" + (space.shared() ? "shared" : "system"));
        } catch (ModPolicyException failure) {
            Consumer<String> sink = failureLog(markers);
            sink.accept("[weaver] MOD POLICY FATAL " + failure.getMessage());
            for (ModDiagnostic diagnostic : failure.diagnostics()) {
                sink.accept("[weaver] MOD POLICY DIAGNOSTIC severity="
                        + diagnostic.severity() + " type=" + diagnostic.type()
                        + " subject=" + diagnostic.subject() + " message="
                        + diagnostic.message());
            }
            throw failure;
        } catch (Throwable failure) {
            onAgentFailure(failure, failureLog(markers));
        } finally {
            if (markers != null) {
                markers.flush();
            }
        }
    }

    /** The system property that selects what a boot failure does: {@code fatal} (default) or {@code warn}. */
    static final String FAILURE_POLICY_PROPERTY = "weaver.agent.failurePolicy";

    /**
     * Handles an unexpected boot failure.
     *
     * <p>The failure is logged through {@code log}, so it also reaches the
     * markers file. By default the failure is rethrown, which aborts the JVM:
     * continuing would start the game half-initialised, without mods or
     * transformers, and without any visible fatal error. Setting
     * {@value #FAILURE_POLICY_PROPERTY}{@code =warn} logs the failure and lets
     * the game continue without Weaver.</p>
     *
     * @param failure the boot failure
     * @param log     the diagnostic sink
     * @throws RuntimeException or {@link Error} unless the policy is {@code warn}
     */
    static void onAgentFailure(Throwable failure, Consumer<String> log) {
        boolean warn = "warn".equalsIgnoreCase(
                System.getProperty(FAILURE_POLICY_PROPERTY, "fatal").trim());
        log.accept("[weaver] AGENT FAILED " + failure);
        failure.printStackTrace(System.out);
        if (warn) {
            log.accept("[weaver] AGENT FAILURE POLICY policy=warn action=continue-without-weaver");
            return;
        }
        log.accept("[weaver] AGENT FAILURE POLICY policy=fatal action=abort-jvm"
                + " override=-D" + FAILURE_POLICY_PROPERTY + "=warn");
        if (failure instanceof RuntimeException runtime) {
            throw runtime;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        throw new IllegalStateException("Weaver agent failed: " + failure, failure);
    }

    /** A sink for failures: standard output plus the markers file when it was opened. */
    private static Consumer<String> failureLog(PrintWriter markers) {
        return line -> {
            System.out.println(line);
            if (markers != null) {
                markers.println(line);
                markers.flush();
            }
        };
    }

    /**
     * Registers every accepted mod's mixin configs with the engine.
     *
     * @param mods      the accepted mods in load order
     * @param modLoader the loader that hosts mod classes
     * @return the number of configs offered to the engine
     */
    private static int registerModMixinConfigs(java.util.List<ModJar> mods, ClassLoader modLoader,
                                               Consumer<String> log) {
        int configs = 0;
        for (ModJar mod : mods) {
            for (Map.Entry<String, byte[]> config : mod.mixinConfigs().entrySet()) {
                try {
                    engine.addConfig(mod.metadata().id(), config.getKey(), config.getValue(),
                            modLoader);
                    configs++;
                } catch (OutOfMemoryError fatal) {
                    throw fatal;
                } catch (Throwable failure) {
                    // One mod's broken mixin config costs that config, not the game.
                    log.accept("[weaver] MIXIN CONFIG REJECTED mod=" + mod.metadata().id()
                            + " config=" + config.getKey() + " reason=" + failure);
                }
            }
        }
        return configs;
    }

    /**
     * Installs the transformers that must exist before any optional game
     * feature is activated. The bridge and capability sync are deliberately
     * activated by the caller only after this method returns.
     *
     * <p>The method is package-private so the boot-order test can exercise the
     * real install sequence without starting a game.
     *
     * @param instrumentation the agent instrumentation
     * @param gameLoader      the loader that hosts the game classes
     * @param side            the process side
     * @param log             the diagnostic sink
     */
    static void installLateTransformers(Instrumentation instrumentation, ClassLoader gameLoader,
                                        String side, Consumer<String> log) {
        GameHookTransformer.install(instrumentation, gameLoader);
        RegistryTransform.install(instrumentation, gameLoader);
        if ("client".equals(side)) {
            ClientInitTransform.install(instrumentation, gameLoader);
        }
        ServerInitTransform.install(instrumentation, gameLoader);
        BootPhaseOrder.markTransformersInstalled();
        log.accept("[weaver] TRANSFORMERS INSTALLED game-hook=true registry=true client="
                + "client".equals(side) + " server=true");
    }

    /**
     * Writes the mod-list report when {@code modListFile} is configured.
     *
     * <p>The agent option {@code modListFile=...} takes precedence over the
     * {@code weaver.modListFile} system property. {@code modListFormat=text}
     * selects the line-oriented form; every other value selects JSON. A write
     * failure is logged and never fails the boot.
     *
     * @param options agent options parsed from the {@code -javaagent} argument
     * @param report  the discovery report
     * @param log     the diagnostic sink
     */
    private static void writeModList(Map<String, String> options, ModList report,
                                     Consumer<String> log) {
        String file = options.getOrDefault("modListFile",
                System.getProperty("weaver.modListFile", "")).trim();
        if (file.isEmpty()) {
            return;
        }
        boolean json = !"text".equalsIgnoreCase(options.getOrDefault("modListFormat",
                System.getProperty("weaver.modListFormat", "json")).trim());
        Path path = Path.of(file);
        try {
            ModReportWriter.write(report, path, json);
            log.accept("[weaver] MOD LIST path=" + path.toAbsolutePath()
                    + " format=" + (json ? "json" : "text")
                    + " mods=" + report.entries().size()
                    + " diagnostics=" + report.diagnostics().size());
        } catch (IOException | RuntimeException failure) {
            log.accept("[weaver] MOD LIST FAILED path=" + path + " reason=" + failure);
        }
    }

    /**
     * Logs whether the game loader hosts the optional patched artifact.
     *
     * <p>Detection is reflective and never fails boot. A present marker class
     * logs its patch level and, when the optional marker metadata is readable,
     * the patch-set id. An absent marker class keeps the runtime transformer
     * path and is reported as unpatched.
     *
     * @param gameLoader the loader that hosts the game classes
     * @param log        the diagnostic sink
     */
    private static void logPatch(ClassLoader gameLoader, Consumer<String> log) {
        try {
            PatchDetection.Result patch = PatchDetection.classify(gameLoader);
            if (patch.unavailable()) {
                log.accept("[weaver] GAME PATCH probe=unavailable reason=" + patch.reason());
            } else if (patch.patched()) {
                String line = "[weaver] GAME PATCH level=" + patch.level()
                        + " artifact=patched";
                if (patch.patchSet() != null && !patch.patchSet().isBlank()) {
                    line += " patch-set=" + patch.patchSet();
                }
                log.accept(line);
            } else {
                log.accept("[weaver] GAME PATCH level=0 artifact=unpatched action=runtime-only");
            }
        } catch (Throwable failure) {
            log.accept("[weaver] GAME PATCH probe=unavailable reason=" + failure);
        }
    }

    /**
     * Installs the typed event factory when {@code api-game} is visible to the
     * game class loader.
     *
     * <p>The loader has no compile-time dependency on {@code api-game}, so the
     * factory is resolved reflectively. When the class or one of its game
     * dependencies is missing, the base factory stays installed and the loader
     * logs one diagnostic; boot never fails because of this step.
     *
     * @param gameLoader the loader that must see {@code api-game} and its game dependencies
     * @param log        the diagnostic sink
     * @return {@code true} when the typed factory was installed, {@code false} when the
     *         base factory remains
     */
    static boolean installGameEventFactory(ClassLoader gameLoader, Consumer<String> log) {
        try {
            Class<?> type = Class.forName("net.weaver.api.game.TypedGameEventFactory", true,
                    gameLoader);
            Object instance = type.getDeclaredConstructor().newInstance();
            GameEventFactoryHolder.install((GameEventFactory) instance);
            log.accept("[weaver] TYPED EVENTS enabled factory=" + type.getName());
            return true;
        } catch (ClassNotFoundException | LinkageError missing) {
            log.accept("[weaver] TYPED EVENTS unavailable reason=" + missing);
            return false;
        } catch (Throwable failure) {
            log.accept("[weaver] TYPED EVENTS unavailable reason=" + failure);
            return false;
        }
    }

    /**
     * Arms client-only mixins that live in the optional {@code api-game}
     * artifact. The resource is absent when the game API was skipped, so the
     * feature stays optional without affecting server or no-game builds.
     */
    private static void installClientFeatureMixins(ClassLoader gameLoader, Consumer<String> log) {
        String resource = "weaver.client.mixins.json";
        try (InputStream input = LoaderAgent.class.getClassLoader().getResourceAsStream(resource)) {
            if (input == null) {
                return;
            }
            engine.addConfig("weaver-client", resource, input.readAllBytes(), gameLoader);
            log.accept("[weaver] CLIENT FEATURE arabic-input armed");
        } catch (Throwable failure) {
            log.accept("[weaver] CLIENT FEATURE arabic-input FAILED reason=" + failure);
        }
    }

    /**
     * Arms the typed game-event mixins ({@code weaver.events.mixins.json}) that
     * live in the optional {@code api-game} artifact, on both sides. They fire the
     * events in {@code net.weaver.api.game.event.living|player|world} that need mid-method injection
     * or argument changes, which the ASM hook specs in {@code GameHooks} cannot
     * express. The resource is absent when the game API was skipped.
     */
    private static void installGameEventMixins(ClassLoader gameLoader, Consumer<String> log) {
        String resource = "weaver.events.mixins.json";
        try (InputStream input = LoaderAgent.class.getClassLoader().getResourceAsStream(resource)) {
            if (input == null) {
                return;
            }
            engine.addConfig("weaver-events", resource, input.readAllBytes(), gameLoader);
            log.accept("[weaver] GAME EVENTS armed config=" + resource);
        } catch (Throwable failure) {
            log.accept("[weaver] GAME EVENTS FAILED reason=" + failure);
        }
    }

    /** The game side of the developer hot reload ({@code api-game}'s DevReload), called by reflection. */
    private static net.weaver.core.dev.HotSwapService.Game devGame(ClassLoader gameLoader, Consumer<String> log) {
        return new net.weaver.core.dev.HotSwapService.Game() {
            @Override
            public void reloadResources(boolean assets, boolean data) {
                call("reloadResources", new Class<?>[] {boolean.class, boolean.class}, assets, data);
            }

            @Override
            public void restart(String why) {
                call("restart", new Class<?>[] {String.class}, why);
            }

            private void call(String method, Class<?>[] types, Object... args) {
                try {
                    Class.forName("net.weaver.api.game.dev.DevReload", true, gameLoader)
                            .getMethod(method, types).invoke(null, args);
                } catch (ReflectiveOperationException | LinkageError failure) {
                    log.accept("[weaver] HOTSWAP GAME " + method + " unavailable reason=" + failure);
                }
            }
        };
    }

    private static void installDevResources(ClassLoader gameLoader, Path dataDir, Consumer<String> log) {
        List<String> roots = new java.util.ArrayList<>();
        for (String entry : System.getProperty(net.weaver.core.dev.HotSwapService.RESOURCES_PROPERTY, "")
                .split(java.io.File.pathSeparator)) {
            if (!entry.isBlank() && Files.isDirectory(Path.of(entry.trim()))) {
                roots.add(Path.of(entry.trim()).toAbsolutePath().normalize().toString());
            }
        }
        try {
            Class.forName("net.weaver.api.game.dev.DevReload", true, gameLoader)
                    .getMethod("install", Path.class, List.class).invoke(null, dataDir, roots);
        } catch (ReflectiveOperationException | LinkageError failure) {
            log.accept("[weaver] HOTSWAP RESOURCES unavailable reason=" + failure);
        }
    }

    /** The SHA-256 of each mod jar, by mod id; a jar that cannot be read is left out and logged. */
    static java.util.Map<String, String> fingerprints(List<ModJar> mods, java.util.function.Consumer<String> log) {
        java.util.Map<String, String> sha = new java.util.LinkedHashMap<>();
        for (ModJar mod : mods) {
            try (InputStream in = java.nio.file.Files.newInputStream(mod.path())) {
                java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
                byte[] buffer = new byte[64 * 1024];
                for (int read; (read = in.read(buffer)) > 0; ) {
                    digest.update(buffer, 0, read);
                }
                sha.put(mod.metadata().id(), java.util.HexFormat.of().formatHex(digest.digest()));
            } catch (Exception failure) {
                log.accept("[weaver] MOD FINGERPRINT FAILED id=" + mod.metadata().id() + " reason=" + failure);
            }
        }
        return sha;
    }

    /**
     * The mods the network handshake advertises: those whose metadata side is
     * {@code both}, which must be installed on client and server at the same
     * version. Client-only and server-only mods are not advertised. An entry
     * the handshake cannot carry (an over-long id or version) is skipped and
     * logged.
     */
    static List<net.weaver.api.network.NetworkModInfo> advertisedMods(List<ModJar> mods,
                                                                     java.util.function.Consumer<String> log) {
        List<net.weaver.api.network.NetworkModInfo> advertised = new java.util.ArrayList<>();
        for (ModJar mod : mods) {
            ModMetadata metadata = mod.metadata();
            if (!"both".equals(metadata.side())) {
                continue;
            }
            try {
                advertised.add(net.weaver.api.network.NetworkModInfo.of(metadata.id(), metadata.version()));
            } catch (IllegalArgumentException invalid) {
                log.accept("[weaver] NETWORK BRIDGE mod skipped id=" + metadata.id() + " reason=" + invalid.getMessage());
            }
            if (advertised.size() == net.weaver.api.network.NetworkProtocol.MAX_MODS) {
                log.accept("[weaver] NETWORK BRIDGE mod list truncated at " + advertised.size());
                break;
            }
        }
        return advertised;
    }

    private static Map<String, String> parse(String args) {
        Map<String, String> options = new LinkedHashMap<>();
        if (args == null || args.isBlank()) {
            return options;
        }
        // Split only where a new key=value starts, so a comma or semicolon inside a path
        // (C:\Users\Smith, John\mods) stays part of the value.
        for (String part : args.split("[;,](?=\\s*[A-Za-z][A-Za-z0-9_.]*=)")) {
            int equals = part.indexOf('=');
            if (equals > 0) {
                options.put(part.substring(0, equals).trim(), part.substring(equals + 1).trim());
            }
        }
        return options;
    }

    /**
     * Reads every mod's {@code enumextensions.json}, hands the entries to
     * {@code EnumExtensions}, and adds the transformer that builds the constants when
     * their enum loads.
     */
    private static void installEnumExtensions(java.lang.instrument.Instrumentation instrumentation, ModLoader loader,
                                              ClassLoader modLoader, java.util.function.Consumer<String> log) {
        java.util.List<net.weaver.core.enums.EnumExtender.Addition> additions = new java.util.ArrayList<>();
        java.util.List<String> problems = new java.util.ArrayList<>();
        for (ModJar mod : loader.mods()) {
            byte[] json = mod.enumExtensions();
            if (json == null) {
                continue;
            }
            try {
                additions.addAll(net.weaver.core.enums.EnumExtender.parse(mod.metadata().id(), json, problems));
            } catch (RuntimeException bad) {
                problems.add(mod.metadata().id() + ": unreadable enumextensions.json: " + bad);
            }
        }
        for (String problem : problems) {
            log.accept("[weaver] ENUM EXTENSION REJECTED " + problem);
        }
        if (additions.isEmpty()) {
            return;
        }
        net.weaver.api.EnumExtensions.install(additions.stream()
                .map(addition -> new net.weaver.api.EnumExtensions.Entry(addition.modId(), addition.enumName(),
                        addition.constant(), addition.factoryClass(), addition.factoryMethod(), modLoader))
                .toList());
        net.weaver.core.enums.EnumExtender extender = new net.weaver.core.enums.EnumExtender(additions);
        instrumentation.addTransformer(new net.weaver.core.enums.EnumExtensionTransformer(extender), false);
        log.accept("[weaver] ENUM EXTENSIONS constants=" + extender.size());
    }
}
