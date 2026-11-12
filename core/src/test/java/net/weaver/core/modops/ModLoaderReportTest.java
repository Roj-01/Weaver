package net.weaver.core.modops;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import net.weaver.core.ModLoader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModLoaderReportTest {

    @Test
    void loadOrderFollowsDependencies() throws IOException {
        Path root = ModOpsFixtures.createRoot("weaver-modops-order");
        try {
            Path mods = ModOpsFixtures.modsDir(root);
            ModOpsFixtures.mod("app", "2.0.0").dependency("lib", "*")
                    .classEntry("com/example/App.class").write(mods, "a-app.jar");
            ModOpsFixtures.mod("lib", "1.0.0").classEntry("com/example/Lib.class")
                    .write(mods, "z-lib.jar");

            List<String> log = new ArrayList<>();
            try (ModLoader loader = ModLoader.discover(mods, "client", log::add,
                    ModPolicies.warn())) {
                ModList report = loader.report();
                assertEquals(List.of("lib", "app"), report.loadOrdered().stream()
                        .map(ModListEntry::id).toList());
                assertEquals(0, entry(report, "lib").loadOrder());
                assertEquals(1, entry(report, "app").loadOrder());
                assertEquals(List.of("app", "lib"), report.entries().stream()
                        .map(ModListEntry::id).toList());
            }
        } finally {
            ModOpsFixtures.deleteRecursively(root);
        }
    }

    @Test
    void missingDependencyIsRejectedAndReported() throws IOException {
        Path root = ModOpsFixtures.createRoot("weaver-modops-missing");
        try {
            Path mods = ModOpsFixtures.modsDir(root);
            ModOpsFixtures.mod("app", "1.0.0").dependency("missing", "*")
                    .write(mods, "app.jar");

            List<String> log = new ArrayList<>();
            try (ModLoader loader = ModLoader.discover(mods, "client", log::add,
                    ModPolicies.warn())) {
                ModList report = loader.report();
                ModListEntry app = entry(report, "app");
                assertEquals(ModStatus.REJECTED_DEPENDENCY, app.status());
                assertTrue(app.statusDetail().contains("missing"), app.statusDetail());
                assertEquals(-1, app.loadOrder());
                assertTrue(report.accepted().isEmpty());
                assertEquals(List.of("app"), report.rejected().stream()
                        .map(ModListEntry::id).toList());
                assertTrue(report.diagnostics().stream().anyMatch(diagnostic ->
                        diagnostic.severity() == ModDiagnostic.Severity.WARN
                                && diagnostic.type() == ModDiagnostic.Type.MISSING_DEPENDENCY
                                && diagnostic.subject().equals("app")));
            }
            assertEquals(List.of("[weaver] MOD DEPENDENCY MISSING id=app"
                    + " dependency=missing range=*"), log.stream()
                    .filter(line -> line.contains("MOD DEPENDENCY MISSING")).toList());
        } finally {
            ModOpsFixtures.deleteRecursively(root);
        }
    }

    @Test
    void versionMismatchIsRejectedAndReported() throws IOException {
        Path root = ModOpsFixtures.createRoot("weaver-modops-version");
        try {
            Path mods = ModOpsFixtures.modsDir(root);
            ModOpsFixtures.mod("lib", "1.0.0").write(mods, "lib.jar");
            ModOpsFixtures.mod("app", "1.0.0").dependency("lib", ">=2.0.0")
                    .write(mods, "app.jar");

            List<String> log = new ArrayList<>();
            try (ModLoader loader = ModLoader.discover(mods, "client", log::add,
                    ModPolicies.warn())) {
                ModList report = loader.report();
                assertEquals(ModStatus.REJECTED_VERSION, entry(report, "app").status());
                assertEquals(ModStatus.LOADED, entry(report, "lib").status());
                assertTrue(report.diagnostics().stream().anyMatch(diagnostic ->
                        diagnostic.type() == ModDiagnostic.Type.VERSION_MISMATCH
                                && diagnostic.subject().equals("app")
                                && diagnostic.message().contains("actual=1.0.0")));
            }
            assertEquals(List.of("[weaver] MOD DEPENDENCY VERSION id=app dependency=lib"
                    + " required=>=2.0.0 actual=1.0.0"), log.stream()
                    .filter(line -> line.contains("MOD DEPENDENCY VERSION")).toList());
        } finally {
            ModOpsFixtures.deleteRecursively(root);
        }
    }

    @Test
    void cycleIsReportedOnceAndUsesDeterministicFallback() throws IOException {
        Path root = ModOpsFixtures.createRoot("weaver-modops-cycle");
        try {
            Path mods = ModOpsFixtures.modsDir(root);
            ModOpsFixtures.mod("a", "1.0.0").dependency("b", "*").write(mods, "a.jar");
            ModOpsFixtures.mod("b", "1.0.0").dependency("a", "*").write(mods, "b.jar");

            List<String> log = new ArrayList<>();
            try (ModLoader loader = ModLoader.discover(mods, "client", log::add,
                    ModPolicies.warn())) {
                ModList report = loader.report();
                assertEquals(2, report.accepted().size());
                assertEquals(List.of("a", "b"), report.loadOrdered().stream()
                        .map(ModListEntry::id).toList());
                assertEquals(1, report.diagnostics().stream()
                        .filter(diagnostic -> diagnostic.type() == ModDiagnostic.Type.CYCLE)
                        .count());
                ModDiagnostic cycle = report.diagnostics().stream()
                        .filter(diagnostic -> diagnostic.type() == ModDiagnostic.Type.CYCLE)
                        .findFirst().orElseThrow();
                assertTrue(cycle.subject().contains("a"), cycle.subject());
                assertTrue(cycle.subject().contains("b"), cycle.subject());
                JsonArray diagnosticsJson = JsonParser.parseString(report.toJson())
                        .getAsJsonObject().getAsJsonArray("diagnostics");
                long cycleJson = 0;
                for (var diagnostic : diagnosticsJson) {
                    if (diagnostic.getAsJsonObject().get("type").getAsString().equals("CYCLE")) {
                        cycleJson++;
                    }
                }
                assertEquals(1, cycleJson);
            }
            assertEquals(List.of("[weaver] MOD ORDER cycle=true remaining=2"
                    + " action=filename-order"), log.stream()
                    .filter(line -> line.contains("MOD ORDER cycle=")).toList());
        } finally {
            ModOpsFixtures.deleteRecursively(root);
        }
    }

    @Test
    void breaksRejectsTheDeclaringMod() throws IOException {
        Path root = ModOpsFixtures.createRoot("weaver-modops-breaks");
        try {
            Path mods = ModOpsFixtures.modsDir(root);
            ModOpsFixtures.mod("lib", "1.0.0").write(mods, "lib.jar");
            ModOpsFixtures.mod("app", "1.0.0").breaks("lib", ">=1.0.0")
                    .write(mods, "app.jar");

            List<String> log = new ArrayList<>();
            try (ModLoader loader = ModLoader.discover(mods, "client", log::add,
                    ModPolicies.warn())) {
                ModList report = loader.report();
                assertEquals(ModStatus.REJECTED_BREAKS, entry(report, "app").status());
                assertTrue(report.diagnostics().stream().anyMatch(diagnostic ->
                        diagnostic.type() == ModDiagnostic.Type.BREAKS
                                && diagnostic.subject().equals("app")));
            }
            assertEquals(List.of("[weaver] MOD BREAKS id=app target=lib"
                    + " target-version=1.0.0 range=>=1.0.0"), log.stream()
                    .filter(line -> line.contains("MOD BREAKS")).toList());
        } finally {
            ModOpsFixtures.deleteRecursively(root);
        }
    }

    @Test
    void optionalVersionMismatchKeepsTheModLoaded() throws IOException {
        Path root = ModOpsFixtures.createRoot("weaver-modops-optional");
        try {
            Path mods = ModOpsFixtures.modsDir(root);
            ModOpsFixtures.mod("lib", "1.0.0").write(mods, "lib.jar");
            ModOpsFixtures.mod("app", "1.0.0").optional("lib", ">=2.0.0")
                    .write(mods, "app.jar");

            List<String> log = new ArrayList<>();
            try (ModLoader loader = ModLoader.discover(mods, "client", log::add,
                    ModPolicies.warn())) {
                ModList report = loader.report();
                assertEquals(ModStatus.LOADED, entry(report, "app").status());
                assertTrue(report.diagnostics().stream().anyMatch(diagnostic ->
                        diagnostic.type() == ModDiagnostic.Type.OPTIONAL_VERSION
                                && diagnostic.subject().equals("app")));
            }
            assertEquals(List.of("[weaver] MOD OPTIONAL VERSION id=app optional=lib"
                    + " range=>=2.0.0 actual=1.0.0"), log.stream()
                    .filter(line -> line.contains("MOD OPTIONAL VERSION")).toList());
        } finally {
            ModOpsFixtures.deleteRecursively(root);
        }
    }

    @Test
    void wrongSideIsReported() throws IOException {
        Path root = ModOpsFixtures.createRoot("weaver-modops-side");
        try {
            Path mods = ModOpsFixtures.modsDir(root);
            ModOpsFixtures.mod("servermod", "1.0.0").side("server")
                    .write(mods, "servermod.jar");

            List<String> log = new ArrayList<>();
            try (ModLoader loader = ModLoader.discover(mods, "client", log::add,
                    ModPolicies.warn())) {
                ModList report = loader.report();
                ModListEntry entry = entry(report, "servermod");
                assertEquals(ModStatus.WRONG_SIDE, entry.status());
                assertTrue(entry.statusDetail().contains("process-side=client"));
                assertTrue(report.diagnostics().stream().anyMatch(diagnostic ->
                        diagnostic.type() == ModDiagnostic.Type.WRONG_SIDE
                                && diagnostic.subject().equals("servermod")));
            }
            assertEquals(List.of("[weaver] MOD SKIP id=servermod side=server"
                    + " process-side=client"), log.stream()
                    .filter(line -> line.contains("MOD SKIP")).toList());
        } finally {
            ModOpsFixtures.deleteRecursively(root);
        }
    }

    @Test
    void readFailureIsReportedWithFallbackIdentity() throws IOException {
        Path root = ModOpsFixtures.createRoot("weaver-modops-read");
        try {
            Path mods = ModOpsFixtures.modsDir(root);
            Map<String, byte[]> entries = new LinkedHashMap<>();
            entries.put("com/example/Broken.class", new byte[]{1});
            ModOpsFixtures.writeRawJar(mods, "broken.jar", entries);

            List<String> log = new ArrayList<>();
            try (ModLoader loader = ModLoader.discover(mods, "client", log::add,
                    ModPolicies.warn())) {
                ModList report = loader.report();
                ModListEntry entry = entry(report, "broken");
                assertEquals(ModStatus.READ_FAILED, entry.status());
                assertEquals("broken.jar", entry.file());
                assertTrue(entry.statusDetail().startsWith("reason="), entry.statusDetail());
                assertTrue(report.diagnostics().stream().anyMatch(diagnostic ->
                        diagnostic.type() == ModDiagnostic.Type.READ_FAILED
                                && diagnostic.subject().equals("broken.jar")));
            }
            assertTrue(log.stream().anyMatch(line -> line.startsWith(
                    "[weaver] MOD READ FAILED path=broken.jar reason=")), log.toString());
        } finally {
            ModOpsFixtures.deleteRecursively(root);
        }
    }

    @Test
    void duplicateIdKeepsFirstJarAndReportsSecond() throws IOException {
        Path root = ModOpsFixtures.createRoot("weaver-modops-duplicate-id");
        try {
            Path mods = ModOpsFixtures.modsDir(root);
            ModOpsFixtures.mod("same", "1.0.0").write(mods, "first.jar");
            ModOpsFixtures.mod("same", "1.0.0").write(mods, "second.jar");

            List<String> log = new ArrayList<>();
            try (ModLoader loader = ModLoader.discover(mods, "client", log::add,
                    ModPolicies.warn())) {
                ModList report = loader.report();
                assertEquals(2, report.entries().size());
                assertEquals(ModStatus.LOADED, byFile(report, "first.jar").status());
                assertEquals(ModStatus.DUPLICATE_ID, byFile(report, "second.jar").status());
                assertTrue(report.diagnostics().stream().anyMatch(diagnostic ->
                        diagnostic.type() == ModDiagnostic.Type.DUPLICATE_ID
                                && diagnostic.subject().equals("same")));
                assertEquals(1, report.accepted().size());
            }
            assertEquals(List.of("[weaver] MOD SKIP id=same"
                    + " reason=duplicate-id path=second.jar"), log.stream()
                    .filter(line -> line.contains("MOD SKIP")).toList());
        } finally {
            ModOpsFixtures.deleteRecursively(root);
        }
    }

    private static ModListEntry entry(ModList report, String id) {
        return report.entries().stream()
                .filter(entry -> entry.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing entry " + id));
    }

    private static ModListEntry byFile(ModList report, String file) {
        return report.entries().stream()
                .filter(entry -> entry.file().equals(file))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing file " + file));
    }
}
