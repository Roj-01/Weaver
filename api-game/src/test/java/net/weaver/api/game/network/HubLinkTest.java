package net.weaver.api.game.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;
import net.weaver.api.WeaverMods;
import net.weaver.api.network.ModFingerprints;
import net.weaver.api.network.NetworkModInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** What the client tells Weaver Hub about a server's mods, and what a server advertises. */
class HubLinkTest {
    private static WeaverMods.Mod mod(String id, String version, String side) {
        return new WeaverMods.Mod(id, version, id, side, "", List.of(), "", "", "");
    }

    @BeforeEach
    void install() {
        WeaverMods.install(List.of(mod("shared", "1.0.0", "both"), mod("minimap", "2.0.0", "client"),
                mod("old", "1.0.0", "both"), mod("tools", "3.0.0", "server")));
        ModFingerprints.install("2.2.0-test", Map.of("shared", "a".repeat(64), "old", "b".repeat(64)));
    }

    @AfterEach
    void reset() {
        WeaverMods.install(List.of());
        ModFingerprints.install("", Map.of());
    }

    @Test
    void matchingModsNeedNothing() {
        assertNull(HubLink.compare("srv:25565", List.of(NetworkModInfo.of("shared", "1.0.0"), NetworkModInfo.of("old", "1.0.0"))));
    }

    @Test
    void missingAndDifferentModsAreListed() {
        JsonObject request = HubLink.compare("srv:25565", List.of(
                NetworkModInfo.of("shared", "1.0.0"), NetworkModInfo.of("old", "1.1.0"), NetworkModInfo.of("extra", "0.5.0")));
        assertEquals("server-mods", request.get("type").getAsString());
        assertEquals("srv:25565", request.get("server").getAsString());
        assertEquals("extra", request.getAsJsonArray("missing").get(0).getAsJsonObject().get("id").getAsString());
        JsonObject different = request.getAsJsonArray("different").get(0).getAsJsonObject();
        assertEquals("old", different.get("id").getAsString());
        assertEquals("1.1.0", different.get("version").getAsString());
        assertEquals("1.0.0", different.get("local").getAsString());
    }

    @Test
    void vanillaServerPausesOnlyModsThatNeedAServer() {
        JsonObject request = HubLink.vanilla("srv:25565");
        assertEquals("vanilla-server", request.get("type").getAsString());
        assertEquals(List.of("shared", "old"), request.getAsJsonArray("paused").asList().stream().map(e -> e.getAsString()).toList());
        WeaverMods.install(List.of(mod("minimap", "2.0.0", "client")));
        assertNull(HubLink.vanilla("srv:25565"), "client-only mods join vanilla servers as they are");
    }

    @Test
    void requestsAreSentOnlyWhenTheHubStartedTheGame() {
        System.clearProperty(HubLink.PROPERTY);
        assertFalse(HubLink.send(HubLink.vanilla("srv:25565")));
        System.setProperty(HubLink.PROPERTY, "true");
        try {
            assertTrue(HubLink.send(HubLink.vanilla("srv:25565")));
        } finally {
            System.clearProperty(HubLink.PROPERTY);
        }
    }

    @Test
    void statusAdvertisesServerBoundModsWithFingerprints() {
        JsonObject weaver = StatusAdvertisement.json();
        assertEquals("2.2.0-test", weaver.get("loader").getAsString());
        assertEquals(2, weaver.getAsJsonArray("mods").size(), "client-only and server-only mods are not advertised");
        JsonObject shared = weaver.getAsJsonArray("mods").get(0).getAsJsonObject();
        assertEquals("shared", shared.get("id").getAsString());
        assertEquals("a".repeat(64), shared.get("sha256").getAsString());
        assertFalse(weaver.get("truncated").getAsBoolean());
        JsonObject status = new JsonObject();
        status.add("weaver", weaver);
        assertEquals(weaver, StatusAdvertisement.read(status));
        assertNull(StatusAdvertisement.read(new JsonObject()), "a vanilla server has no weaver field");
    }
}
