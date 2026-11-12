package net.weaver.core.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DurableFilesTest
{
    @TempDir
    Path tempDir;

    @Test
    void writeReplacesTheTargetWithFullyWrittenContent() throws Exception
    {
        Path target = tempDir.resolve("nested").resolve("data.json");
        byte[] content = "{\"k\":\"v\"}\n".getBytes(StandardCharsets.UTF_8);
        AtomicLong sizeAtMove = new AtomicLong(-1);

        DurableFiles.write(target, content, (source, destination) ->
        {
            sizeAtMove.set(Files.size(source));
            DurableFiles.moveReplacing(source, destination);
        });

        assertEquals(content.length, sizeAtMove.get());
        assertEquals("{\"k\":\"v\"}\n", Files.readString(target, StandardCharsets.UTF_8));
        assertEquals(List.of("data.json"), names(target.getParent()));
    }

    @Test
    void failedMoveKeepsThePreviousTargetAndRemovesTheTemporaryFile() throws Exception
    {
        Path target = tempDir.resolve("data.json");
        Files.writeString(target, "old", StandardCharsets.UTF_8);

        assertThrows(IOException.class, () -> DurableFiles.write(target,
                "new".getBytes(StandardCharsets.UTF_8), (source, destination) ->
                {
                    throw new IOException("move boom");
                }));

        assertEquals("old", Files.readString(target, StandardCharsets.UTF_8));
        assertEquals(List.of("data.json"), names(tempDir));
    }

    @Test
    void backupPathsAreUniqueAndSanitized() throws Exception
    {
        Path file = tempDir.resolve("permissions.json");
        Path first = DurableFiles.backupPath(file, "corrupt");
        Files.writeString(first, "x");
        Path second = DurableFiles.backupPath(file, "corrupt");

        assertNotEquals(first, second);
        assertTrue(first.getFileName().toString().startsWith("permissions.json.corrupt-"));
        assertTrue(first.getFileName().toString().endsWith(".bak"));
        assertTrue(DurableFiles.backupPath(file, "bad reason!").getFileName().toString()
                .startsWith("permissions.json.bad-reason-"));
    }

    @Test
    void forcingADirectoryNeverThrows()
    {
        assertDoesNotThrow(() -> DurableFiles.forceDirectory(tempDir));
        assertDoesNotThrow(() -> DurableFiles.forceDirectory(tempDir.resolve("missing")));
        assertDoesNotThrow(() -> DurableFiles.forceDirectory(null));
    }

    private static List<String> names(Path directory) throws IOException
    {
        try (Stream<Path> listing = Files.list(directory))
        {
            return listing.map(path -> path.getFileName().toString()).sorted().toList();
        }
    }
}
