package net.weaver.gradle;

import net.weaver.gradle.run.LaunchPlan;
import net.weaver.gradle.run.LaunchPlanner;
import net.weaver.gradle.run.RunOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure unit tests for {@link LaunchPlanner} argument construction. */
class RunPlannerTest {

    @TempDir
    Path temp;

    private Path gameDir;
    private Path serverDir;
    private Path agentJar;

    @BeforeEach
    void setUp() throws IOException {
        gameDir = temp.resolve("game");
        serverDir = temp.resolve("server");
        agentJar = temp.resolve("agent.jar");
        Files.createDirectories(gameDir);
        Files.createDirectories(serverDir.resolve("versions/26.3"));
        Files.createDirectories(serverDir.resolve("libraries"));
        Files.writeString(agentJar, "fake agent", StandardCharsets.UTF_8);
        Files.writeString(gameDir.resolve("classpath.txt"), "fake-client.jar", StandardCharsets.UTF_8);
        Files.writeString(serverDir.resolve("versions/26.3/server-26.3.jar"),
                "fake server", StandardCharsets.UTF_8);
        Files.writeString(serverDir.resolve("libraries/z-lib.jar"), "z", StandardCharsets.UTF_8);
        Files.writeString(serverDir.resolve("libraries/a-lib.jar"), "a", StandardCharsets.UTF_8);
    }

    @Test
    void clientCommandIncludesClasspathAgentUsernameAndProgramArgs() throws IOException {
        RunOptions options = new RunOptions(
                List.of("-Dtest.jvm=1"),
                List.of("--bonus", "--demo"),
                Map.of("WEAVER_TEST_ENV", "1"),
                false, 5005, true, "", 25566, "WeaverProof", "TestWorld", "localhost:25565");

        LaunchPlan plan = planner().client(options);
        List<String> command = plan.command();

        assertEquals("client", plan.side());
        int classpath = command.indexOf("-cp");
        assertTrue(classpath >= 0, command.toString());
        assertEquals("fake-client.jar", command.get(classpath + 1));
        assertTrue(command.stream().anyMatch(argument -> argument.startsWith("-javaagent:")
                && argument.contains("side=client")), command.toString());
        // Single-player worlds run in the client, so it applies the server hooks too.
        assertTrue(command.stream().anyMatch(argument -> argument.startsWith("-javaagent:")
                && argument.contains("integratedServer=true")), command.toString());
        // Vanilla 26.3 passes it; without it native code can overflow the stack and kill the JVM.
        assertTrue(command.contains("-XX:StackShadowPages=32"), command.toString());
        int main = command.indexOf("net.minecraft.client.main.Main");
        assertTrue(main >= 0, command.toString());
        assertTrue(command.indexOf("--username") > main, command.toString());
        assertEquals("WeaverProof", command.get(command.indexOf("--username") + 1));
        assertTrue(command.indexOf("--quickPlaySingleplayer") > main, command.toString());
        assertEquals("TestWorld", command.get(command.indexOf("--quickPlaySingleplayer") + 1));
        assertTrue(command.indexOf("--quickPlayMultiplayer") > main, command.toString());
        assertEquals("localhost:25565", command.get(command.indexOf("--quickPlayMultiplayer") + 1));
        assertEquals(List.of("--bonus", "--demo"),
                command.subList(command.size() - 2, command.size()));
        assertTrue(command.indexOf("-Dtest.jvm=1") > 0, command.toString());
        assertTrue(command.indexOf("-Dtest.jvm=1") < main, command.toString());
    }

    @Test
    void serverCommandSortsLibrariesBeforeServerJar() throws IOException {
        RunOptions options = new RunOptions(
                List.of(), List.of("--bonus"), Map.of(),
                false, 5005, true, "", 25999, "WeaverProof", "", "");

        LaunchPlan plan = planner().server(options);
        List<String> command = plan.command();

        assertEquals("server", plan.side());
        int main = command.indexOf("net.minecraft.server.Main");
        assertTrue(main >= 0, command.toString());
        assertEquals("nogui", command.get(main + 1));
        assertEquals("--port", command.get(main + 2));
        assertEquals("25999", command.get(main + 3));
        assertEquals("--bonus", command.get(command.size() - 1));

        int classpathIndex = command.indexOf("-cp");
        assertTrue(classpathIndex >= 0, command.toString());
        String classpath = command.get(classpathIndex + 1);
        String first = serverDir.resolve("libraries/a-lib.jar").toAbsolutePath().toString();
        String second = serverDir.resolve("libraries/z-lib.jar").toAbsolutePath().toString();
        String serverJar = serverDir.resolve("versions/26.3/server-26.3.jar")
                .toAbsolutePath().toString();
        assertTrue(classpath.contains(first), classpath);
        assertTrue(classpath.contains(second), classpath);
        assertTrue(classpath.contains(serverJar), classpath);
        assertTrue(classpath.indexOf(first) < classpath.indexOf(second), classpath);
        assertTrue(classpath.indexOf(second) < classpath.indexOf(serverJar), classpath);
        assertTrue(command.stream().anyMatch(argument -> argument.startsWith("-javaagent:")
                && argument.contains("side=server") && argument.contains("integratedServer=false")), command.toString());

        int agent = -1;
        for (int index = 0; index < command.size(); index++) {
            if (command.get(index).startsWith("-javaagent:")) {
                agent = index;
            }
        }
        assertTrue(agent >= 0 && agent < main, command.toString());
    }

