package net.weaver.gradle;

import net.weaver.gradle.dependency.WeaverResolveModsTask;
import net.weaver.gradle.jij.WeaverExportJijTask;
import net.weaver.gradle.jij.WeaverJijTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.provider.Provider;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Weaver Gradle plugin: mod dependencies, jar-in-jar, run tasks, and datagen. */
public final class WeaverPlugin implements Plugin<Project> {

    public static final String GROUP = "weaver";

    /**
     * Weaver artifact version used to derive default toolchain paths when the
     * root project is not the Weaver repository.
     *
     * <p>Mod projects set their own root {@code version}; it is not the
     * Weaver artifact version. The default jar paths therefore use the root
     * version only when the root project also contains the {@code api} and
     * {@code api-game} modules (the Weaver repository layout), and otherwise
     * fall back to this constant. Override the individual paths with
     * {@code weaver.apiJar}, {@code weaver.gameApiJar}, or the matching
     * environment variables.
     */
    public static final String WEAVER_VERSION = "2.1.0-SNAPSHOT";

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply("java-library");
        WeaverExtension weaver = project.getExtensions().create("weaver", WeaverExtension.class);
        applyConventions(project, weaver);

        Configuration mods = createModConfiguration(project, "weaverMod", true,
                "Weaver runtime mod dependencies embedded as jar-in-jar.");
        Configuration optional = createModConfiguration(project, "weaverOptional", true,
                "Optional Weaver mod dependencies, resolved for diagnostics but never embedded.");

        project.afterEvaluate(p -> {
            if (weaver.getAddModDependenciesToCompileClasspath().getOrElse(true)) {
                p.getConfigurations().named("compileOnly").configure(c -> c.extendsFrom(mods));
            }
        });

