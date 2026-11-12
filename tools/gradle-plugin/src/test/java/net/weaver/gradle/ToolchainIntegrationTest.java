package net.weaver.gradle;

import groovy.json.JsonSlurper;
import net.weaver.gradle.support.TestProjects;
import org.gradle.testkit.runner.BuildResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end TestKit coverage for mod dependency resolution, conflict
 * reporting, and deterministic jar-in-jar packaging.
 */
class ToolchainIntegrationTest {

    @TempDir
    Path temp;

    private TestProjects project;

    @BeforeEach
    void setUp() {
        project = new TestProjects(temp);
    }

    @Test
    void resolvesTransitiveRangeAndOptionalDependencies() throws IOException {
        project.mavenModule("com.example", "trans", "1.0.0", "trans", "1.0.0", Map.of());
        project.mavenMetadata("com.example", "trans", "1.0.0");
        project.mavenModule("com.example", "opt", "1.0.0", "opt", "1.0.0", Map.of());
        project.mavenMetadata("com.example", "opt", "1.0.0");
        project.mavenModule("com.example", "dep", "1.0.0", "dep", "1.0.0",
                Map.of("com.example:trans", "1.0.0"));
        project.mavenMetadata("com.example", "dep", "1.0.0");
        project.buildGradle("""
                plugins { id 'net.weaver.gradle' }

                repositories {
                    maven { url = uri('repo') }
                }

                dependencies {
                    weaverMod('com.example:dep:[1.0,2.0)')
                    weaverOptional('com.example:opt:1.0.0')
                }

                weaver {
                    mainClass.set('example.Mod')
                    modId.set('test-mod')
                    modVersion.set('1.0.0')
                }
                """);

        BuildResult first = TestProjects.runner(project.root(), "weaverResolveMods",
                "--stacktrace").build();
        String output = first.getOutput();
        assertTrue(output.contains("BUILD SUCCESSFUL"), output);
        assertTrue(output.contains("WEAVER DEPS ok=true"), output);
        assertTrue(output.contains("id=dep"), output);
        assertTrue(output.contains("id=trans"), output);
        assertTrue(output.contains("id=opt"), output);

        Path report = project.path(TestProjects.DEPENDENCIES_REPORT);
        assertTrue(Files.isRegularFile(report), "missing report " + report);
        Map<String, Map<?, ?>> mods = modsById(report);
        assertEquals(3, mods.size(), mods.toString());
        assertEquals("1.0.0", mods.get("dep").get("version"));
        assertEquals("com.example:dep:1.0.0", mods.get("dep").get("coordinates"));
        assertEquals(Boolean.FALSE, mods.get("dep").get("transitive"));
        assertEquals(Boolean.FALSE, mods.get("dep").get("optional"));
        assertEquals(Boolean.FALSE, mods.get("trans").get("optional"));
        assertEquals(Boolean.TRUE, mods.get("trans").get("transitive"));
        assertEquals(Boolean.TRUE, mods.get("opt").get("optional"));
        assertEquals(Boolean.FALSE, mods.get("opt").get("transitive"));

        List<String> firstOrder = depLines(output);
        assertEquals(3, firstOrder.size(), firstOrder.toString());

        BuildResult second = TestProjects.runner(project.root(), "weaverResolveMods",
                "--rerun-tasks", "--stacktrace").build();
        assertTrue(second.getOutput().contains("BUILD SUCCESSFUL"), second.getOutput());
        assertEquals(firstOrder, depLines(second.getOutput()));
    }

