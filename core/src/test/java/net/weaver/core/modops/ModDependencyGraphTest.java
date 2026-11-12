package net.weaver.core.modops;

import net.weaver.core.ModLoader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModDependencyGraphTest {

    @Test
    void graphExposesDependenciesDependentsAndOrder() throws IOException {
        Path root = ModOpsFixtures.createRoot("weaver-modops-graph");
        try {
            Path mods = ModOpsFixtures.modsDir(root);
            ModOpsFixtures.mod("app", "1.0.0").dependency("lib", "*").write(mods, "app.jar");
            ModOpsFixtures.mod("lib", "1.0.0").write(mods, "lib.jar");

            ModList report;
            try (ModLoader loader = ModLoader.discover(mods, "client", line -> { },
                    ModPolicies.warn())) {
                report = loader.report();
            }
            ModDependencyGraph graph = ModDependencyGraph.from(report);
            assertEquals(Set.of("app", "lib"), graph.nodes());
            assertEquals(List.of("lib"), graph.dependenciesOf("app"));
            assertEquals(List.of("app"), graph.dependentsOf("lib"));
            assertEquals(List.of("lib", "app"), graph.topologicalOrder());
            assertTrue(graph.cycles().isEmpty());
            assertEquals(List.of(), graph.dependenciesOf("absent"));
            assertEquals(List.of(), graph.dependentsOf("absent"));
        } finally {
            ModOpsFixtures.deleteRecursively(root);
        }
    }

    @Test
    void graphFindsTwoNodeCycleAndFallsBackToSortedIds() throws IOException {
        Path root = ModOpsFixtures.createRoot("weaver-modops-graph-cycle");
        try {
            Path mods = ModOpsFixtures.modsDir(root);
            ModOpsFixtures.mod("a", "1.0.0").dependency("b", "*").write(mods, "a.jar");
            ModOpsFixtures.mod("b", "1.0.0").dependency("a", "*").write(mods, "b.jar");

            ModList report;
            try (ModLoader loader = ModLoader.discover(mods, "client", line -> { },
                    ModPolicies.warn())) {
                report = loader.report();
            }
            ModDependencyGraph graph = ModDependencyGraph.from(report);
            assertEquals(List.of(List.of("a", "b")), graph.cycles());
            assertEquals(List.of("a", "b"), graph.topologicalOrder());
        } finally {
            ModOpsFixtures.deleteRecursively(root);
        }
    }

    @Test
    void graphFindsSelfCycle() throws IOException {
        Path root = ModOpsFixtures.createRoot("weaver-modops-graph-self");
        try {
            Path mods = ModOpsFixtures.modsDir(root);
            ModOpsFixtures.mod("self", "1.0.0").dependency("self", "*").write(mods, "self.jar");

            ModList report;
            try (ModLoader loader = ModLoader.discover(mods, "client", line -> { },
                    ModPolicies.warn())) {
                report = loader.report();
            }
            ModDependencyGraph graph = ModDependencyGraph.from(report);
            assertEquals(List.of(List.of("self")), graph.cycles());
            assertEquals(List.of("self"), graph.topologicalOrder());
        } finally {
            ModOpsFixtures.deleteRecursively(root);
        }
    }

    @Test
    void optionalEdgesRequireMatchingVersions() throws IOException {
        Path matchingRoot = ModOpsFixtures.createRoot("weaver-modops-graph-optional");
        Path mismatchedRoot = ModOpsFixtures.createRoot("weaver-modops-graph-optional-mismatch");
        try {
            Path matching = ModOpsFixtures.modsDir(matchingRoot);
            ModOpsFixtures.mod("lib", "1.0.0").write(matching, "lib.jar");
            ModOpsFixtures.mod("app", "1.0.0").optional("lib", "^1.0.0")
                    .write(matching, "app.jar");
            ModList matchingReport;
            try (ModLoader loader = ModLoader.discover(matching, "client", line -> { },
                    ModPolicies.warn())) {
                matchingReport = loader.report();
            }
            assertEquals(List.of("lib"),
                    ModDependencyGraph.from(matchingReport).dependenciesOf("app"));

            Path mismatched = ModOpsFixtures.modsDir(mismatchedRoot);
            ModOpsFixtures.mod("lib", "1.0.0").write(mismatched, "lib.jar");
            ModOpsFixtures.mod("app", "1.0.0").optional("lib", ">=2.0.0")
                    .write(mismatched, "app.jar");
            ModList mismatchedReport;
            List<String> log = new ArrayList<>();
            try (ModLoader loader = ModLoader.discover(mismatched, "client", log::add,
                    ModPolicies.warn())) {
                mismatchedReport = loader.report();
            }
            assertEquals(List.of(),
                    ModDependencyGraph.from(mismatchedReport).dependenciesOf("app"));
            assertTrue(log.stream().anyMatch(line -> line.contains("MOD OPTIONAL VERSION")));
        } finally {
            ModOpsFixtures.deleteRecursively(matchingRoot);
            ModOpsFixtures.deleteRecursively(mismatchedRoot);
        }
    }
}
