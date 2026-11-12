package net.weaver.api.game.network;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.weaver.api.network.NetworkProtocol;
import net.weaver.api.network.PayloadBuffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistryFingerprintTest {

    private static RegistryFingerprint of(Object... pairs) {
        Map<String, List<String>> registries = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            @SuppressWarnings("unchecked")
            List<String> ids = (List<String>) pairs[i + 1];
            registries.put((String) pairs[i], ids);
        }
        return RegistryFingerprint.of(registries);
    }

    @Test
    void equalFingerprintsMatch() {
        RegistryFingerprint server = of("minecraft:item", List.of("examplemod:ruby", "examplemod:sapphire"));
        RegistryFingerprint client = of("minecraft:item", List.of("examplemod:ruby", "examplemod:sapphire"));
        assertNull(RegistryFingerprint.compare(server, client));
    }

    @Test
    void reportsEntriesMissingOnTheClient() {
        RegistryFingerprint server = of("minecraft:item", List.of("examplemod:ruby", "examplemod:sapphire"));
        RegistryFingerprint client = of("minecraft:item", List.of("examplemod:ruby"));
        assertEquals("registry minecraft:item differs from the server: missing here examplemod:sapphire",
                RegistryFingerprint.compare(server, client));
    }

    @Test
    void reportsEntriesTheServerDoesNotHave() {
        RegistryFingerprint server = of();
        RegistryFingerprint client = of("minecraft:block", List.of("othermod:lamp"));
        assertEquals("registry minecraft:block differs from the server: not on the server othermod:lamp",
                RegistryFingerprint.compare(server, client));
    }

    @Test
    void reportsBothSidesAndSummarizesLongLists() {
        RegistryFingerprint server = of("minecraft:item", List.of("a:1", "a:2", "a:3", "a:4", "a:5"));
        RegistryFingerprint client = of("minecraft:item", List.of("b:1"));
        assertEquals("registry minecraft:item differs from the server: missing here a:1, a:2, a:3 and 2 more;"
                + " not on the server b:1", RegistryFingerprint.compare(server, client));
    }

    @Test
    void reportsTheSameEntriesInADifferentOrder() {
        RegistryFingerprint server = of("minecraft:item", List.of("examplemod:ruby", "examplemod:sapphire"));
        RegistryFingerprint client = of("minecraft:item", List.of("examplemod:sapphire", "examplemod:ruby"));
        String reason = RegistryFingerprint.compare(server, client);
        assertTrue(reason.startsWith("registry minecraft:item differs from the server: same entries in a"
                + " different order (first: examplemod:ruby is entry 1"), reason);
    }

    @Test
    void reasonFitsTheDisconnectLimit() {
        List<String> longIds = java.util.stream.IntStream.range(0, 3)
                .mapToObj(i -> "mod:" + "x".repeat(240) + i).toList();
        String reason = RegistryFingerprint.compare(of("minecraft:item", longIds), of());
        assertTrue(reason.length() <= NetworkProtocol.MAX_REASON_CHARS, "length=" + reason.length());
    }

    @Test
    void codecRoundTripKeepsOrder() {
        RegistryFingerprint original = of("minecraft:item", List.of("examplemod:ruby", "examplemod:sapphire"),
                "minecraft:block", List.of("examplemod:ruby_block"));
        PayloadBuffer out = PayloadBuffer.writer();
        RegistryFingerprint.CODEC.encode(original, out);
        PayloadBuffer in = PayloadBuffer.reader(out.toByteArray());
        RegistryFingerprint decoded = RegistryFingerprint.CODEC.decode(in);
        in.ensureFullyRead();
        assertEquals(original, decoded);
        assertEquals(List.of("minecraft:item", "minecraft:block"), List.copyOf(decoded.registries().keySet()));
        assertEquals(3, decoded.entryCount());
    }

    @Test
    void codecRejectsOversizedCounts() {
        PayloadBuffer out = PayloadBuffer.writer();
        out.writeVarInt(RegistryFingerprint.MAX_REGISTRIES + 1);
        assertThrows(IllegalArgumentException.class,
                () -> RegistryFingerprint.CODEC.decode(PayloadBuffer.reader(out.toByteArray())));
    }
    @Test
    void digestsAloneStillDetectADifference() {
        RegistryFingerprint server = of("minecraft:item", List.of("a:1", "a:2")).withoutIds();
        RegistryFingerprint same = of("minecraft:item", List.of("a:1", "a:2"));
        RegistryFingerprint reordered = of("minecraft:item", List.of("a:2", "a:1"));
        RegistryFingerprint shorter = of("minecraft:item", List.of("a:1"));
        assertNull(RegistryFingerprint.compare(server, same));
        assertEquals("registry minecraft:item differs from the server: 2 modded entries on the server, 2 here"
                + " (different entries or order)", RegistryFingerprint.compare(server, reordered));
        assertEquals("registry minecraft:item differs from the server: 2 modded entries on the server, 1 here",
                RegistryFingerprint.compare(server, shorter));
    }

    @Test
    void largeFingerprintsFitThePayloadAndKeepDigests() {
        List<String> many = java.util.stream.IntStream.range(0, 100_000).mapToObj(i -> "bigmod:item_" + i).toList();
        RegistryFingerprint original = of("minecraft:item", many, "minecraft:block", List.of("bigmod:block"));
        PayloadBuffer out = PayloadBuffer.writer();
        RegistryFingerprint.CODEC.encode(original, out);
        assertTrue(out.toByteArray().length < RegistryFingerprint.DETAIL_BYTES, "bytes=" + out.toByteArray().length);
        RegistryFingerprint decoded = RegistryFingerprint.CODEC.decode(PayloadBuffer.reader(out.toByteArray()));
        assertEquals(100_001, decoded.entryCount());
        assertTrue(!decoded.registries().get("minecraft:item").listed());
        assertTrue(decoded.registries().get("minecraft:block").listed());
        assertNull(RegistryFingerprint.compare(decoded, original));
    }
}
