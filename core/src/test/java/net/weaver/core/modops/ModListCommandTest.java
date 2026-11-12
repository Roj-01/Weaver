package net.weaver.core.modops;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModListCommandTest {

    @Test
    void writesJsonReportToStdoutAndLoaderLogsToStderr() throws IOException {
        Path root = ModOpsFixtures.createRoot("weaver-modops-command");
        try {
            Path mods = ModOpsFixtures.modsDir(root);
            ModOpsFixtures.mod("lib", "1.0.0").write(mods, "lib.jar");
            ModOpsFixtures.mod("app", "1.0.0").dependency("lib", "*").write(mods, "app.jar");

            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();
            int code;
            try (PrintStream out = new PrintStream(stdout, true, StandardCharsets.UTF_8);
                 PrintStream err = new PrintStream(stderr, true, StandardCharsets.UTF_8)) {
                code = ModListCommand.run(new String[]{
                        "--mods", mods.toString(), "--side", "client", "--json",
                        "--policy", "warn"}, out, err);
            }

            assertEquals(0, code);
            JsonObject json = JsonParser.parseString(
                    stdout.toString(StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals(2, json.getAsJsonArray("mods").size());
            assertTrue(stderr.toString(StandardCharsets.UTF_8).contains("MOD READ"),
                    stderr.toString(StandardCharsets.UTF_8));
        } finally {
            ModOpsFixtures.deleteRecursively(root);
        }
    }

    @Test
    void fatalPolicyReturnsOne() throws IOException {
        Path root = ModOpsFixtures.createRoot("weaver-modops-command-fatal");
        try {
            Path mods = ModOpsFixtures.modsDir(root);
            ModOpsFixtures.mod("moda", "1.0.0")
                    .classEntry("com/example/Shared.class").write(mods, "moda.jar");
            ModOpsFixtures.mod("modb", "1.0.0")
                    .classEntry("com/example/Shared.class").write(mods, "modb.jar");

            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();
            int code;
            try (PrintStream out = new PrintStream(stdout, true, StandardCharsets.UTF_8);
                 PrintStream err = new PrintStream(stderr, true, StandardCharsets.UTF_8)) {
                code = ModListCommand.run(new String[]{
                        "--mods", mods.toString(), "--policy", "fatal"}, out, err);
            }

            assertEquals(1, code);
            String errorText = stderr.toString(StandardCharsets.UTF_8);
            assertTrue(errorText.contains("MOD CLASS CONFLICT"), errorText);
            assertTrue(errorText.contains("action=fatal"), errorText);
            assertTrue(errorText.contains("MOD POLICY FAILED"), errorText);
            assertTrue(stdout.toString(StandardCharsets.UTF_8).isBlank());
        } finally {
            ModOpsFixtures.deleteRecursively(root);
        }
    }

    @Test
    void invalidArgumentsReturnTwo() {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int code;
        try (PrintStream out = new PrintStream(stdout, true, StandardCharsets.UTF_8);
             PrintStream err = new PrintStream(stderr, true, StandardCharsets.UTF_8)) {
            code = ModListCommand.run(new String[]{"--side", "everywhere"}, out, err);
        }
        assertEquals(2, code);
        assertTrue(stderr.toString(StandardCharsets.UTF_8).contains("invalid side"));
    }
}