        Provider<WeaverResolveModsTask> resolveMods = registerResolveMods(project, weaver, mods, optional);
        Provider<WeaverJijTask> jij = registerJij(project, weaver, mods);
        project.afterEvaluate(p -> configureGameClasspath(p, weaver));
        registerModJson(project, weaver);
        Provider<WeaverBuildTask> build = registerBuild(project, weaver, resolveMods, jij);
        Provider<Copy> stage = registerStage(project, build);
        Provider<WeaverExportJijTask> export = registerExport(project, weaver, build);
        registerRun(project, weaver, stage, export, "client");
        registerRun(project, weaver, stage, export, "server");
        registerRun(project, weaver, stage, export, "gametest");
        registerDatagen(project, weaver);
    }

    private Configuration createModConfiguration(Project project, String name, boolean transitive,
                                                  String description) {
        return project.getConfigurations().create(name, c -> {
            c.setCanBeConsumed(false);
            c.setCanBeResolved(true);
            c.setTransitive(transitive);
            c.setDescription(description);
        });
    }

    private void applyConventions(Project project, WeaverExtension weaver) {
        String version = weaverVersion(project);
        weaver.getApiJar().convention(env("WEAVER_API_JAR",
                project.getRootProject().file("api/build/libs/api-" + version + ".jar").getAbsolutePath()));
        weaver.getGameApiJar().convention(env("WEAVER_GAME_API_JAR",
                project.getRootProject().file("api-game/build/libs/api-game-" + version + ".jar")
                        .getAbsolutePath()));
        weaver.getGameDir().convention(env("WEAVER_GAME_DIR",
                project.getRootProject().file("work/game-26.3").getAbsolutePath()));
        weaver.getServerDir().convention(env("WEAVER_SERVER_DIR",
                project.getRootProject().file("work/server-26.3").getAbsolutePath()));
        weaver.getAgentJar().convention(env("WEAVER_AGENT_JAR",
                project.getRootProject().file("core/build/agent/weaver-agent.jar").getAbsolutePath()));
        weaver.getLauncher().convention(env("WEAVER_LAUNCHER",
                project.getRootProject().file("tools/launcher/launch.ps1").getAbsolutePath()));
        weaver.getMainClass().convention("");
        weaver.getModId().convention(project.provider(() -> sanitize(project.getName())));
        weaver.getModName().convention(project.getName());
        weaver.getModVersion().convention(project.provider(() -> {
            Object value = project.getVersion();
            return value == null || value.toString().isBlank() ? "1.0.0" : value.toString();
        }));
        weaver.getMinecraftVersion().convention("26.3");
        weaver.getSide().convention("both");
        weaver.getDatagenClass().convention("");
        weaver.getMixins().convention(List.of());
        weaver.getQuickPlayWorld().convention("");
        weaver.getQuickPlayServer().convention("");
        String dry = project.findProperty("weaver.dryRun") == null
                ? null : project.findProperty("weaver.dryRun").toString();
        weaver.getDryRun().convention(dry != null && Boolean.parseBoolean(dry));
        weaver.getDependencies().convention(Map.of());
        weaver.getOptionalDependencies().convention(Map.of());
        weaver.getEmbedModDependencies().convention(true);
        weaver.getStrictConflicts().convention(false);
        weaver.getAddModDependenciesToCompileClasspath().convention(true);
        weaver.getExportJijForRun().convention(true);
        weaver.getJvmArgs().convention(List.of());
        weaver.getProgramArgs().convention(List.of());
        weaver.getEnvironment().convention(Map.of());
        weaver.getDebug().convention(false);
        weaver.getHotSwap().convention(true);
        weaver.getDebugPort().convention(5005);
        weaver.getDebugSuspend().convention(true);
        weaver.getWorkingDir().convention("");
        weaver.getServerPort().convention(25566);
        Object gameTests = project.findProperty("weaver.gameTests");
        weaver.getGameTests().convention(gameTests == null ? "" : gameTests.toString());
    }

    private Provider<WeaverResolveModsTask> registerResolveMods(Project project, WeaverExtension weaver,
                                                                 Configuration mods,
                                                                 Configuration optional) {
        Provider<WeaverResolveModsTask> task = project.getTasks().register(
                "weaverResolveMods", WeaverResolveModsTask.class, t -> {
                    t.setGroup(GROUP);
                    t.setDescription("Resolves weaverMod/weaverOptional dependencies, validates "
                            + "version ranges, and reports conflicts deterministically.");
                    t.setModsConfiguration(mods);
                    t.setOptionalConfiguration(optional);
                    t.getLocalMods().from(weaver.getLocalMods());
                    t.getStrict().set(weaver.getStrictConflicts());
                    t.getReportFile().set(project.getLayout().getBuildDirectory()
                            .file("weaver/dependencies.json"));
                    t.getInputs().files(mods).withPropertyName("weaverMod");
                    t.getInputs().files(optional).withPropertyName("weaverOptional");
                });
        return task;
    }

    private Provider<WeaverJijTask> registerJij(Project project, WeaverExtension weaver,
                                                 Configuration mods) {
        return project.getTasks().register("weaverJij", WeaverJijTask.class, t -> {
            t.setGroup(GROUP);
            t.setDescription("Stages jar-in-jar nested mods under META-INF/weaver/jars.");
            t.getJars().from(mods);
            t.getJars().from(weaver.getLocalMods());
            t.getOutputDirectory().set(project.getLayout().getBuildDirectory().dir("weaver/jij"));
            t.onlyIf(spec -> weaver.getEmbedModDependencies().getOrElse(true)
                    && !t.getJars().isEmpty());
        });
    }

    private void configureGameClasspath(Project project, WeaverExtension weaver) {
        List<File> files = new ArrayList<>();
        Path api = Path.of(weaver.getApiJar().get());
        if (Files.isRegularFile(api)) {
            files.add(api.toFile());
        } else {
            project.getLogger().warn("weaver: API jar not found at {}", api);
        }
        Path gameApi = Path.of(weaver.getGameApiJar().get());
        if (Files.isRegularFile(gameApi)) {
            files.add(gameApi.toFile());
        } else {
            project.getLogger().warn("weaver: game API jar not found at {} (optional; "
                    + "set weaver.gameApiJar or WEAVER_GAME_API_JAR)", gameApi);
        }
        Path game = Path.of(weaver.getGameDir().get());
        Path classpath = game.resolve("classpath.txt");
        if (Files.isRegularFile(classpath)) {
            try {
                for (String entry : Files.readString(classpath, StandardCharsets.UTF_8).trim().split(";")) {
                    if (!entry.isBlank()) {
                        files.add(Path.of(entry.trim()).toFile());
                    }
                }
            } catch (IOException e) {
                project.getLogger().warn("weaver: cannot read {}", classpath, e);
            }
        }
        Path server = Path.of(weaver.getServerDir().get());
        Path serverJar = server.resolve("versions/26.3/server-26.3.jar");
        if (Files.isRegularFile(serverJar)) {
            files.add(serverJar.toFile());
        }
        Path libraries = server.resolve("libraries");
        if (Files.isDirectory(libraries)) {
            try (var stream = Files.walk(libraries)) {
                stream.filter(path -> path.toString().endsWith(".jar"))
                        .forEach(path -> files.add(path.toFile()));
            } catch (IOException e) {
                project.getLogger().warn("weaver: cannot read {}", libraries, e);
            }
        }
        if (!files.isEmpty()) {
            project.getDependencies().add("compileOnly", project.files(files));
        }
    }

    private void registerModJson(Project project, WeaverExtension weaver) {
        var descriptor = project.getLayout().getBuildDirectory().dir("weaver/descriptor");
        var task = project.getTasks().register("weaverModJson", WeaverModJsonTask.class, t -> {
            t.setGroup(GROUP);
            t.setDescription("Writes weaver.mod.json v2 into the processed resources.");
            t.getModId().set(weaver.getModId());
            t.getModName().set(weaver.getModName());
            t.getModVersion().set(weaver.getModVersion());
            t.getMinecraftVersion().set(weaver.getMinecraftVersion());
            t.getSide().set(weaver.getSide());
            t.getMainClass().set(weaver.getMainClass());
            t.getMixins().set(weaver.getMixins());
            t.getDependencies().set(weaver.getDependencies());
            t.getOptionalDependencies().set(weaver.getOptionalDependencies());
            t.getOutputFile().set(descriptor.map(dir -> dir.file("weaver.mod.json")));
        });
        SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
        SourceSet main = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        main.getResources().srcDir(descriptor);
        project.getTasks().named("processResources").configure(t -> t.dependsOn(task));
    }

    private Provider<WeaverBuildTask> registerBuild(Project project, WeaverExtension weaver,
                                                     Provider<WeaverResolveModsTask> resolveMods,
                                                     Provider<WeaverJijTask> jij) {
        SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
        SourceSet main = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        Provider<WeaverBuildTask> task = project.getTasks().register("weaverBuild", WeaverBuildTask.class, t -> {
            t.setGroup(GROUP);
            t.setDescription("Builds the Weaver mod jar, optionally embedding jar-in-jar mods.");
            t.from(main.getOutput());
            t.from(jij.flatMap(WeaverJijTask::getOutputDirectory));
            t.dependsOn(resolveMods, jij);
            t.getModuleClass().set(weaver.getMainClass());
            t.getModId().set(weaver.getModId());
            t.getModVersion().set(weaver.getModVersion());
            t.getArchiveBaseName().convention(weaver.getModName());
            t.getArchiveVersion().convention("");
            t.getArchiveClassifier().convention("");
            t.getArchiveExtension().convention("jar");
            t.getDestinationDirectory().convention(project.getLayout().getBuildDirectory().dir("libs"));
        });
        project.getTasks().named("assemble").configure(t -> t.dependsOn(task));
        return task;
    }

    private Provider<Copy> registerStage(Project project, Provider<WeaverBuildTask> build) {
        return project.getTasks().register("weaverStage", Copy.class, t -> {
            t.setGroup(GROUP);
            t.setDescription("Copies the mod jar into the run mods directory.");
            t.dependsOn(build);
            t.from(build.flatMap(WeaverBuildTask::getArchiveFile));
            t.into(project.getLayout().getBuildDirectory().dir("weaver/mods"));
        });
    }

    private Provider<WeaverExportJijTask> registerExport(Project project, WeaverExtension weaver,
                                                          Provider<WeaverBuildTask> build) {
        return project.getTasks().register("weaverExportJij", WeaverExportJijTask.class, t -> {
            t.setGroup(GROUP);
            t.setDescription("Extracts embedded jar-in-jar mods into the run mods directory.");
            t.dependsOn(build);
            t.getJarFile().set(build.flatMap(WeaverBuildTask::getArchiveFile));
            t.getOutputDirectory().set(project.getLayout().getBuildDirectory().dir("weaver/mods"));
            t.getExportEnabled().set(weaver.getEmbedModDependencies()
                    .zip(weaver.getExportJijForRun(), (embed, export) -> embed && export));
        });
    }

    private void registerRun(Project project, WeaverExtension weaver, Provider<Copy> stage,
                             Provider<WeaverExportJijTask> export, String side) {
        String name = "gametest".equals(side) ? "weaverGameTest"
                : "run" + Character.toUpperCase(side.charAt(0)) + side.substring(1);
        project.getTasks().register(name, RunGameTask.class, t -> {
                    t.setGroup(GROUP);
                    t.setDescription("gametest".equals(side)
                            ? "Runs the mod's game tests on a headless game test server; writes build/weaver/gametest/report.xml."
                            : "Runs the " + side + " with the Weaver agent.");
                    t.dependsOn(stage, export);
                    t.getSide().set(side);
                    t.getGameDir().set(weaver.getGameDir());
                    t.getServerDir().set(weaver.getServerDir());
                    t.getAgentJar().set(weaver.getAgentJar());
                    t.getLauncher().set(weaver.getLauncher());
                    t.getModsDir().set(project.getLayout().getBuildDirectory().dir("weaver/mods")
                            .map(dir -> dir.getAsFile().getAbsolutePath()));
                    t.getConfigDir().set(project.getLayout().getBuildDirectory().dir("weaver/config")
                            .map(dir -> dir.getAsFile().getAbsolutePath()));
                    t.getDataDir().set(project.getLayout().getBuildDirectory().dir("weaver/data")
                            .map(dir -> dir.getAsFile().getAbsolutePath()));
                    t.getLog().set(project.getLayout().getBuildDirectory().dir("weaver/logs")
                            .map(dir -> dir.file(side + ".log").getAsFile().getAbsolutePath()));
                    t.getMarkers().set(project.getLayout().getBuildDirectory().dir("weaver/logs")
                            .map(dir -> dir.file(side + "-markers.log").getAsFile().getAbsolutePath()));
                    t.getQuickPlayWorld().set(weaver.getQuickPlayWorld());
                    t.getQuickPlayServer().set(weaver.getQuickPlayServer());
                    t.getDryRun().set(weaver.getDryRun());
                    t.getJvmArgs().set("gametest".equals(side) ? weaver.getJvmArgs()
                            : weaver.getJvmArgs().zip(weaver.getHotSwap(), (args, hotSwap) -> hotSwap
                                    ? withHotSwap(args, project) : args));
                    t.getProgramArgs().set(weaver.getProgramArgs());
                    t.getEnvironment().set(weaver.getEnvironment());
                    t.getDebug().set(weaver.getDebug());
                    t.getDebugPort().set(weaver.getDebugPort());
                    t.getDebugSuspend().set(weaver.getDebugSuspend());
                    t.getWorkingDir().set(weaver.getWorkingDir());
                    t.getServerPort().set(weaver.getServerPort());
                    t.getGameTests().set(weaver.getGameTests());
                    t.getGameTestReport().set(project.getLayout().getBuildDirectory().dir("weaver/gametest")
                            .map(dir -> dir.file("report.xml").getAsFile().getAbsolutePath()));
                    t.getGameTestUniverse().set(project.getLayout().getBuildDirectory().dir("weaver/gametest/universe")
                            .map(dir -> dir.getAsFile().getAbsolutePath()));
                });
    }

    /** Adds the developer hot swap flags, watching this project's compiled classes. */
    static List<String> withHotSwap(List<String> args, Project project) {
        SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
        List<String> dirs = new ArrayList<>();
        for (File dir : sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME).getOutput().getClassesDirs().getFiles()) {
            dirs.add(dir.getAbsolutePath());
        }
        List<String> all = new ArrayList<>(args);
        all.add("-Dweaver.dev.hotswap=true");
        all.add("-Dweaver.dev.hotswap.dirs=" + String.join(File.pathSeparator, dirs));
        File resources = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME).getOutput().getResourcesDir();
        if (resources != null) {
            // assets and data reload live from here; rerun processResources (or let the IDE copy them)
            all.add("-Dweaver.dev.hotswap.resources=" + resources.getAbsolutePath());
        }
        return all;
    }

    private void registerDatagen(Project project, WeaverExtension weaver) {
        SourceSetContainer sourceSets = project.getExtensions().getByType(SourceSetContainer.class);
        SourceSet main = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        project.getTasks().register("runDatagen", JavaExec.class, t -> {
            t.setGroup(GROUP);
            t.setDescription("Runs the mod's datagen entrypoint.");
            t.onlyIf(task -> !weaver.getDatagenClass().getOrElse("").isBlank());
            t.classpath(main.getRuntimeClasspath());
            t.getMainClass().set(weaver.getDatagenClass());
            List<String> args = new ArrayList<>();
            args.add("--out");
            args.add(project.getLayout().getBuildDirectory().dir("weaver/datagen")
                    .map(dir -> dir.getAsFile().getAbsolutePath()).get());
            args.addAll(weaver.getProgramArgs().getOrElse(List.of()));
            t.args(args);
            List<String> jvm = new ArrayList<>(weaver.getJvmArgs().getOrElse(List.of()));
            if (weaver.getDebug().getOrElse(false)) {
                jvm.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend="
                        + (weaver.getDebugSuspend().getOrElse(true) ? "y" : "n")
                        + ",address=*:" + weaver.getDebugPort().getOrElse(5005));
            }
            t.jvmArgs(jvm);
            t.environment(weaver.getEnvironment().getOrElse(Map.of()));
            String working = weaver.getWorkingDir().getOrElse("");
            t.workingDir(working.isBlank() ? project.getProjectDir() : new File(working));
        });
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String weaverVersion(Project project) {
        Project root = project.getRootProject();
        if (root.file("api").isDirectory() && root.file("api-game").isDirectory()) {
            Object value = root.getVersion();
            if (value != null && !value.toString().isBlank() && !"unspecified".equals(value.toString())) {
                return value.toString();
            }
        }
        return WEAVER_VERSION;
    }

    private static String sanitize(String raw) {
        String id = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]+", "-")
                .replaceAll("^-+|-+$", "");
        return id.isEmpty() ? "weaver-mod" : id;
    }
}
