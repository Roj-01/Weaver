package net.weaver.gradle;

import net.weaver.gradle.run.LaunchPlan;
import net.weaver.gradle.run.LaunchPlanner;
import net.weaver.gradle.run.RunOptions;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Launches the client or the dedicated server directly with the Weaver agent.
 *
 * <p>The command is built by {@link LaunchPlanner}; it matches
 * {@code tools/launcher/launch.ps1} for required arguments and also supports
 * extra JVM arguments, program arguments, environment variables, a JDWP debug
 * agent, and an explicit working directory. A dry run prints the exact command
 * without starting the game.
 */
public abstract class RunGameTask extends DefaultTask {

    @Input
    public abstract Property<String> getSide();

    @Input
    public abstract Property<String> getGameDir();

    @Input
    public abstract Property<String> getServerDir();

    @Input
    public abstract Property<String> getAgentJar();

    @Input
    @Optional
    public abstract Property<String> getLauncher();

    @Input
    public abstract Property<String> getModsDir();

    @Input
    public abstract Property<String> getConfigDir();

    @Input
    public abstract Property<String> getDataDir();

    @Input
    public abstract Property<String> getLog();

    @Input
    public abstract Property<String> getMarkers();

    @Input
    @Optional
    public abstract Property<String> getQuickPlayWorld();

    @Input
    @Optional
    public abstract Property<String> getQuickPlayServer();

    @Input
    @Optional
    public abstract Property<Boolean> getDryRun();

    @Input
    @Optional
    public abstract ListProperty<String> getJvmArgs();

    @Input
    @Optional
    public abstract ListProperty<String> getProgramArgs();

    @Input
    @Optional
    public abstract MapProperty<String, String> getEnvironment();

    @Input
    @Optional
    public abstract Property<Boolean> getDebug();

    @Input
    @Optional
    public abstract Property<Integer> getDebugPort();

    @Input
    @Optional
    public abstract Property<Boolean> getDebugSuspend();

    @Input
    @Optional
    public abstract Property<String> getWorkingDir();

    @Input
    @Optional
    public abstract Property<Integer> getServerPort();

    /** Game test selection for side {@code gametest}, such as {@code examplemod:*}; empty runs all. */
    @Input
    @Optional
    public abstract Property<String> getGameTests();

    /** JUnit XML report for side {@code gametest}. */
    @Input
    @Optional
    public abstract Property<String> getGameTestReport();

    /** Folder the game test world is created in, emptied first. */
    @Input
    @Optional
    public abstract Property<String> getGameTestUniverse();

    @Inject
    public abstract ExecOperations getExecOperations();

    @TaskAction
    public void run() throws IOException {
        String side = getSide().get();
        Path mods = Path.of(getModsDir().get());
        Path config = Path.of(getConfigDir().get());
        Path data = Path.of(getDataDir().get());
        Path log = Path.of(getLog().get());
        Path markers = Path.of(getMarkers().get());
        Files.createDirectories(mods);
        Files.createDirectories(config);
        Files.createDirectories(data);
        Files.createDirectories(log.toAbsolutePath().getParent());
        Files.createDirectories(markers.toAbsolutePath().getParent());

        String quickPlayWorld = getQuickPlayWorld().getOrElse("");
        String quickPlayServer = getQuickPlayServer().getOrElse("");
        for (int restarts = 0; ; restarts++) {
        RunOptions options = new RunOptions(
                getJvmArgs().getOrElse(java.util.List.of()),
                getProgramArgs().getOrElse(java.util.List.of()),
                getEnvironment().getOrElse(java.util.Map.of()),
                getDebug().getOrElse(false),
                getDebugPort().getOrElse(5005),
                getDebugSuspend().getOrElse(true),
                getWorkingDir().getOrElse(""),
                getServerPort().getOrElse(25566),
                "WeaverProof",
                quickPlayWorld,
                quickPlayServer);
        LaunchPlanner planner = new LaunchPlanner(
                "",
                Path.of(getGameDir().get()),
                Path.of(getServerDir().get()),
                Path.of(getAgentJar().get()),
                mods, config, data, log, markers);
        LaunchPlan plan;
        try {
            if ("client".equals(side)) {
                plan = planner.client(options);
            } else if ("gametest".equals(side)) {
                Path universe = Path.of(getGameTestUniverse().get());
                Path report = Path.of(getGameTestReport().get());
                deleteTree(universe);
                Files.createDirectories(universe.toAbsolutePath().getParent());
                Files.createDirectories(report.toAbsolutePath().getParent());
                Files.deleteIfExists(report);
                plan = planner.gameTest(options, universe, report, getGameTests().getOrElse(""));
            } else {
                plan = planner.server(options);
            }
        } catch (IOException e) {
            throw new GradleException("weaver: cannot prepare " + side + " launch: " + e.getMessage(), e);
        }
        if (getDryRun().getOrElse(false)) {
            getLogger().lifecycle(plan.describe());
            return;
        }
        Files.createDirectories(plan.workingDir().toPath());
        getLogger().lifecycle("RUN side={} workdir={} log={}", side,
                plan.workingDir().getAbsolutePath(), plan.log().getAbsolutePath());

        try (OutputStream logStream = new FileOutputStream(plan.log(), true);
             TeeOutputStream out = new TeeOutputStream(System.out, logStream);
             TeeOutputStream err = new TeeOutputStream(System.err, logStream)) {
            getExecOperations().exec(spec -> {
                spec.setExecutable(plan.command().get(0));
                spec.setArgs(plan.command().subList(1, plan.command().size()));
                spec.setWorkingDir(plan.workingDir());
                if (!plan.environment().isEmpty()) {
                    spec.environment(plan.environment());
                }
                spec.setStandardInput(System.in);
                spec.setStandardOutput(out);
                spec.setErrorOutput(err);
            });
        }
        // Developer hot reload: a game that restarted itself to apply a change left a
        // note saying where to come back to (the world or the server it was in).
        Path note = data.resolve("dev-restart.json");
        if (!Files.isRegularFile(note) || restarts >= 50) {
            break;
        }
        String json = Files.readString(note);
        Files.delete(note);
        quickPlayWorld = jsonString(json, "quickPlayWorld");
        quickPlayServer = jsonString(json, "quickPlayServer");
        getLogger().lifecycle("RUN DEV RESTART side={} world={} server={}", side, quickPlayWorld, quickPlayServer);
        }
        getLogger().lifecycle("RUN DONE side={}", side);
    }

    /** A string field of the small restart note, or empty. */
    static String jsonString(String json, String field) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"" + field + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
        return m.find() ? m.group(1).replace("\\\"", "\"").replace("\\\\", "\\") : "";
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    /** Writes to two streams so the console and the log file both see output. */
    private static final class TeeOutputStream extends OutputStream {
        private final OutputStream first;
        private final OutputStream second;

        private TeeOutputStream(OutputStream first, OutputStream second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public void write(int value) throws IOException {
            first.write(value);
            second.write(value);
        }

        @Override
        public void write(byte[] buffer, int offset, int length) throws IOException {
            first.write(buffer, offset, length);
            second.write(buffer, offset, length);
        }

        @Override
        public void flush() throws IOException {
            first.flush();
            second.flush();
        }

        @Override
        public void close() throws IOException {
            try {
                flush();
            } finally {
                second.close();
            }
        }
    }
}
