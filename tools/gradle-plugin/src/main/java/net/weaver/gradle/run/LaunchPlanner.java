package net.weaver.gradle.run;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds the client and dedicated-server commands used by {@code runClient} and
 * {@code runServer}.
 *
 * <p>The planner is deliberately independent of Gradle types so the exact
 * argument construction can be unit-tested and printed by a dry run. The
 * argument order matches {@code tools/launcher/launch.ps1} for the required
 * arguments; extra JVM and program arguments are inserted at documented
 * positions.
 */
public final class LaunchPlanner {

    /** Minecraft version used by the launcher. */
    public static final String MINECRAFT_VERSION = "26.3";
    /** Client asset index used by the launcher. */
    public static final String ASSET_INDEX = "34";
    /** Vanilla's game test server entry point. */
    public static final String GAME_TEST_MAIN = "net.minecraft.gametest.Main";

    private final String javaExecutable;
    private final Path gameDir;
    private final Path serverDir;
    private final Path agentJar;
    private final Path modsDir;
    private final Path configDir;
    private final Path dataDir;
    private final Path log;
    private final Path markers;

    public LaunchPlanner(String javaExecutable, Path gameDir, Path serverDir, Path agentJar,
                         Path modsDir, Path configDir, Path dataDir, Path log, Path markers) {
        this.javaExecutable = javaExecutable == null || javaExecutable.isBlank()
                ? defaultJavaExecutable() : javaExecutable;
        this.gameDir = gameDir;
        this.serverDir = serverDir;
        this.agentJar = agentJar;
        this.modsDir = modsDir;
        this.configDir = configDir;
        this.dataDir = dataDir;
        this.log = log;
        this.markers = markers;
    }

    /**
     * Resolves the Java executable for the current process.
     *
     * @return absolute executable path
     */
    public static String defaultJavaExecutable() {
        String home = System.getProperty("java.home");
        if (home == null || home.isBlank()) {
            home = System.getenv("JAVA_HOME");
        }
        if (home == null || home.isBlank()) {
            return isWindows() ? "java.exe" : "java";
        }
        String executable = isWindows() ? "java.exe" : "java";
        return Path.of(home, "bin", executable).toAbsolutePath().toString();
    }

    /**
     * Builds a client launch plan.
     *
     * @param options extra run options
     * @return the plan
     * @throws IOException when a required game, agent, or classpath file is missing
     */
    public LaunchPlan client(RunOptions options) throws IOException {
        requireFile(agentJar, "agent jar");
        Path classpathFile = gameDir.resolve("classpath.txt");
        requireFile(classpathFile, "game classpath");
        String classpath = Files.readString(classpathFile, StandardCharsets.UTF_8).trim();
        if (classpath.isBlank()) {
            throw new IOException("game classpath is empty: " + classpathFile);
        }
        Path natives = gameDir.resolve("natives");
        Path assets = gameDir.resolve("assets");

        List<String> command = new ArrayList<>();
        command.add(javaExecutable);
        command.add("-Dstdout.encoding=UTF-8");
        command.add("-Dstderr.encoding=UTF-8");
        command.add("-Xmx2G");
        // Vanilla 26.3 passes this (version.json): native code needs the stack room.
        command.add("-XX:StackShadowPages=32");
        command.addAll(options.jvmArgs());
        addDebug(command, options);
        command.add("-Djava.library.path=" + path(natives.resolve("java")));
        command.add("-Djna.tmpdir=" + path(natives.resolve("jna")));
        command.add("-Dorg.lwjgl.system.SharedLibraryExtractPath=" + path(natives.resolve("lwjgl")));
        command.add("-Dio.netty.native.workdir=" + path(natives.resolve("netty")));
        command.add("--enable-native-access=ALL-UNNAMED");
        command.add("--add-exports");
        command.add("java.base/jdk.internal.misc=ALL-UNNAMED");
        command.add("-cp");
        command.add(classpath);
        command.add(agentArgument("client"));
        command.add("net.minecraft.client.main.Main");
        command.add("--username");
        command.add(options.username());
        command.add("--version");
        command.add(MINECRAFT_VERSION);
        command.add("--gameDir");
        command.add(path(gameDir));
        command.add("--assetsDir");
        command.add(path(assets));
        command.add("--assetIndex");
        command.add(ASSET_INDEX);
        command.add("--uuid");
        command.add("da816c73-37ef-3826-bda4-5ae589e5ac56");
        command.add("--accessToken");
        command.add("weaver-offline-0");
        command.add("--xuid");
        command.add("0");
        command.add("--versionType");
        command.add("release");
        if (!options.quickPlayWorld().isBlank()) {
            command.add("--quickPlaySingleplayer");
            command.add(options.quickPlayWorld());
        }
        if (!options.quickPlayServer().isBlank()) {
            command.add("--quickPlayMultiplayer");
            command.add(options.quickPlayServer());
        }
        command.addAll(options.programArgs());

        Path workingDir = workingDirectory(options, gameDir);
        return new LaunchPlan("client", command, options.environment(), workingDir.toFile(),
                log.toFile(), markers.toFile());
    }

