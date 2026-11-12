package net.weaver.core.modops;

import net.weaver.core.ModLoader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModLoaderPolicyTest {

    @Test
    void duplicateClassWarnKeepsBothMods() throws IOException {
        Path root = ModOpsFixtures.createRoot("weaver-modops-class-warn");
        try {
            Path mods = ModOpsFixtures.modsDir(root);
            ModOpsFixtures.mod("moda", "1.0.0")
                    .classEntry("com/example/Shared.class")
                    .classEntry("com/example/OnlyA.class")
                    .write(mods, "moda.jar");
            ModOpsFixtures.mod("modb", "1.0.0")
                    .classEntry("com/example/Shared.class")
                    .classEntry("com/example/OnlyB.class")
                    .write(mods, "modb.jar");

            List<String> log = new ArrayList<>();
            try (ModLoader loader = ModLoader.discover(mods, "client", log::add,
                    ModPolicies.warn())) {
                ModList report = loader.report();
                assertEquals(2, report.accepted().size());
                ModDiagnostic diagnostic = report.diagnostics().stream()
                        .filter(item -> item.type() == ModDiagnostic.Type.DUPLICATE_CLASS)
                        .findFirst().orElseThrow();
                assertEquals(ModDiagnostic.Severity.WARN, diagnostic.severity());
                assertEquals("com/example/Shared.class", diagnostic.subject());
                assertEquals("mods=moda,modb first=moda", diagnostic.message());
            }
            assertEquals(List.of("[weaver] MOD CLASS CONFLICT class=com/example/Shared.class"
                    + " mods=moda,modb first=moda"), log.stream()
                    .filter(line -> line.contains("MOD CLASS CONFLICT")).toList());
        } finally {
            ModOpsFixtures.deleteRecursively(root);
        }
    }

    @Test
    void duplicateClassFatalThrowsAndClosesJars() throws IOException {
        Path root = ModOpsFixtures.createRoot("weaver-modops-class-fatal");
        try {
            Path mods = ModOpsFixtures.modsDir(root);
            ModOpsFixtures.mod("moda", "1.0.0")
                    .classEntry("com/example/Shared.class")
                    .write(mods, "moda.jar");
            ModOpsFixtures.mod("modb", "1.0.0")
                    .classEntry("com/example/Shared.class")
                    .write(mods, "modb.jar");

            List<String> log = new ArrayList<>();
            ModPolicyException failure = assertThrows(ModPolicyException.class,
                    () -> ModLoader.discover(mods, "client", log::add, ModPolicies.fatal()));
            assertEquals(1, failure.diagnostics().size());
            ModDiagnostic diagnostic = failure.diagnostics().get(0);
            assertEquals(ModDiagnostic.Severity.FATAL, diagnostic.severity());
            assertEquals(ModDiagnostic.Type.DUPLICATE_CLASS, diagnostic.type());
            assertEquals("com/example/Shared.class", diagnostic.subject());
            assertTrue(failure.getMessage().contains("DUPLICATE_CLASS"), failure.getMessage());
            assertEquals(List.of("[weaver] MOD CLASS CONFLICT class=com/example/Shared.class"
                    + " mods=moda,modb first=moda action=fatal"), log.stream()
                    .filter(line -> line.contains("MOD CLASS CONFLICT")).toList());
        } finally {
            ModOpsFixtures.deleteRecursively(root);
        }
    }

    @Test
    void apiShadowWarnLoadsTheMod() throws IOException {
        Path root = ModOpsFixtures.createRoot("weaver-modops-shadow-warn");
        try {
            Path mods = ModOpsFixtures.modsDir(root);
            ModOpsFixtures.mod("shadow", "1.0.0")
                    .classEntry("com/example/Ok.class")
                    .classEntry("net/weaver/api/WeaverApi.class")
                    .classEntry("net/weaver/api/game/TypedGameEventFactory.class")
                    .write(mods, "shadow.jar");

            List<String> log = new ArrayList<>();
            try (ModLoader loader = ModLoader.discover(mods, "client", log::add,
                    ModPolicies.warn())) {
                ModList report = loader.report();
                assertEquals(1, report.accepted().size());
                assertEquals(ModStatus.LOADED, report.accepted().get(0).status());
                assertEquals(2, report.diagnostics().stream()
                        .filter(item -> item.type() == ModDiagnostic.Type.API_SHADOW)
                        .count());
            }
            assertEquals(List.of(
                    "[weaver] MOD API SHADOW class=net/weaver/api/WeaverApi.class"
                            + " mod=shadow action=warn",
                    "[weaver] MOD API SHADOW"
                            + " class=net/weaver/api/game/TypedGameEventFactory.class"
                            + " mod=shadow action=warn"), log.stream()
                    .filter(line -> line.contains("MOD API SHADOW")).toList());
        } finally {
            ModOpsFixtures.deleteRecursively(root);
        }
    }

    @Test
    void apiShadowFatalThrowsAndClosesJars() throws IOException {
        Path root = ModOpsFixtures.createRoot("weaver-modops-shadow-fatal");
        try {
            Path mods = ModOpsFixtures.modsDir(root);
            ModOpsFixtures.mod("shadow", "1.0.0")
                    .classEntry("net/weaver/api/WeaverApi.class")
                    .classEntry("net/weaver/api/game/TypedGameEventFactory.class")
                    .write(mods, "shadow.jar");

            List<String> log = new ArrayList<>();
            ModPolicyException failure = assertThrows(ModPolicyException.class,
                    () -> ModLoader.discover(mods, "client", log::add, ModPolicies.fatal()));
            assertEquals(2, failure.diagnostics().size());
            assertTrue(failure.diagnostics().stream().allMatch(diagnostic ->
                    diagnostic.severity() == ModDiagnostic.Severity.FATAL
                            && diagnostic.type() == ModDiagnostic.Type.API_SHADOW));
            assertEquals(2, log.stream().filter(line ->
                    line.contains("MOD API SHADOW") && line.endsWith("action=fatal")).count());
        } finally {
            ModOpsFixtures.deleteRecursively(root);
        }
    }

    @Test
    void apiShadowDoesNotFlagClassesOnlyCompiledAgainstTheApi() throws IOException {
        Path root = ModOpsFixtures.createRoot("weaver-modops-shadow-compiled");
        try {
            Path mods = ModOpsFixtures.modsDir(root);
            ModOpsFixtures.mod("consumer", "1.0.0")
                    .classEntry("com/example/UsesWeaverApi.class")
                    .write(mods, "consumer.jar");

            List<String> log = new ArrayList<>();
            try (ModLoader loader = ModLoader.discover(mods, "client", log::add,
                    ModPolicies.warn())) {
                assertEquals(1, loader.report().accepted().size());
                assertTrue(loader.report().diagnostics().stream()
                        .noneMatch(item -> item.type() == ModDiagnostic.Type.API_SHADOW));
            }
            assertTrue(log.stream().noneMatch(line -> line.contains("MOD API SHADOW")));
        } finally {
            ModOpsFixtures.deleteRecursively(root);
        }
    }
}