    @Test
    void reportsVersionConflictAndStrictFails() throws IOException {
        project.mavenModule("com.example", "dep", "1.0.0", "dep", "1.0.0", Map.of());
        project.mavenModule("com.example", "dep", "2.0.0", "dep", "2.0.0", Map.of());
        project.mavenMetadata("com.example", "dep", "1.0.0", "2.0.0");
        project.buildGradle("""
                plugins { id 'net.weaver.gradle' }

                repositories {
                    maven { url = uri('repo') }
                }

                dependencies {
                    weaverMod('com.example:dep:1.0.0')
                    weaverMod('com.example:dep:2.0.0')
                }

                weaver {
                    mainClass.set('example.Mod')
                }
                """);

        BuildResult relaxed = TestProjects.runner(project.root(), "weaverResolveMods",
                "--stacktrace").build();
        String output = relaxed.getOutput();
        assertTrue(output.contains("BUILD SUCCESSFUL"), output);
        String conflict = output.lines()
                .map(String::trim)
                .filter(line -> line.startsWith("WEAVER DEP CONFLICT"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no conflict line in:\n" + output));
        assertTrue(conflict.contains("kind=module-version"), conflict);
        assertTrue(conflict.contains("module=com.example:dep"), conflict);
        assertTrue(conflict.contains("selected=2.0.0"), conflict);
        assertTrue(conflict.contains("requested=[1.0.0,2.0.0]"), conflict);

        project.buildGradle("""
                plugins { id 'net.weaver.gradle' }

                repositories {
                    maven { url = uri('repo') }
                }

                dependencies {
                    weaverMod('com.example:dep:1.0.0')
                    weaverMod('com.example:dep:2.0.0')
                }

                weaver {
                    mainClass.set('example.Mod')
                    strictConflicts.set(true)
                }
                """);

        BuildResult strict = TestProjects.runner(project.root(), "weaverResolveMods",
                "--rerun-tasks", "--stacktrace").buildAndFail();
        assertTrue(strict.getOutput().contains("WEAVER DEPS FAILED"), strict.getOutput());
    }

    @Test
    void embedsJijDeterministically() throws IOException {
        project.jar("libs/alpha.jar", "alpha", "1.0.0");
        project.jar("libs/beta.jar", "beta", "2.0.0");
        project.write("src/main/java/example/Mod.java",
                "package example; public final class Mod {}\n");
        project.buildGradle("""
                plugins { id 'net.weaver.gradle' }

                weaver {
                    mainClass.set('example.Mod')
                    modId.set('test-mod')
                    modVersion.set('1.0.0')
                    localMods.from(files('libs/alpha.jar', 'libs/beta.jar'))
                }
                """);

        BuildResult first = TestProjects.runner(project.root(), "weaverBuild",
                "--stacktrace").build();
        assertTrue(first.getOutput().contains("BUILD SUCCESSFUL"), first.getOutput());
        Path jar = project.path("build/libs/test.jar");
        assertTrue(Files.isRegularFile(jar), "missing build output " + jar);

        String firstManifest = jijManifest(jar);
        assertJijManifest(firstManifest);

        BuildResult second = TestProjects.runner(project.root(), "weaverBuild",
                "--rerun-tasks", "--stacktrace").build();
        assertTrue(second.getOutput().contains("BUILD SUCCESSFUL"), second.getOutput());
        assertEquals(firstManifest, jijManifest(jar), "jij manifest must be stable");

        BuildResult export = TestProjects.runner(project.root(), "weaverExportJij",
                "--rerun-tasks", "--stacktrace").build();
        assertTrue(export.getOutput().contains("BUILD SUCCESSFUL"), export.getOutput());
        assertTrue(Files.isRegularFile(project.path("build/weaver/mods/jij-0001-alpha-1.0.0.jar")),
                "missing first exported nested jar");
        assertTrue(Files.isRegularFile(project.path("build/weaver/mods/jij-0002-beta-2.0.0.jar")),
                "missing second exported nested jar");
    }

    private static Map<String, Map<?, ?>> modsById(Path report) throws IOException {
        Map<?, ?> root = (Map<?, ?>) new JsonSlurper()
                .parseText(Files.readString(report, StandardCharsets.UTF_8));
        List<?> mods = (List<?>) root.get("mods");
        assertNotNull(mods, "report has no mods array");
        Map<String, Map<?, ?>> byId = new LinkedHashMap<>();
        for (Object mod : mods) {
            Map<?, ?> map = (Map<?, ?>) mod;
            byId.put((String) map.get("id"), map);
        }
        return byId;
    }

    private static List<String> depLines(String output) {
        return output.lines()
                .map(String::trim)
                .filter(line -> line.startsWith("WEAVER DEP module="))
                .toList();
    }

    private static String jijManifest(Path jarPath) throws IOException {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var manifestEntry = jar.getEntry(TestProjects.JIJ_MANIFEST);
            assertNotNull(manifestEntry, "missing " + TestProjects.JIJ_MANIFEST);
            assertNotNull(jar.getEntry(TestProjects.JIJ_ROOT + "alpha-1.0.0.jar"),
                    "missing nested alpha jar");
            assertNotNull(jar.getEntry(TestProjects.JIJ_ROOT + "beta-2.0.0.jar"),
                    "missing nested beta jar");
            try (InputStream input = jar.getInputStream(manifestEntry)) {
                return new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
    }

    private static void assertJijManifest(String manifest) {
        Map<?, ?> root = (Map<?, ?>) new JsonSlurper().parseText(manifest);
        assertEquals(Boolean.TRUE, root.get("firstWins"), manifest);
        List<?> jars = (List<?>) root.get("jars");
        assertNotNull(jars, manifest);
        assertEquals(2, jars.size(), manifest);
        Map<?, ?> first = (Map<?, ?>) jars.get(0);
        Map<?, ?> second = (Map<?, ?>) jars.get(1);
        assertEquals("alpha", first.get("id"), manifest);
        assertEquals("1.0.0", first.get("version"), manifest);
        assertEquals(Boolean.FALSE, first.get("optional"), manifest);
        assertEquals(TestProjects.JIJ_ROOT + "alpha-1.0.0.jar", first.get("file"), manifest);
        assertEquals("beta", second.get("id"), manifest);
        assertEquals("2.0.0", second.get("version"), manifest);
        assertEquals(TestProjects.JIJ_ROOT + "beta-2.0.0.jar", second.get("file"), manifest);
        assertFalse(manifest.contains("\"firstWins\": false"), manifest);
    }
}
