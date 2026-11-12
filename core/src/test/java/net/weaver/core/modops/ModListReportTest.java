package net.weaver.core.modops;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.weaver.core.ModLoader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModListReportTest {

    @Test
    void reportExposesFieldsLoadOrderAndStableJson() throws IOException {
        Path root = ModOpsFixtures.createRoot("weaver-modops-report");
        try {
            Path mods = ModOpsFixtures.modsDir(root);
            ModOpsFixtures.mod("lib", "1.2.0").name("Library")
                    .mainClass("com.example.Lib")
                    .mixin("lib.mixins.json")
                    .classEntry("com/example/Lib.class")
                    .write(mods, "lib.jar");
            ModOpsFixtures.mod("app", "2.0.0").name("Application")
                    .dependency("lib", ">=1.0.0")
                    .classEntry("com/example/App.class")
                    .write(mods, "app.jar");

            List<String> log = new ArrayList<>();
            ModList report;
            try (ModLoader loader = ModLoader.discover(mods, "client", log::add,
                    ModPolicies.warn())) {
                report = loader.report();
            }

            assertEquals("client", report.side());
            assertEquals(2, report.entries().size());
            assertEquals(List.of("app", "lib"), report.entries().stream()
                    .map(ModListEntry::id).toList());

            ModListEntry app = report.entries().stream()
                    .filter(entry -> entry.id().equals("app"))
                    .findFirst().orElseThrow();
            assertEquals("2.0.0", app.version());
            assertEquals("Application", app.name());
            assertEquals("both", app.side());
            assertEquals("26.3", app.minecraft());
            assertEquals("com.example.Mod", app.mainClass());
            assertEquals(Map.of("lib", ">=1.0.0"), app.dependencies());
            assertEquals(Map.of(), app.optional());
            assertEquals(Map.of(), app.breaks());
            assertEquals(List.of(), app.mixins());
            assertEquals(List.of(), app.accessTransformers());
            assertEquals(1, app.loadOrder());
            assertEquals(ModStatus.LOADED, app.status());
            assertEquals("", app.statusDetail());
            assertEquals("app.jar", app.file());

            ModListEntry lib = report.entries().stream()
                    .filter(entry -> entry.id().equals("lib"))
                    .findFirst().orElseThrow();
            assertEquals(List.of("lib.mixins.json"), lib.mixins());
            assertEquals(0, lib.loadOrder());

            assertEquals(List.of("lib", "app"), report.loadOrdered().stream()
                    .map(ModListEntry::id).toList());
            assertEquals(2, report.accepted().size());
            assertTrue(report.rejected().isEmpty());
            assertTrue(report.summaryLine().contains("loaded=2"), report.summaryLine());
            assertTrue(report.toTextLines().stream().anyMatch(line -> line.contains("mod id=app")));

            JsonObject json = JsonParser.parseString(report.toJson()).getAsJsonObject();
            assertEquals(ModList.SCHEMA_VERSION, json.get("schemaVersion").getAsInt());
            assertEquals("weaver", json.getAsJsonObject("loader").get("name").getAsString());
            assertFalse(json.getAsJsonObject("loader").get("version").getAsString().isBlank());
            assertEquals("client", json.get("side").getAsString());
            assertEquals(2, json.getAsJsonObject("summary").get("loaded").getAsInt());

            JsonArray modsJson = json.getAsJsonArray("mods");
            assertEquals(2, modsJson.size());
            JsonObject appJson = find(modsJson, "app");
            assertEquals("2.0.0", appJson.get("version").getAsString());
            assertEquals("both", appJson.get("side").getAsString());
            assertEquals("26.3", appJson.get("minecraft").getAsString());
            assertEquals("com.example.Mod", appJson.get("mainClass").getAsString());
            assertEquals(">=1.0.0", appJson.getAsJsonObject("dependencies").get("lib").getAsString());
            assertTrue(appJson.getAsJsonArray("mixins").isEmpty());
            assertTrue(appJson.getAsJsonArray("accessTransformers").isEmpty());
            assertEquals(1, appJson.get("loadOrder").getAsInt());
            assertEquals("LOADED", appJson.get("status").getAsString());
            assertEquals("app.jar", appJson.get("file").getAsString());
            assertTrue(json.has("diagnostics"));

            Path jsonFile = root.resolve("report.json");
            ModReportWriter.write(report, jsonFile, true);
            assertTrue(Files.readString(jsonFile).contains("\"schemaVersion\""));

            Path textFile = root.resolve("report.txt");
            ModReportWriter.write(report, textFile, false);
            assertTrue(Files.readString(textFile).contains("mod id=app"));
            assertTrue(Files.readString(textFile).contains("summary mods total=2"));
        } finally {
            ModOpsFixtures.deleteRecursively(root);
        }
    }

    private static JsonObject find(JsonArray mods, String id) {
        for (var item : mods) {
            JsonObject mod = item.getAsJsonObject();
            if (mod.get("id").getAsString().equals(id)) {
                return mod;
            }
        }
        throw new AssertionError("missing mod " + id);
    }
}
