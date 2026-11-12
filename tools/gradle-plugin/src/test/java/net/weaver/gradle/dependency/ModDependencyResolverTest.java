package net.weaver.gradle.dependency;

import groovy.json.JsonSlurper;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModDependencyResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void readsDescriptorFromJar() throws IOException {
        String json = "{"
                + "\"schemaVersion\":2,"
                + "\"id\":\"example-mod\","
                + "\"version\":\"1.2.3\","
                + "\"name\":\"Example Mod\","
                + "\"mainClass\":\"com.example.ExampleMod\","
                + "\"mixins\":[\"example.mixins.json\"],"
                + "\"dependencies\":{\"base\":\"[1.0,2.0)\"},"
                + "\"optional\":{\"extra\":\"1.0\"},"
                + "\"side\":\"client\","
                + "\"minecraft\":\"26.3\""
                + "}";
        File jar = createJar("example-mod-1.2.3.jar", json);

        ModDescriptor descriptor = ModDescriptor.read(jar);

        assertEquals("example-mod", descriptor.id());
        assertEquals("1.2.3", descriptor.version());
        assertEquals("Example Mod", descriptor.name());
        assertEquals("com.example.ExampleMod", descriptor.mainClass());
        assertEquals(List.of("example.mixins.json"), descriptor.mixins());
        assertEquals("[1.0,2.0)", descriptor.dependencies().get("base"));
        assertEquals("1.0", descriptor.optional().get("extra"));
        assertEquals("client", descriptor.side());
        assertEquals("26.3", descriptor.minecraft());
    }

    @Test
    void rejectsMissingDescriptor() throws IOException {
        File jar = createJar("no-descriptor.jar", null);

        IOException error = assertThrows(IOException.class, () -> ModDescriptor.read(jar));

        assertTrue(error.getMessage().contains("no-descriptor.jar"));
        assertTrue(error.getMessage().contains("weaver.mod.json"));
    }

    @Test
    void rejectsInvalidModId() throws IOException {
        File jar = createJar("bad-id.jar", descriptor("Bad Id", "1.0"));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ModDescriptor.read(jar));

        assertTrue(error.getMessage().contains("bad-id.jar"));
        assertThrows(IllegalArgumentException.class,
                () -> ModDescriptor.parse(descriptor("Bad Id", "1.0").getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void detectsDuplicateModIdAsFatal() throws IOException {
        File first = createJar("dup-1.0.jar", descriptor("dup", "1.0"));
        File second = createJar("dup-2.0.jar", descriptor("dup", "2.0"));
        Project project = newProject();

        ModDependencyResolver resolver = new ModDependencyResolver(
                configuration(project, "weaverMod"),
                configuration(project, "weaverOptional"),
                project.files(first, second),
                false,
                project.getLogger());
        ModResolution resolution = resolver.resolve();

        assertTrue(resolver.hasFatalConflicts());
        assertTrue(resolver.getFatalConflictCount() > 0);
        ModConflict conflict = resolution.conflicts().stream()
                .filter(candidate -> "mod-id".equals(candidate.kind()))
                .findFirst()
                .orElseThrow();
        assertEquals("dup", conflict.module());
        assertTrue(conflict.requested().containsAll(List.of("1.0", "2.0")));
        assertTrue(conflict.message().contains("dup-1.0.jar"));
        assertTrue(conflict.message().contains("dup-2.0.jar"));
        assertEquals(2, resolution.mods().size());
    }

    @Test
    void warnsOnSameIdAndVersionDuplicates() throws IOException {
        File first = createJar("same-a.jar", descriptor("same", "1.0"));
        File second = createJar("same-b.jar", descriptor("same", "1.0"));
        Project project = newProject();

        ModDependencyResolver resolver = new ModDependencyResolver(
                configuration(project, "weaverMod"),
                configuration(project, "weaverOptional"),
                project.files(first, second),
                false,
                project.getLogger());
        ModResolution resolution = resolver.resolve();

        assertFalse(resolver.hasFatalConflicts());
        assertEquals(1, resolution.mods().size());
        assertTrue(resolution.warnings().stream()
                .anyMatch(warning -> warning.contains("duplicate mod id 'same'")));
    }

    @Test
    void sortsModsAndLibrariesDeterministically() throws IOException {
        File zeta = createJar("zeta.jar", descriptor("zeta", "1.0"));
        File alpha = createJar("alpha.jar", descriptor("alpha", "2.0"));
        File middle = createJar("middle.jar", descriptor("middle", "1.5"));
        File libraryB = createJar("b-lib.jar", null);
        File libraryA = createJar("a-lib.jar", null);
        Project project = newProject();

        ModDependencyResolver resolver = new ModDependencyResolver(
                configuration(project, "weaverMod"),
                configuration(project, "weaverOptional"),
                project.files(zeta, alpha, middle, libraryB, libraryA),
                false,
                project.getLogger());
        ModResolution resolution = resolver.resolve();

        assertEquals(List.of("alpha", "middle", "zeta"),
                resolution.mods().stream().map(ResolvedMod::id).toList());
        assertEquals(List.of("a-lib.jar", "b-lib.jar"),
                resolution.libraries().stream().map(File::getName).toList());
    }

    @Test
    void writesReportWithEscapedJson() throws IOException {
        Project project = newProject();
        ModDependencyResolver resolver = new ModDependencyResolver(
                configuration(project, "weaverMod"),
                configuration(project, "weaverOptional"),
                project.files(),
                false,
                project.getLogger());
        ModResolution resolution = new ModResolution(
                List.of(new ResolvedMod("quoted", "1.0", tempDir.resolve("quoted.jar").toFile(),
                        "example:quoted:\"1.0\"", "1.0,2.0\\", false, false)),
                List.of(new ModConflict("mod-id", "quoted", "1.0", List.of("1.0", "2.0"),
                        "say \"hi\" and use a \\ backslash\non one line")),
                List.of("warn \"quoted\" \\ slash"),
                List.of(tempDir.resolve("lib").resolve("a.jar").toFile()));
        Path report = tempDir.resolve("report.json");

        resolver.writeReport(report.toFile(), resolution);
        String json = Files.readString(report, StandardCharsets.UTF_8);

        assertTrue(json.contains("\\\"hi\\\""));
        assertTrue(json.contains("\\\\"));
        Map<?, ?> root = (Map<?, ?>) new JsonSlurper().parseText(json);
        assertEquals(1, ((Number) root.get("schemaVersion")).intValue());
        List<?> mods = (List<?>) root.get("mods");
        assertEquals(1, mods.size());
        Map<?, ?> mod = (Map<?, ?>) mods.get(0);
        assertEquals("quoted", mod.get("id"));
        List<?> libraries = (List<?>) root.get("libraries");
        assertEquals(List.of("a.jar"), libraries);
        List<?> warnings = (List<?>) root.get("warnings");
        assertEquals(1, warnings.size());
    }

    @Test
    void readsLocalModsAndSeparatesLibraries() throws IOException {
        File local = createJar("local-mod.jar", descriptor("local-mod", "3.2.1"));
        File library = createJar("plain-library.jar", null);
        Project project = newProject();
        ModDependencyResolver resolver = new ModDependencyResolver(
                configuration(project, "weaverMod"),
                configuration(project, "weaverOptional"),
                project.files(local, library),
                false,
                project.getLogger());

        ModResolution resolution = resolver.resolve();

        assertEquals(1, resolution.mods().size());
        ResolvedMod mod = resolution.mods().get(0);
        assertEquals("local-mod", mod.id());
        assertEquals("3.2.1", mod.version());
        assertEquals("3.2.1", mod.requested());
        assertFalse(mod.optional());
        assertFalse(mod.transitive());
        assertTrue(mod.coordinates().startsWith("local:"));
        assertEquals("local-mod.jar", mod.file().getName());
        assertEquals(List.of("plain-library.jar"),
                resolution.libraries().stream().map(File::getName).toList());
        assertFalse(resolution.warnings().isEmpty());
    }

    @Test
    void marksOptionalDependencies() throws IOException {
        File optionalJar = createJar("optional-mod.jar", descriptor("optional-mod", "0.9.0"));
        Project project = newProject();
        Configuration optional = configuration(project, "weaverOptional");
        project.getDependencies().add(optional.getName(), project.files(optionalJar));
        ModDependencyResolver resolver = new ModDependencyResolver(
                configuration(project, "weaverMod"),
                optional,
                project.files(),
                false,
                project.getLogger());

        ModResolution resolution = resolver.resolve();

        assertEquals(1, resolution.mods().size());
        ResolvedMod mod = resolution.mods().get(0);
        assertEquals("optional-mod", mod.id());
        assertTrue(mod.optional());
    }

    private Project newProject() {
        try {
            return ProjectBuilder.builder()
                    .withProjectDir(tempDir.toFile())
                    .withGradleUserHomeDir(Files.createTempDirectory("weaver-test-user-home").toFile())
                    .build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Configuration configuration(Project project, String name) {
        Configuration configuration = project.getConfigurations().create(name);
        configuration.setCanBeConsumed(false);
        configuration.setCanBeResolved(true);
        configuration.setTransitive(true);
        return configuration;
    }

    private File createJar(String name, String descriptor) throws IOException {
        Path jar = tempDir.resolve(name);
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            if (descriptor != null) {
                out.putNextEntry(new JarEntry(ModDescriptor.FILE));
                out.write(descriptor.getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
            out.putNextEntry(new JarEntry("META-INF/MANIFEST.MF"));
            out.write("Manifest-Version: 1.0\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        return jar.toFile();
    }

    private static String descriptor(String id, String version) {
        return "{"
                + "\"schemaVersion\":2,"
                + "\"id\":\"" + id + "\","
                + "\"version\":\"" + version + "\","
                + "\"name\":\"" + id + " mod\","
                + "\"mainClass\":\"com.example.Mod\""
                + "}";
    }
}
