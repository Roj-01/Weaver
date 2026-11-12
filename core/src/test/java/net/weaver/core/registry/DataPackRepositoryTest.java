package net.weaver.core.registry;

import net.weaver.api.registry.DataRegistry;
import net.weaver.api.registry.DataRegistryManager;
import net.weaver.api.registry.TagKey;
import net.weaver.api.registry.TagView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataPackRepositoryTest
{
    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp()
    {
        DataRegistryManager.reset();
    }

    @AfterEach
    void tearDown()
    {
        DataRegistryManager.reset();
    }

    @Test
    void higherPriorityPackWinsAndPrioritiesAreDeterministic() throws IOException
    {
        Path low = pack("low");
        Path high = pack("high");
        metadata(low, 15);
        metadata(high, 20);
        entry(low, "testpack", "example", "gems", "ruby", "{\"color\":\"blue\"}");
        entry(high, "testpack", "example", "gems", "ruby", "{\"color\":\"red\"}");

        DataPackRepository.Report report = DataPackRepository.load(List.of(low, high));

        assertTrue(report.problems().isEmpty(), () -> report.problems().toString());
        assertEquals(2, report.snapshots().size());
        assertEquals(0, report.snapshots().get(0).priority());
        assertEquals(1, report.snapshots().get(1).priority());
        assertEquals("low", report.snapshots().get(0).packId());
        assertEquals("high", report.snapshots().get(1).packId());
        assertEquals(15, report.packs().get(0).packFormat());
        assertEquals(20, report.packs().get(1).packFormat());

        DataRegistry<String> registry = colors("example:gems");
        DataRegistryManager.ApplyReport applied = DataRegistryManager.loadAll(report.snapshots());

        assertEquals(1, applied.added());
        assertEquals(1, applied.replaced());
        assertEquals("red", registry.get("example:ruby"));

        DataRegistryManager.reset();
        DataRegistry<String> reversedRegistry = colors("example:gems");
        DataPackRepository.Report reversed = DataPackRepository.load(List.of(high, low));
        DataRegistryManager.loadAll(reversed.snapshots());

        assertEquals("blue", reversedRegistry.get("example:ruby"),
                "list order, not pack_format, must decide precedence");
    }

    @Test
    void replacingTagDropsLowerPriorityValues() throws IOException
    {
        Path low = pack("tag-low");
        Path high = pack("tag-high");
        metadata(low, 15);
        metadata(high, 15);
        entry(low, "testpack", "example", "gems", "ruby", "{\"color\":\"red\"}");
        entry(low, "testpack", "example", "gems", "sapphire", "{\"color\":\"blue\"}");
        tag(low, "testpack", "example", "gems", "shiny",
                "{\"values\":[\"example:ruby\"]}");
        tag(high, "testpack", "example", "gems", "shiny",
                "{\"values\":[\"example:sapphire\"],\"replace\":true}");

        DataPackRepository.Report report = DataPackRepository.load(List.of(low, high));
        DataRegistry<String> registry = colors("example:gems");
        DataRegistryManager.loadAll(report.snapshots());

        TagView<String> view = registry.tag(
                TagKey.of(registry.registryKey(), "testpack:shiny"));

        assertEquals(List.of("blue"), view.values());
        assertFalse(view.contains("red"));
    }

    @Test
    void packMetadataAcceptsArrayIntegerAndObjectFormats() throws IOException
    {
        Path array = pack("array");
        metadata(array, "{\"pack\":{\"pack_format\":15,\"description\":\"array\","
                + "\"supported_formats\":[10,20]}}");
        Path single = pack("single");
        metadata(single, "{\"pack\":{\"pack_format\":17,"
                + "\"supported_formats\":17}}");
        Path object = pack("object");
        metadata(object, "{\"pack\":{\"pack_format\":12,"
                + "\"supported_formats\":{\"min_inclusive\":11,"
                + "\"max_inclusive\":13}}}");
        entry(array, "testpack", "example", "gems", "ruby", "{}");
        entry(single, "testpack", "example", "gems", "ruby", "{}");
        entry(object, "testpack", "example", "gems", "ruby", "{}");

        DataPackRepository.Report report = DataPackRepository.load(
                List.of(array, single, object));

        assertTrue(report.problems().isEmpty(), () -> report.problems().toString());
        assertEquals(3, report.packs().size());
        assertEquals(10, report.packs().get(0).minFormat());
        assertEquals(20, report.packs().get(0).maxFormat());
        assertTrue(report.packs().get(0).supports(15));
        assertFalse(report.packs().get(0).supports(21));
        assertEquals(17, report.packs().get(1).minFormat());
        assertEquals(17, report.packs().get(1).maxFormat());
        assertEquals(11, report.packs().get(2).minFormat());
        assertEquals(13, report.packs().get(2).maxFormat());
    }

    @Test
    void malformedPackIsSkippedAndReported() throws IOException
    {
        Path valid = pack("valid");
        Path invalid = pack("invalid");
        metadata(valid, 15);
        entry(valid, "testpack", "example", "gems", "ruby", "{}");
        metadata(invalid, "{\"pack\":{\"pack_format\":\"not-a-number\"}}");
        entry(invalid, "testpack", "example", "gems", "sapphire", "{}");

        DataPackRepository.Report report = DataPackRepository.load(List.of(invalid, valid));

        assertEquals(1, report.snapshots().size());
        assertEquals("valid", report.snapshots().get(0).packId());
        assertEquals(1, report.packs().size());
        assertTrue(report.problems().stream()
                        .anyMatch(problem -> problem.contains("pack.mcmeta")
                                && problem.contains("pack_format")),
                () -> report.problems().toString());
        assertTrue(report.snapshots().get(0).entries()
                .get("example:gems").containsKey("ruby"));
        assertFalse(report.snapshots().get(0).entries()
                .get("example:gems").containsKey("sapphire"));
    }

    @Test
    void traversalAndUnsafePathsAreRejected() throws IOException
    {
        assertFalse(DataPackRepository.isSafeRelative("../escape"));
        assertFalse(DataPackRepository.isSafeRelative("a/../b"));
        assertFalse(DataPackRepository.isSafeRelative("/absolute"));
        assertFalse(DataPackRepository.isSafeRelative("a\\b"));
        assertFalse(DataPackRepository.isSafeRelative(".."));
        assertFalse(DataPackRepository.isSafeRelative("a//b"));
        assertTrue(DataPackRepository.isSafeRelative("a/b"));

        Path root = pack("traversal");
        metadata(root, 15);
        entry(root, "testpack", "example", "gems", "a..b", "{}");

        DataPackRepository.Report report = DataPackRepository.load(List.of(root));

        assertTrue(report.problems().stream()
                        .anyMatch(problem -> problem.contains("invalid entry path")),
                () -> report.problems().toString());
        assertTrue(report.snapshots().get(0).entries()
                .getOrDefault("example:gems", Map.of()).isEmpty());
    }

    @Test
    void oversizedFileIsSkippedAndReported() throws IOException
    {
        Path root = pack("oversized");
        metadata(root, 15);
        Path file = file(root, "data/testpack/weaver/registry/example/gems/huge.json");
        Files.write(file, new byte[(int) DataPackRepository.MAX_FILE_BYTES + 1]);

        DataPackRepository.Report report = DataPackRepository.load(List.of(root));

        assertTrue(report.snapshots().get(0).entries()
                .getOrDefault("example:gems", Map.of()).isEmpty());
        assertTrue(report.problems().stream()
                        .anyMatch(problem -> problem.contains("file too large")),
                () -> report.problems().toString());
    }

    @Test
    void singlePackFacadeMatchesRepositoryReader() throws IOException
    {
        Path root = pack("facade-pack");
        entry(root, "testpack", "example", "gems", "ruby", "{\"color\":\"red\"}");
        tag(root, "testpack", "example", "gems", "shiny",
                "{\"values\":[\"example:ruby\"],\"priority\":12}");

        DataPackLoader.Report old = DataPackLoader.read(root, "facade-pack", 7);
        DataPackRepository.Report repository = DataPackRepository.load(List.of(root), 7);

        assertEquals(1, repository.snapshots().size());
        assertEquals("facade-pack", old.snapshot().packId());
        assertEquals(old.snapshot().packId(), repository.snapshots().get(0).packId());
        assertEquals(old.snapshot().priority(), repository.snapshots().get(0).priority());
        assertEquals(old.snapshot().entries(), repository.snapshots().get(0).entries());
        assertEquals(old.snapshot().tags(), repository.snapshots().get(0).tags());
        assertEquals(old.problems(), repository.problems());
    }

    @Test
    void missingDataDirectoryMatchesLegacyProblem() throws IOException
    {
        Path root = pack("empty");

        DataPackLoader.Report old = DataPackLoader.read(root, "empty-pack", 3);
        DataPackRepository.Report single =
                DataPackRepository.readSingle(root, "empty-pack", 3);
        DataPackRepository.Report repository = DataPackRepository.load(List.of(root), 3);

        assertEquals(List.of("data: missing data directory"), old.problems());
        assertEquals(old.problems(), single.problems());
        assertTrue(old.snapshot().entries().isEmpty());
        assertTrue(repository.snapshots().get(0).entries().isEmpty());
        assertTrue(repository.problems().stream()
                        .anyMatch(problem -> problem.contains("data: missing data directory")),
                () -> repository.problems().toString());
    }

    private Path pack(String name) throws IOException
    {
        return Files.createDirectories(tempDir.resolve(name));
    }

    private static DataRegistry<String> colors(String registryId)
    {
        return DataRegistryManager.create(registryId, String.class,
                (entryId, data) -> (String) data.get("color"));
    }

    private static void metadata(Path root, int format) throws IOException
    {
        metadata(root, "{\"pack\":{\"pack_format\":" + format
                + ",\"description\":\"test pack\"}}");
    }

    private static void metadata(Path root, String json) throws IOException
    {
        Files.writeString(root.resolve("pack.mcmeta"), json, StandardCharsets.UTF_8);
    }

    private static void entry(Path root, String packNamespace, String registryNamespace,
                              String registryPath, String entryPath, String json)
            throws IOException
    {
        Files.writeString(file(root, "data/" + packNamespace + "/weaver/registry/"
                        + registryNamespace + "/" + registryPath + "/" + entryPath + ".json"),
                json, StandardCharsets.UTF_8);
    }

    private static void tag(Path root, String packNamespace, String registryNamespace,
                            String registryPath, String tagPath, String json)
            throws IOException
    {
        Files.writeString(file(root, "data/" + packNamespace + "/weaver/tags/"
                        + registryNamespace + "/" + registryPath + "/" + tagPath + ".json"),
                json, StandardCharsets.UTF_8);
    }

    private static Path file(Path root, String relative) throws IOException
    {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        return file;
    }
}