    @Test
    void debugArgumentIsPlacedAfterJvmArgsAndBeforeClasspath() throws IOException {
        String debug =
                "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5011";
        RunOptions options = new RunOptions(
                List.of("-Dtest.jvm=1"), List.of(), Map.of(),
                true, 5011, false, "", 25566, "WeaverProof", "", "");

        LaunchPlan server = planner().server(options);
        List<String> command = server.command();
        int jvm = command.indexOf("-Dtest.jvm=1");
        int debugIndex = command.indexOf(debug);
        int classpath = command.indexOf("-cp");
        int main = command.indexOf("net.minecraft.server.Main");
        assertTrue(jvm >= 0, command.toString());
        assertTrue(debugIndex > jvm, command.toString());
        assertTrue(debugIndex < classpath, command.toString());
        assertTrue(debugIndex < main, command.toString());

        LaunchPlan client = planner().client(options);
        int clientDebug = client.command().indexOf(debug);
        int clientMain = client.command().indexOf("net.minecraft.client.main.Main");
        assertTrue(clientDebug > client.command().indexOf("-Dtest.jvm=1"),
                client.command().toString());
        assertTrue(clientDebug < clientMain, client.command().toString());
    }

    @Test
    void missingAgentAndServerJarFailWithClearMessages() {
        Path missingAgent = temp.resolve("missing-agent.jar");
        LaunchPlanner agentPlanner = new LaunchPlanner("java", gameDir, serverDir, missingAgent,
                temp.resolve("mods"), temp.resolve("config"), temp.resolve("data"),
                temp.resolve("run.log"), temp.resolve("markers.log"));
        IOException agentError = assertThrows(IOException.class,
                () -> agentPlanner.client(RunOptions.defaults()));
        assertTrue(agentError.getMessage().contains("agent jar missing"), agentError.getMessage());
        assertTrue(agentError.getMessage().contains("missing-agent.jar"), agentError.getMessage());

        Path emptyServer = temp.resolve("empty-server");
        LaunchPlanner serverPlanner = new LaunchPlanner("java", gameDir, emptyServer, agentJar,
                temp.resolve("mods"), temp.resolve("config"), temp.resolve("data"),
                temp.resolve("run.log"), temp.resolve("markers.log"));
        IOException serverError = assertThrows(IOException.class,
                () -> serverPlanner.server(RunOptions.defaults()));
        assertTrue(serverError.getMessage().contains("server jar missing"),
                serverError.getMessage());
        assertTrue(serverError.getMessage().contains("server-26.3.jar"), serverError.getMessage());
    }

    @Test
    void environmentAndWorkingDirectoryAreCarriedThrough() throws IOException {
        Path work = temp.resolve("run-work");
        RunOptions options = new RunOptions(
                List.of(), List.of(), Map.of("WEAVER_TEST_ENV", "1"),
                false, 5005, true, work.toString(), 25566, "WeaverProof", "", "");

        LaunchPlan client = planner().client(options);
        assertEquals(Map.of("WEAVER_TEST_ENV", "1"), client.environment());
        assertEquals(work.toAbsolutePath().toFile(), client.workingDir());

        LaunchPlan server = planner().server(options);
        assertEquals(Map.of("WEAVER_TEST_ENV", "1"), server.environment());
        assertEquals(work.toAbsolutePath().toFile(), server.workingDir());

        assertEquals(gameDir.toAbsolutePath().toFile(),
                planner().client(RunOptions.defaults()).workingDir());
        assertEquals(serverDir.toAbsolutePath().toFile(),
                planner().server(RunOptions.defaults()).workingDir());
    }

    @Test
    void gameTestCommandRunsVanillaGameTestMainWithReportAndSelection() throws IOException {
        Path universe = temp.resolve("gametest/universe");
        Path report = temp.resolve("gametest/report.xml");

        LaunchPlan plan = planner().gameTest(RunOptions.defaults(), universe, report, "examplemod:*");
        List<String> command = plan.command();

        assertEquals("gametest", plan.side());
        int main = command.indexOf(LaunchPlanner.GAME_TEST_MAIN);
        assertTrue(main >= 0, command.toString());
        assertEquals(List.of("--universe", universe.toAbsolutePath().toString(),
                        "--report", report.toAbsolutePath().toString(), "--tests", "examplemod:*"),
                command.subList(main + 1, main + 7));
        assertTrue(command.stream().anyMatch(argument -> argument.startsWith("-javaagent:")
                && argument.contains("side=server")), command.toString());
        String classpath = command.get(command.indexOf("-cp") + 1);
        assertTrue(classpath.endsWith(serverDir.resolve("versions/26.3/server-26.3.jar")
                .toAbsolutePath().toString()), classpath);

        LaunchPlan all = planner().gameTest(RunOptions.defaults(), universe, report, "");
        assertTrue(!all.command().contains("--tests"), all.command().toString());
    }

    private LaunchPlanner planner() {
        return new LaunchPlanner("java", gameDir, serverDir, agentJar,
                temp.resolve("mods"), temp.resolve("config"), temp.resolve("data"),
                temp.resolve("client.log"), temp.resolve("client-markers.log"));
    }
}
