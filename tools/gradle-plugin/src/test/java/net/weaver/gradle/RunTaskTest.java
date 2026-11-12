package net.weaver.gradle;

import net.weaver.gradle.support.TestProjects;
import org.gradle.testkit.runner.BuildResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** TestKit coverage for {@code runServer}/{@code runClient} dry runs. */
class RunTaskTest {

    @TempDir
    Path temp;

    private TestProjects project;

    @BeforeEach
    void setUp() {
        project = new TestProjects(temp);
    }

    @Test
    void runServerDryRunIncludesArgsEnvDebugAndWorkingDir() throws IOException {
        Path agent = project.fakeJar("agent/weaver-agent.jar");
        Path serverDir = project.fakeServerDir("server");
        Path serverJar = project.path("server/versions/26.3/server-26.3.jar");
        Path work = temp.resolve("run-server-work");
        project.write("src/main/java/example/Mod.java",
                "package example; public final class Mod {}\n");
        project.buildGradle("""
                plugins { id 'net.weaver.gradle' }

                weaver {
                    mainClass.set('example.Mod')
                    agentJar.set('%s')
                    serverDir.set('%s')
                    workingDir.set('%s')
                    jvmArgs.add('-Dtest.jvm=1')
                    programArgs.add('--bonus')
                    environment.put('WEAVER_TEST_ENV', '1')
                    debug.set(true)
                    debugPort.set(5011)
                    debugSuspend.set(false)
                    serverPort.set(25999)
                }
                """.formatted(TestProjects.groovyPath(agent),
                TestProjects.groovyPath(serverDir), TestProjects.groovyPath(work)));

        BuildResult result = TestProjects.runner(project.root(), "runServer",
                "-Pweaver.dryRun=true", "--stacktrace").build();
        String output = result.getOutput();

        assertTrue(output.contains("BUILD SUCCESSFUL"), output);
        assertTrue(output.contains("RUN DRY side=server"), output);
        assertTrue(output.contains("workingDir=" + work.toAbsolutePath()), output);
        assertTrue(output.contains("WEAVER_TEST_ENV=1"), output);
        assertTrue(output.contains("-Dtest.jvm=1"), output);
        assertTrue(output.contains(
                "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5011"), output);
        assertTrue(output.contains("--port 25999"), output);
        assertTrue(output.contains("--bonus"), output);
        assertTrue(output.contains("net.minecraft.server.Main"), output);
        assertTrue(output.contains(serverJar.toAbsolutePath().toString()), output);
        assertTrue(output.contains("side=server"), output);
    }

    @Test
    void runClientDryRunBuildsClientCommand() throws IOException {
        Path agent = project.fakeJar("agent/weaver-agent.jar");
        Path gameDir = temp.resolve("game");
        Path clientJar = project.fakeJar("game/versions/26.3/client.jar");
        project.write("game/classpath.txt", clientJar.toAbsolutePath().toString());
        project.write("src/main/java/example/Mod.java",
                "package example; public final class Mod {}\n");
        project.buildGradle("""
                plugins { id 'net.weaver.gradle' }

                weaver {
                    mainClass.set('example.Mod')
                    agentJar.set('%s')
                    gameDir.set('%s')
                    quickPlayWorld.set('TestWorld')
                    quickPlayServer.set('localhost:25565')
                    jvmArgs.add('-Dtest.client=1')
                    programArgs.add('--client-bonus')
                }
                """.formatted(TestProjects.groovyPath(agent), TestProjects.groovyPath(gameDir)));

        BuildResult result = TestProjects.runner(project.root(), "runClient",
                "-Pweaver.dryRun=true", "--stacktrace").build();
        String output = result.getOutput();

        assertTrue(output.contains("BUILD SUCCESSFUL"), output);
        assertTrue(output.contains("RUN DRY side=client"), output);
        assertTrue(output.contains("net.minecraft.client.main.Main"), output);
        assertTrue(output.contains("--gameDir"), output);
        assertTrue(output.contains("--quickPlaySingleplayer"), output);
        assertTrue(output.contains("TestWorld"), output);
        assertTrue(output.contains("--quickPlayMultiplayer"), output);
        assertTrue(output.contains("localhost:25565"), output);
        assertTrue(output.contains("-Dtest.client=1"), output);
        assertTrue(output.contains("--client-bonus"), output);
        assertTrue(output.contains(gameDir.toAbsolutePath().toString()), output);
        assertTrue(output.contains("side=client"), output);
        assertFalse(output.contains("-agentlib:jdwp"), output);
    }
}
