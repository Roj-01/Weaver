package net.weaver.core.command;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonPermissionStoreTest
{
    @TempDir
    Path temporaryDirectory;

    @Test
    void roundTripPreservesDecisionsAndWritesDeterministicKeys() throws Exception
    {
        Path file = temporaryDirectory.resolve("permissions.json");
        JsonPermissionStore store = new JsonPermissionStore(file, message ->
        {
        });
        Map<String, Map<String, Boolean>> decisions = new LinkedHashMap<>();
        Map<String, Boolean> bob = new LinkedHashMap<>();
        bob.put("demo:command/zeta", false);
        bob.put("demo:command/alpha", true);
        decisions.put("player:second", bob);
        decisions.put("player:first", Map.of("demo:command/gate", true));

        store.save(decisions);

        assertEquals(decisions.get("player:second"),
                store.load().get("player:second"));
        assertEquals(Map.of("demo:command/gate", true),
                store.load().get("player:first"));

        String text = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(text.indexOf("\"player:first\"") < text.indexOf("\"player:second\""), text);
        assertTrue(text.indexOf("demo:command/alpha") < text.indexOf("demo:command/zeta"), text);
    }

    @Test
    void missingFileLoadsAsEmptyAndLogsOnce()
    {
        List<String> messages = new ArrayList<>();
        JsonPermissionStore store = new JsonPermissionStore(
                temporaryDirectory.resolve("missing.json"), messages::add);

        assertTrue(store.load().isEmpty());
        assertEquals(1, messages.size(), messages.toString());
        assertTrue(messages.get(0).contains("[weaver] PERMISSION STORE EMPTY"));

        assertTrue(store.load().isEmpty());
        assertEquals(1, messages.size(), messages.toString());
    }

    @Test
    void corruptFileLoadsAsEmptyAndLogsOnce() throws Exception
    {
        Path file = temporaryDirectory.resolve("corrupt.json");
        Files.writeString(file, "{ not json", StandardCharsets.UTF_8);
        List<String> messages = new ArrayList<>();
        JsonPermissionStore store = new JsonPermissionStore(file, messages::add);

        assertTrue(store.load().isEmpty());
        assertEquals(1, messages.size(), messages.toString());
        assertTrue(messages.get(0).contains("[weaver] PERMISSION STORE LOAD FAILED"));

        assertTrue(store.load().isEmpty());
        assertEquals(1, messages.size(), messages.toString());
    }

    @Test
    void corruptFileIsMovedAsideAndSurvivesTheNextSave() throws Exception
    {
        Path file = temporaryDirectory.resolve("permissions.json");
        String original = "{ \"subjects\": { \"player:a\": { \"demo:command/x\": false } ";
        Files.writeString(file, original, StandardCharsets.UTF_8);
        List<String> messages = new ArrayList<>();
        JsonPermissionStore store = new JsonPermissionStore(file, messages::add);

        assertTrue(store.load().isEmpty());
        Path backup = singleBackup();
        assertEquals(original, Files.readString(backup, StandardCharsets.UTF_8));
        assertTrue(messages.get(0).contains("[weaver] PERMISSION STORE LOAD FAILED"),
                messages.toString());
        assertTrue(messages.get(0).contains("backup=" + backup), messages.toString());

        store.save(Map.of("player:b", Map.of("demo:command/y", true)));

        assertEquals(original, Files.readString(backup, StandardCharsets.UTF_8));
        assertEquals(Map.of("player:b", Map.of("demo:command/y", true)), store.load());
        assertEquals(1, backups().size());
    }

    @Test
    void invalidUtf8AndEmptyFilesAreMovedAside() throws Exception
    {
        Path file = temporaryDirectory.resolve("permissions.json");
        byte[] invalid = {'{', (byte) 0xC3, (byte) 0x28, '}'};
        Files.write(file, invalid);
        JsonPermissionStore store = new JsonPermissionStore(file, message ->
        {
        });

        assertTrue(store.load().isEmpty());
        assertArrayEquals(invalid, Files.readAllBytes(singleBackup()));

        Path empty = temporaryDirectory.resolve("empty").resolve("permissions.json");
        Files.createDirectories(empty.getParent());
        Files.write(empty, new byte[0]);
        assertTrue(new JsonPermissionStore(empty, message ->
        {
        }).load().isEmpty());
        try (Stream<Path> listing = Files.list(empty.getParent()))
        {
            assertTrue(listing.anyMatch(path -> path.getFileName().toString()
                    .startsWith("permissions.json.corrupt-")));
        }
        assertFalse(Files.exists(empty));
    }

    @Test
    void saveLeavesNoTemporaryFiles() throws Exception
    {
        Path file = temporaryDirectory.resolve("permissions.json");
        JsonPermissionStore store = new JsonPermissionStore(file, message ->
        {
        });
        store.save(Map.of("player:a", Map.of("demo:command/x", true)));
        store.save(Map.of("player:a", Map.of("demo:command/x", false)));

        assertEquals(Map.of("player:a", Map.of("demo:command/x", false)), store.load());
        try (Stream<Path> listing = Files.list(temporaryDirectory))
        {
            assertEquals(List.of(file.getFileName().toString()),
                    listing.map(path -> path.getFileName().toString()).toList());
        }
    }

    private Path singleBackup() throws Exception
    {
        List<Path> backups = backups();
        assertEquals(1, backups.size(), backups.toString());
        return backups.get(0);
    }

    private List<Path> backups() throws Exception
    {
        try (Stream<Path> listing = Files.list(temporaryDirectory))
        {
            return listing.filter(path ->
                    {
                        String name = path.getFileName().toString();
                        return name.startsWith("permissions.json.corrupt-")
                                && name.endsWith(".bak");
                    })
                    .toList();
        }
    }

    @Test
    void invalidSubjectsShapeLoadsAsEmptyAndLogsOnce() throws Exception
    {
        Path file = temporaryDirectory.resolve("invalid.json");
        Files.writeString(file, "{\"subjects\":[]}", StandardCharsets.UTF_8);
        List<String> messages = new ArrayList<>();
        JsonPermissionStore store = new JsonPermissionStore(file, messages::add);

        assertTrue(store.load().isEmpty());
        assertEquals(1, messages.size(), messages.toString());
    }
}
