package net.weaver.api.game.data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.weaver.api.network.PayloadBuffer;
import net.weaver.api.registry.DataPackSnapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataPackFilesTest {

    private static Map<String, String> files(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }

    @Test
    void readsEntriesIntoTheirRegistry() {
        DataPackFiles.Result result = DataPackFiles.read("file/gems", 3, files(
                "examplemod/weaver/registry/examplemod/gems/ruby.json", "{\"color\":\"red\",\"hardness\":7}",
                "examplemod/weaver/registry/examplemod/gems/deep/onyx.json", "{\"color\":\"black\"}"));
        assertEquals(List.of(), result.problems());
        DataPackSnapshot snapshot = result.snapshot();
        assertEquals("file/gems", snapshot.packId());
        assertEquals(3, snapshot.priority());
        Map<String, Map<String, Object>> gems = snapshot.entries().get("examplemod:gems");
        assertEquals("red", gems.get("ruby").get("color"));
        assertEquals(7.0, gems.get("ruby").get("hardness"));
        assertTrue(gems.containsKey("deep/onyx"));
    }

    @Test
    void readsTagsKeyedByPackNamespace() {
        DataPackFiles.Result result = DataPackFiles.read("file/gems", 2, files(
                "mypack/weaver/tags/examplemod/gems/shiny.json",
                "{\"values\":[\"examplemod:ruby\",{\"id\":\"examplemod:onyx\",\"required\":true},\"#examplemod:dark\"],"
                        + "\"replace\":true}"));
        assertEquals(List.of(), result.problems());
        DataPackSnapshot.TagDeclaration shiny = result.snapshot().tags().get("examplemod:gems").get("mypack:shiny");
        assertEquals(2, shiny.priority());
        assertTrue(shiny.replace());
        assertEquals(List.of(DataPackSnapshot.TagValue.value("examplemod:ruby", false),
                DataPackSnapshot.TagValue.value("examplemod:onyx", true),
                DataPackSnapshot.TagValue.reference("examplemod:dark", false)), shiny.values());
    }

    @Test
    void tagPriorityOverridesThePackPriority() {
        DataPackFiles.Result result = DataPackFiles.read("p", 1, files(
                "a/weaver/tags/a/gems/t.json", "{\"values\":[],\"priority\":50}"));
        assertEquals(50, result.snapshot().tags().get("a:gems").get("a:t").priority());
    }

    @Test
    void reportsBadFilesAndKeepsTheRest() {
        DataPackFiles.Result result = DataPackFiles.read("p", 0, files(
                "a/weaver/registry/a/gems/broken.json", "{not json",
                "a/weaver/registry/a/gems/array.json", "[1,2]",
                "a/weaver/registry/a/Gems/upper.json", "{}",
                "a/weaver/registry/a/gems/good.json", "{\"ok\":true}"));
        assertEquals(3, result.problems().size(), result.problems().toString());
        assertEquals(Map.of("good", Map.of("ok", true)), result.snapshot().entries().get("a:gems"));
    }

    @Test
    void ignoresFilesOutsideTheWeaverTrees() {
        DataPackFiles.Result result = DataPackFiles.read("p", 0, files(
                "a/weaver/readme.txt", "x",
                "a/weaver/other/a/gems/x.json", "{}"));
        assertEquals(List.of(), result.problems());
        assertTrue(result.snapshot().entries().isEmpty());
        assertTrue(result.snapshot().tags().isEmpty());
    }

    @Test
    void syncCodecRoundTripsEntriesAndTags() {
        DataPackSnapshot snapshot = DataPackFiles.read("file/gems", 4, files(
                "examplemod/weaver/registry/examplemod/gems/ruby.json", "{\"color\":\"red\",\"tiers\":[1,2],\"extra\":null}",
                "examplemod/weaver/tags/examplemod/gems/shiny.json", "{\"values\":[\"#examplemod:dark\"],\"replace\":true}"))
                .snapshot();
        PackDataRegistries.Sync sync = new PackDataRegistries.Sync(UUID.randomUUID(), List.of(snapshot));
        PayloadBuffer out = PayloadBuffer.writer();
        PackDataRegistries.Sync.CODEC.encode(sync, out);
        PayloadBuffer in = PayloadBuffer.reader(out.toByteArray());
        PackDataRegistries.Sync decoded = PackDataRegistries.Sync.CODEC.decode(in);
        in.ensureFullyRead();
        assertEquals(sync.generation(), decoded.generation());
        DataPackSnapshot copy = decoded.snapshots().get(0);
        assertEquals("file/gems", copy.packId());
        assertEquals(4, copy.priority());
        assertEquals(snapshot.entries(), copy.entries());
        assertEquals(snapshot.tags(), copy.tags());
    }
}
