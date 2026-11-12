package net.weaver.minimap.api;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The public extension point for other client mods.
 *
 * <p>Other mods register providers by id. Registration is idempotent: calling
 * a register method again with the same id leaves exactly one entry and the
 * newest provider wins. Registration, unregistration, snapshot retrieval, and
 * provider invocation are confined to the client thread; the constructor
 * accepts a {@link ThreadCheck} so the integrator can enforce that contract.
 * A permissive check is used by {@link #shared()} and the no-argument
 * constructor.</p>
 *
 * <p>Every provider call is isolated. A provider that throws is skipped, the
 * failure is logged at most once per provider and call type, and the remaining
 * providers still run. No server sync and no loader dependency is involved;
 * the API only describes client-side data and actions.</p>
 */
public final class MinimapApi
{
    private static final MinimapApi SHARED = new MinimapApi(() ->
    {
    }, message ->
    {
    });

    private final ThreadCheck threadCheck;
    private final Consumer<String> log;
    private final Map<String, MapLayerProvider> mapLayers = new LinkedHashMap<>();
    private final Map<String, MapColorProvider> mapColors = new LinkedHashMap<>();
    private final Map<String, WaypointProvider> waypoints = new LinkedHashMap<>();
    private final Map<String, EntityIconProvider> entityIcons = new LinkedHashMap<>();
    private final Map<String, HudOverlayProvider> hudOverlays = new LinkedHashMap<>();
    private final Map<String, MapActionProvider> mapActions = new LinkedHashMap<>();
    private final Set<String> reportedFailures = new HashSet<>();

    /**
     * Creates an API with permissive thread checking and no logging.
     */
    public MinimapApi()
    {
        this(() ->
        {
        }, message ->
        {
        });
    }

    /**
     * Creates an API with an explicit thread check and no logging.
     *
     * @param threadCheck the client-thread assertion, never {@code null}
     * @throws NullPointerException when {@code threadCheck} is {@code null}
     */
    public MinimapApi(ThreadCheck threadCheck)
    {
        this(threadCheck, message ->
        {
        });
    }

    /**
     * Creates an API with an explicit thread check and log sink.
     *
     * @param threadCheck the client-thread assertion, never {@code null}
     * @param log         the log sink, never {@code null}
     * @throws NullPointerException when an argument is {@code null}
     */
    public MinimapApi(ThreadCheck threadCheck, Consumer<String> log)
    {
        this.threadCheck = Objects.requireNonNull(threadCheck, "threadCheck");
        this.log = Objects.requireNonNull(log, "log");
    }

    /**
     * Returns the process-wide permissive API instance.
     *
     * @return the shared instance, never {@code null}
     */
    public static MinimapApi shared()
    {
        return SHARED;
    }

    /**
     * Registers a map layer provider.
     *
     * @param id       the provider id, non-blank
     * @param provider the provider, never {@code null}
     * @return {@code true} when the id was new, {@code false} when replaced
     */
    public boolean registerMapLayer(String id, MapLayerProvider provider)
    {
        checkThread();
        return put(mapLayers, id, provider);
    }

    /**
     * Removes a map layer provider.
     *
     * @param id the provider id, non-blank
     * @return {@code true} when a provider was removed
     */
    public boolean unregisterMapLayer(String id)
    {
        checkThread();
        return mapLayers.remove(requireId(id)) != null;
    }

    /**
     * Registers a map color provider.
     *
     * @param id       the provider id, non-blank
     * @param provider the provider, never {@code null}
     * @return {@code true} when the id was new, {@code false} when replaced
     */
    public boolean registerMapColor(String id, MapColorProvider provider)
    {
        checkThread();
        return put(mapColors, id, provider);
    }

    /**
     * Removes a map color provider.
     *
     * @param id the provider id, non-blank
     * @return {@code true} when a provider was removed
     */
    public boolean unregisterMapColor(String id)
    {
        checkThread();
        return mapColors.remove(requireId(id)) != null;
    }

    /**
     * Registers a waypoint provider.
     *
     * @param id       the provider id, non-blank
     * @param provider the provider, never {@code null}
     * @return {@code true} when the id was new, {@code false} when replaced
     */
    public boolean registerWaypoints(String id, WaypointProvider provider)
    {
        checkThread();
        return put(waypoints, id, provider);
    }

    /**
     * Removes a waypoint provider.
     *
     * @param id the provider id, non-blank
     * @return {@code true} when a provider was removed
     */
    public boolean unregisterWaypoints(String id)
    {
        checkThread();
        return waypoints.remove(requireId(id)) != null;
    }

    /**
     * Registers an entity icon provider.
     *
     * @param id       the provider id, non-blank
     * @param provider the provider, never {@code null}
     * @return {@code true} when the id was new, {@code false} when replaced
     */
    public boolean registerEntityIcon(String id, EntityIconProvider provider)
    {
        checkThread();
        return put(entityIcons, id, provider);
    }

    /**
     * Removes an entity icon provider.
     *
     * @param id the provider id, non-blank
     * @return {@code true} when a provider was removed
     */
    public boolean unregisterEntityIcon(String id)
    {
        checkThread();
        return entityIcons.remove(requireId(id)) != null;
    }

    /**
     * Registers a HUD overlay provider.
     *
     * @param id       the provider id, non-blank
     * @param provider the provider, never {@code null}
     * @return {@code true} when the id was new, {@code false} when replaced
     */
    public boolean registerHudOverlay(String id, HudOverlayProvider provider)
    {
        checkThread();
        return put(hudOverlays, id, provider);
    }

    /**
     * Removes a HUD overlay provider.
     *
     * @param id the provider id, non-blank
     * @return {@code true} when a provider was removed
     */
    public boolean unregisterHudOverlay(String id)
    {
        checkThread();
        return hudOverlays.remove(requireId(id)) != null;
    }

    /**
     * Registers a map action provider.
     *
     * @param id       the provider id, non-blank
     * @param provider the provider, never {@code null}
     * @return {@code true} when the id was new, {@code false} when replaced
     */
    public boolean registerMapAction(String id, MapActionProvider provider)
    {
        checkThread();
        return put(mapActions, id, provider);
    }

    /**
     * Removes a map action provider.
     *
     * @param id the provider id, non-blank
     * @return {@code true} when a provider was removed
     */
    public boolean unregisterMapAction(String id)
    {
        checkThread();
        return mapActions.remove(requireId(id)) != null;
    }

    /**
     * Returns a snapshot of the registered map layer providers.
     *
     * @return an immutable list in registration order
     */
    public List<Registered<MapLayerProvider>> mapLayerProviders()
    {
        checkThread();
        return snapshot(mapLayers);
    }

    /**
     * Returns a snapshot of the registered map color providers.
     *
     * @return an immutable list in registration order
     */
    public List<Registered<MapColorProvider>> mapColorProviders()
    {
        checkThread();
        return snapshot(mapColors);
    }

    /**
     * Returns a snapshot of the registered waypoint providers.
     *
     * @return an immutable list in registration order
     */
    public List<Registered<WaypointProvider>> waypointProviders()
    {
        checkThread();
        return snapshot(waypoints);
    }

    /**
     * Returns a snapshot of the registered entity icon providers.
     *
     * @return an immutable list in registration order
     */
    public List<Registered<EntityIconProvider>> entityIconProviders()
    {
        checkThread();
        return snapshot(entityIcons);
    }

    /**
     * Returns a snapshot of the registered HUD overlay providers.
     *
     * @return an immutable list in registration order
     */
    public List<Registered<HudOverlayProvider>> hudOverlayProviders()
    {
        checkThread();
        return snapshot(hudOverlays);
    }

    /**
     * Returns a snapshot of the registered map action providers.
     *
     * @return an immutable list in registration order
     */
    public List<Registered<MapActionProvider>> mapActionProviders()
    {
        checkThread();
        return snapshot(mapActions);
    }

    /**
     * Resolves all contributed map layers, isolating provider failures.
     *
     * @param context the render context, never {@code null}
     * @return an immutable list in registration order
     */
    public List<MapLayer> resolveMapLayers(MapLayerContext context)
    {
        checkThread();
        Objects.requireNonNull(context, "context");
        List<MapLayer> result = new ArrayList<>();
        for (Map.Entry<String, MapLayerProvider> entry : mapLayers.entrySet())
        {
            try
            {
                MapLayer layer = entry.getValue().layer(context);
                if (layer != null)
                {
                    result.add(layer);
                }
            }
            catch (Throwable failure)
            {
                report("map-layer", entry.getKey(), failure);
            }
        }
        return List.copyOf(result);
    }

    /**
     * Resolves a block color override, isolating provider failures.
     *
     * @param context  the block context, never {@code null}
     * @param fallback the color used when no provider overrides it
     * @return the first non-null override, or {@code fallback}
     */
    public int resolveMapColor(MapColorContext context, int fallback)
    {
        checkThread();
        Objects.requireNonNull(context, "context");
        for (Map.Entry<String, MapColorProvider> entry : mapColors.entrySet())
        {
            try
            {
                MapColor color = entry.getValue().color(context);
                if (color != null)
                {
                    return color.argb();
                }
            }
            catch (Throwable failure)
            {
                report("map-color", entry.getKey(), failure);
            }
        }
        return fallback;
    }

    /**
     * Resolves all contributed waypoint markers, isolating provider failures.
     *
     * @param query the query, never {@code null}
     * @return an immutable list in provider registration order
     */
    public List<WaypointMarker> resolveWaypoints(WaypointQuery query)
    {
        checkThread();
        Objects.requireNonNull(query, "query");
        List<WaypointMarker> result = new ArrayList<>();
        for (Map.Entry<String, WaypointProvider> entry : waypoints.entrySet())
        {
            try
            {
                List<WaypointMarker> markers = entry.getValue().waypoints(query);
                if (markers != null)
                {
                    for (WaypointMarker marker : markers)
                    {
                        if (marker != null)
                        {
                            result.add(marker);
                        }
                    }
                }
            }
            catch (Throwable failure)
            {
                report("waypoints", entry.getKey(), failure);
            }
        }
        return List.copyOf(result);
    }

    /**
     * Resolves an entity icon override, isolating provider failures.
     *
     * @param context  the entity context, never {@code null}
     * @param fallback the default icon
     * @return the first non-blank override, or {@code fallback}
     */
    public String resolveEntityIcon(EntityIconContext context, String fallback)
    {
        checkThread();
        Objects.requireNonNull(context, "context");
        for (Map.Entry<String, EntityIconProvider> entry : entityIcons.entrySet())
        {
            try
            {
                String icon = entry.getValue().icon(context);
                if (icon != null && !icon.isBlank())
                {
                    return icon;
                }
            }
            catch (Throwable failure)
            {
                report("entity-icon", entry.getKey(), failure);
            }
        }
        return fallback;
    }

    /**
     * Resolves all contributed HUD overlay lines, isolating provider failures.
     *
     * @param context the HUD context, never {@code null}
     * @return an immutable list in provider registration order
     */
    public List<HudOverlayLine> resolveHudOverlays(HudOverlayContext context)
    {
        checkThread();
        Objects.requireNonNull(context, "context");
        List<HudOverlayLine> result = new ArrayList<>();
        for (Map.Entry<String, HudOverlayProvider> entry : hudOverlays.entrySet())
        {
            try
            {
                List<HudOverlayLine> lines = entry.getValue().lines(context);
                if (lines != null)
                {
                    for (HudOverlayLine line : lines)
                    {
                        if (line != null)
                        {
                            result.add(line);
                        }
                    }
                }
            }
            catch (Throwable failure)
            {
                report("hud-overlay", entry.getKey(), failure);
            }
        }
        return List.copyOf(result);
    }

    /**
     * Resolves all contributed map actions with isolated invocation.
     *
     * <p>Action ids are unique in the result; the first provider to return an
     * id wins. Each returned action wraps its body so a later throwing action
     * is logged and cannot break the screen.</p>
     *
     * @param context the map context, never {@code null}
     * @return an immutable list in provider registration order
     */
    public List<MapAction> resolveMapActions(MapActionContext context)
    {
        checkThread();
        Objects.requireNonNull(context, "context");
        Map<String, MapAction> byId = new LinkedHashMap<>();
        for (Map.Entry<String, MapActionProvider> entry : mapActions.entrySet())
        {
            try
            {
                List<MapAction> actions = entry.getValue().actions(context);
                if (actions == null)
                {
                    continue;
                }
                for (MapAction action : actions)
                {
                    if (action != null)
                    {
                        byId.putIfAbsent(action.id(), isolated(entry.getKey(), action));
                    }
                }
            }
            catch (Throwable failure)
            {
                report("map-action", entry.getKey(), failure);
            }
        }
        return List.copyOf(byId.values());
    }

    /**
     * Invokes the first action with an id, isolating failures.
     *
     * @param actionId the action id, never {@code null}
     * @param context  the map context, never {@code null}
     * @return {@code true} when a matching action was invoked
     */
    public boolean invokeMapAction(String actionId, MapActionContext context)
    {
        Objects.requireNonNull(actionId, "actionId");
        for (MapAction action : resolveMapActions(context))
        {
            if (action.id().equals(actionId))
            {
                action.invoke().run();
                return true;
            }
        }
        return false;
    }

    /**
     * Removes every provider and resets the failure log memory.
     */
    public void clear()
    {
        checkThread();
        mapLayers.clear();
        mapColors.clear();
        waypoints.clear();
        entityIcons.clear();
        hudOverlays.clear();
        mapActions.clear();
        reportedFailures.clear();
    }

    private void checkThread()
    {
        threadCheck.checkClientThread();
    }

    private <T> boolean put(Map<String, T> registry, String id, T provider)
    {
        Objects.requireNonNull(provider, "provider");
        return registry.put(requireId(id), provider) == null;
    }

    private static String requireId(String id)
    {
        Objects.requireNonNull(id, "id");
        if (id.isBlank())
        {
            throw new IllegalArgumentException("id must not be blank");
        }
        return id;
    }

    private static <T> List<Registered<T>> snapshot(Map<String, T> registry)
    {
        List<Registered<T>> result = new ArrayList<>(registry.size());
        for (Map.Entry<String, T> entry : registry.entrySet())
        {
            result.add(new Registered<>(entry.getKey(), entry.getValue()));
        }
        return List.copyOf(result);
    }

    private MapAction isolated(String providerId, MapAction action)
    {
        return new MapAction(action.id(), action.label(), action.icon(),
                action.requiresConfirmation(), () ->
        {
            try
            {
                action.invoke().run();
            }
            catch (Throwable failure)
            {
                report("map-action-invoke", providerId + "/" + action.id(), failure);
            }
        });
    }

    private void report(String kind, String id, Throwable failure)
    {
        if (reportedFailures.add(kind + "/" + id))
        {
            log.accept("[weaver-minimap] provider " + kind + " '" + id + "' failed: "
                    + failure);
        }
    }

    /**
     * One registered provider.
     *
     * @param id       the registration id, never {@code null}
     * @param provider the provider, never {@code null}
     * @param <T>      the provider type
     */
    public record Registered<T>(String id, T provider)
    {
        /**
         * Validates the entry.
         *
         * @throws NullPointerException when an argument is {@code null}
         */
        public Registered
        {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(provider, "provider");
        }
    }
}