    /**
     * Builds a dedicated-server launch plan.
     *
     * @param options extra run options
     * @return the plan
     * @throws IOException when the server jar, libraries, or agent jar is missing
     */
    public LaunchPlan server(RunOptions options) throws IOException {
        requireFile(agentJar, "agent jar");
        List<String> classpath = serverClasspath();

        List<String> command = new ArrayList<>();
        command.add(javaExecutable);
        command.add("-Dstdout.encoding=UTF-8");
        command.add("-Dstderr.encoding=UTF-8");
        command.add("-Xmx2G");
        command.addAll(options.jvmArgs());
        addDebug(command, options);
        command.add("--enable-native-access=ALL-UNNAMED");
        command.add("-cp");
        command.add(String.join(File.pathSeparator, classpath));
        command.add(agentArgument("server"));
        command.add("net.minecraft.server.Main");
        command.add("nogui");
        command.add("--port");
        command.add(Integer.toString(options.serverPort()));
        command.addAll(options.programArgs());

        Path workingDir = workingDirectory(options, serverDir);
        return new LaunchPlan("server", command, options.environment(), workingDir.toFile(),
                log.toFile(), markers.toFile());
    }

    /**
     * Builds a game test launch plan: vanilla's game test server
     * ({@code net.minecraft.gametest.Main}) on the dedicated-server classpath
     * with the agent in server mode. The server runs every registered test,
     * or those matching {@code tests}, writes a JUnit-style report, and exits
     * with the number of failed required tests.
     *
     * @param options  extra run options (the server port is unused)
     * @param universe folder the test world is created in
     * @param report   JUnit XML report file
     * @param tests    test selection such as {@code examplemod:*}, or empty for all
     * @return the plan
     * @throws IOException when the server jar, libraries, or agent jar is missing
     */
    public LaunchPlan gameTest(RunOptions options, Path universe, Path report, String tests) throws IOException {
        requireFile(agentJar, "agent jar");
        List<String> classpath = serverClasspath();
        List<String> command = new ArrayList<>();
        command.add(javaExecutable);
        command.add("-Dstdout.encoding=UTF-8");
        command.add("-Dstderr.encoding=UTF-8");
        command.add("-Xmx2G");
        command.addAll(options.jvmArgs());
        addDebug(command, options);
        command.add("--enable-native-access=ALL-UNNAMED");
        command.add("-cp");
        command.add(String.join(File.pathSeparator, classpath));
        command.add(agentArgument("server"));
        command.add(GAME_TEST_MAIN);
        command.add("--universe");
        command.add(path(universe));
        command.add("--report");
        command.add(path(report));
        if (tests != null && !tests.isBlank()) {
            command.add("--tests");
            command.add(tests);
        }
        command.addAll(options.programArgs());
        Path workingDir = workingDirectory(options, universe.toAbsolutePath().getParent());
        return new LaunchPlan("gametest", command, options.environment(), workingDir.toFile(),
                log.toFile(), markers.toFile());
    }

    private List<String> serverClasspath() throws IOException {
        Path serverJar = serverDir.resolve("versions").resolve(MINECRAFT_VERSION)
                .resolve("server-" + MINECRAFT_VERSION + ".jar");
        requireFile(serverJar, "server jar");
        List<String> classpath = new ArrayList<>();
        Path libraries = serverDir.resolve("libraries");
        if (Files.isDirectory(libraries)) {
            try (var stream = Files.walk(libraries)) {
                stream.filter(path -> Files.isRegularFile(path)
                                && path.getFileName().toString().endsWith(".jar"))
                        .map(Path::toAbsolutePath)
                        .map(Path::toString)
                        .sorted()
                        .forEach(classpath::add);
            }
        }
        classpath.add(path(serverJar));
        return classpath;
    }

    private String agentArgument(String side) {
        return "-javaagent:" + path(agentJar)
                + "=side=" + side
                // A client hosts its single-player worlds: apply the server hooks there too.
                + ";integratedServer=" + "client".equals(side)
                + ";modsDir=" + path(modsDir)
                + ";configDir=" + path(configDir)
                + ";dataDir=" + path(dataDir)
                + ";markersFile=" + path(markers);
    }

    private static void addDebug(List<String> command, RunOptions options) {
        if (!options.debug()) {
            return;
        }
        command.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend="
                + (options.debugSuspend() ? "y" : "n")
                + ",address=*:" + options.debugPort());
    }

    private Path workingDirectory(RunOptions options, Path fallback) {
        if (options.workingDir() != null && !options.workingDir().isBlank()) {
            return Path.of(options.workingDir()).toAbsolutePath();
        }
        return fallback.toAbsolutePath();
    }

    private static void requireFile(Path file, String label) throws IOException {
        if (file == null || !Files.isRegularFile(file)) {
            throw new IOException(label + " missing: " + file);
        }
    }

    private static String path(Path value) {
        return value.toAbsolutePath().toString();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
