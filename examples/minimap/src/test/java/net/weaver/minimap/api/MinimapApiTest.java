package net.weaver.minimap.api;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinimapApiTest
{
    private static final MapLayerContext LAYER_CONTEXT =
            new MapLayerContext("minecraft:overworld", 0, 0, 1.0D, 100, 100, false);
    private static final MapColorContext COLOR_CONTEXT =
            new MapColorContext("minecraft:overworld", 1, 64, 2, 0xFF000000);
    private static final WaypointQuery WAYPOINT_QUERY =
            new WaypointQuery("minecraft:overworld", 0, 0, 128, false);
    private static final EntityIconContext ICON_CONTEXT =
            new EntityIconContext("minecraft:zombie", "hostile", "minecraft:overworld", false);
    private static final HudOverlayContext HUD_CONTEXT =
            new HudOverlayContext(false, 1920, 1080, 1.0D, "minecraft:overworld", 0, 64, 0,
                    1.0D);
    private static final MapActionContext ACTION_CONTEXT =
            new MapActionContext("minecraft:overworld", true, 0, 0, 1.0D);

    @Test
    void duplicateRegistrationIsIdempotentAndNewestWins()
    {
        MinimapApi api = new MinimapApi();
        MapLayerProvider first = context -> new MapLayer("first", "", 0, true, "");
        MapLayerProvider second = context -> new MapLayer("second", "", 0, true, "");

        assertTrue(api.registerMapLayer("layer", first));
        assertFalse(api.registerMapLayer("layer", second));
        assertFalse(api.registerMapLayer("layer", second));

        assertEquals(1, api.mapLayerProviders().size());
        assertEquals("layer", api.mapLayerProviders().get(0).id());
        assertEquals("second", api.resolveMapLayers(LAYER_CONTEXT).get(0).id());
    }

    @Test
    void snapshotListsAreImmutableAndDoNotTrackLaterRegistrations()
    {
        MinimapApi api = new MinimapApi();
        api.registerMapLayer("first", context -> null);
        List<MinimapApi.Registered<MapLayerProvider>> snapshot = api.mapLayerProviders();

        api.registerMapLayer("second", context -> null);

        assertEquals(1, snapshot.size());
        assertEquals(2, api.mapLayerProviders().size());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.add(new MinimapApi.Registered<>("third", context -> null)));
    }

    @Test
    void unregisterRemovesProviders()
    {
        MinimapApi api = new MinimapApi();
        api.registerMapColor("color", context -> new MapColor(1));

        assertTrue(api.unregisterMapColor("color"));
        assertFalse(api.unregisterMapColor("color"));
        assertTrue(api.mapColorProviders().isEmpty());
    }

    @Test
    void mapLayerFailuresAreIsolatedAndLoggedOnce()
    {
        AtomicInteger logs = new AtomicInteger();
        MinimapApi api = new MinimapApi(() ->
        {
        }, message -> logs.incrementAndGet());
        api.registerMapLayer("broken", context ->
        {
            throw new IllegalStateException("boom");
        });
        api.registerMapLayer("working",
                context -> new MapLayer("working", "", 1, true, ""));

        assertEquals(1, api.resolveMapLayers(LAYER_CONTEXT).size());
        assertEquals(1, api.resolveMapLayers(LAYER_CONTEXT).size());
        assertEquals(1, logs.get());
    }

    @Test
    void mapColorUsesFirstNonNullResult()
    {
        MinimapApi api = new MinimapApi();
        api.registerMapColor("first", context -> null);
        api.registerMapColor("second", context -> new MapColor(0xFF112233));
        api.registerMapColor("third", context -> new MapColor(0xFF445566));

        assertEquals(0xFF112233, api.resolveMapColor(COLOR_CONTEXT, 0xFF000000));
        assertEquals(0xFF000000, new MinimapApi()
                .resolveMapColor(COLOR_CONTEXT, 0xFF000000));
    }

    @Test
    void waypointAndHudFailuresAreIsolated()
    {
        AtomicInteger logs = new AtomicInteger();
        MinimapApi api = new MinimapApi(() ->
        {
        }, message -> logs.incrementAndGet());
        api.registerWaypoints("broken", query ->
        {
            throw new IllegalArgumentException("boom");
        });
        api.registerWaypoints("working", query -> List.of(new WaypointMarker("id", "Home",
                "minecraft:overworld", 0, 64, 0, 0xFF00FF00, null, null, false)));
        api.registerHudOverlay("broken", context ->
        {
            throw new IllegalArgumentException("boom");
        });
        api.registerHudOverlay("working", context -> List.of(new HudOverlayLine("line", 0,
                HudAnchor.TOP_LEFT, 0, 0, 0)));

        assertEquals(1, api.resolveWaypoints(WAYPOINT_QUERY).size());
        assertEquals(1, api.resolveHudOverlays(HUD_CONTEXT).size());
        assertEquals(2, logs.get());
    }

    @Test
    void entityIconUsesFirstNonBlankResult()
    {
        MinimapApi api = new MinimapApi();
        api.registerEntityIcon("blank", context -> " ");
        api.registerEntityIcon("actual", context -> "star");

        assertEquals("star", api.resolveEntityIcon(ICON_CONTEXT, "pin"));
        assertEquals("pin", new MinimapApi().resolveEntityIcon(ICON_CONTEXT, "pin"));
    }

    @Test
    void mapActionsAreDedupedAndInvocationIsIsolated()
    {
        AtomicInteger logs = new AtomicInteger();
        AtomicInteger invoked = new AtomicInteger();
        MinimapApi api = new MinimapApi(() ->
        {
        }, message -> logs.incrementAndGet());
        api.registerMapAction("first", context -> List.of(new MapAction("shared", "Shared",
                "", false, () ->
        {
            invoked.incrementAndGet();
            throw new IllegalStateException("boom");
        })));
        api.registerMapAction("second", context -> List.of(
                new MapAction("shared", "Ignored", "", false, () ->
                {
                }),
                new MapAction("other", "Other", "", false, invoked::incrementAndGet)));

        List<MapAction> actions = api.resolveMapActions(ACTION_CONTEXT);

        assertEquals(2, actions.size());
        assertEquals("shared", actions.get(0).id());
        actions.get(0).invoke().run();
        actions.get(0).invoke().run();
        assertEquals(2, invoked.get());
        assertEquals(1, logs.get());
        assertTrue(api.invokeMapAction("other", ACTION_CONTEXT));
        assertEquals(3, invoked.get());
        assertFalse(api.invokeMapAction("missing", ACTION_CONTEXT));
    }

    @Test
    void threadCheckIsEnforced()
    {
        MinimapApi api = new MinimapApi(() ->
        {
            throw new IllegalStateException("not the client thread");
        });

        assertThrows(IllegalStateException.class,
                () -> api.registerMapLayer("layer", context -> null));
        assertThrows(IllegalStateException.class, api::mapLayerProviders);
        assertThrows(IllegalStateException.class, () -> api.resolveMapLayers(LAYER_CONTEXT));
    }

    @Test
    void clearRemovesEveryProvider()
    {
        MinimapApi api = new MinimapApi();
        api.registerMapLayer("layer", context -> null);
        api.registerMapColor("color", context -> null);
        api.registerWaypoints("waypoints", query -> List.of());
        api.registerEntityIcon("icon", context -> null);
        api.registerHudOverlay("hud", context -> List.of());
        api.registerMapAction("action", context -> List.of());

        api.clear();

        assertTrue(api.mapLayerProviders().isEmpty());
        assertTrue(api.mapColorProviders().isEmpty());
        assertTrue(api.waypointProviders().isEmpty());
        assertTrue(api.entityIconProviders().isEmpty());
        assertTrue(api.hudOverlayProviders().isEmpty());
        assertTrue(api.mapActionProviders().isEmpty());
    }

    @Test
    void invalidIdsAndMissingProvidersAreRejected()
    {
        MinimapApi api = new MinimapApi();

        assertThrows(IllegalArgumentException.class,
                () -> api.registerMapLayer(" ", context -> null));
        assertThrows(NullPointerException.class,
                () -> api.registerMapLayer("layer", null));
        assertThrows(NullPointerException.class, () -> api.unregisterMapLayer(null));
    }
}
