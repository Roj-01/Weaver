package net.weaver.api.client;

import java.util.List;

/**
 * Service-provider interface implemented by the Weaver core client backend.
 *
 * <p>The API module never references game classes. A backend bridges the
 * game-free values in this package to the live client. {@link ClientRegistry}
 * installs a backend supplied by the loader or a test, or resolves
 * {@code net.weaver.core.client.CoreClientBackend} reflectively on first use.
 *
 * <p>The facade calls these methods synchronously. Registration, screen, HUD,
 * tooltip, and chunk methods are documented as client/render-thread operations
 * and must enforce that requirement by throwing {@link ClientThreadException}
 * when invoked from another thread. Implementations should isolate listener
 * failures so one mod cannot break rendering or the tick loop, and should never
 * throw checked exceptions.
 */
public interface ClientBackend
{
    /**
     * Whether this backend can service client calls in the current process.
     *
     * <p>This method may be called from any thread, must be cheap, and must not
     * throw. A dedicated-server backend returns {@code false}.
     *
     * @return {@code true} when client operations can be serviced
     */
    boolean isAvailable();

    /**
     * Installs the backend.
     *
     * <p>Called once by {@link ClientRegistry#install(ClientBackend)} after any
     * previous backend has been uninstalled. Implementations may register their
     * injected hooks here. This method may run on any thread.
     */
    void install();

    /**
     * Uninstalls the backend and releases its hooks and registrations.
     *
     * <p>Called by {@link ClientRegistry#uninstall()} and
     * {@link ClientRegistry#reset()}. This method is idempotent, may run on any
     * thread, and should be fail-soft because it also runs during loader
     * shutdown.
     */
    void uninstall();

    /**
     * Registers a key mapping and the action run when it activates.
     *
     * <p>Must be called on the client/render thread; otherwise throws
     * {@link ClientThreadException}. The action is invoked on the client thread.
     *
     * @param spec   the mapping description, never {@code null}
     * @param action the action run on activation, never {@code null}
     * @return a non-null registration handle
     */
    ClientRegistration registerKeyMapping(KeyMappingSpec spec, Runnable action);

    /**
     * Registers a screen factory.
     *
     * <p>Must be called on the client/render thread; otherwise throws
     * {@link ClientThreadException}. The factory is later invoked through
     * {@link #openScreen(String)}.
     *
     * @param factory the factory, never {@code null}
     * @return a non-null registration handle
     */
    ClientRegistration registerScreenFactory(ScreenFactory factory);

    /**
     * Registers a HUD layer.
     *
     * <p>Must be called on the client/render thread; otherwise throws
     * {@link ClientThreadException}. Layers render in ascending
     * {@link HudLayer#order()}.
     *
     * @param layer the layer, never {@code null}
     * @return a non-null registration handle
     */
    ClientRegistration registerHudLayer(HudLayer layer);

    /**
     * Registers an item tooltip provider.
     *
     * <p>Must be called on the client/render thread; otherwise throws
     * {@link ClientThreadException}.
     *
     * @param provider the provider, never {@code null}
     * @return a non-null registration handle
     */
    ClientRegistration registerTooltipProvider(TooltipProvider provider);

    /**
     * Registers a client chunk listener.
     *
     * <p>Must be called on the client/render thread; otherwise throws
     * {@link ClientThreadException}. The same listener instance may be
     * registered only once.
     *
     * @param listener the listener, never {@code null}
     * @return a non-null registration handle
     * @throws ClientException when the backend does not support chunk listeners
     */
    default ClientRegistration registerChunkListener(ClientChunkListener listener)
    {
        throw new ClientException("chunk listeners are not supported by this backend");
    }

    /**
     * Dispatches a chunk-load event to the active chunk listeners.
     *
     * <p>Must be called on the client thread. The built-in client chunk mixin
     * calls this from the game's chunk-load path. The default is a no-op so an
     * older backend remains binary compatible.
     *
     * @param context the immutable chunk snapshot, never {@code null}
     */
    default void chunkLoaded(ClientChunkContext context)
    {
    }

    /**
     * Dispatches a chunk-unload event to the active chunk listeners.
     *
     * <p>Must be called on the client thread. The built-in client chunk mixin
     * calls this from the game's chunk-unload path. The default is a no-op so
     * an older backend remains binary compatible.
     *
     * @param context the immutable chunk snapshot, never {@code null}
     */
    default void chunkUnloaded(ClientChunkContext context)
    {
    }

    /**
     * Dispatches a block-update event to the active chunk listeners.
     *
     * <p>Must be called on the client thread. The built-in client chunk mixin
     * calls this from the server-confirmed block-update path. The default is a
     * no-op so an older backend remains binary compatible.
     *
     * @param context the immutable block snapshot, never {@code null}
     */
    default void blockUpdated(ClientBlockContext context)
    {
    }

    /**
     * Registers a client tick listener for one phase.
     *
     * <p>Must be called on the client/render thread; otherwise throws
     * {@link ClientThreadException}.
     *
     * @param listener the listener, never {@code null}
     * @param pre      {@code true} for the start-of-tick phase, {@code false}
     *                 for the end-of-tick phase
     * @return a non-null registration handle
     */
    ClientRegistration registerTickListener(ClientTickListener listener, boolean pre);

    /**
     * Registers a client resource reload listener.
     *
     * <p>Must be called on the client/render thread; otherwise throws
     * {@link ClientThreadException}.
     *
     * @param listener the listener, never {@code null}
     * @return a non-null registration handle
     */
    ClientRegistration registerResourceReloadListener(ResourceReloadListener listener);

    /**
     * Opens the screen produced by a registered screen factory.
     *
     * <p>Must be called on the client/render thread; otherwise throws
     * {@link ClientThreadException}. An unknown id has no effect. A factory
     * that returns {@code null} cancels the open request.
     *
     * @param screenFactoryId the non-blank factory id
     */
    void openScreen(String screenFactoryId);

    /**
     * Renders all active HUD layers into the supplied context.
     *
     * <p>Must be called on the render thread; otherwise throws
     * {@link ClientThreadException}. Layers run in ascending
     * {@link HudLayer#order()} and must not mutate game state or the world.
     *
     * @param context the immutable render snapshot, never {@code null}
     */
    void renderHud(HudRenderContext context);

    /**
     * Collects tooltip lines from the active providers.
     *
     * <p>Must be called on the render thread; otherwise throws
     * {@link ClientThreadException}. Returns a non-null list of plain-text
     * lines, possibly empty. The returned list may be immutable; the facade
     * makes its own copy.
     *
     * @param context the immutable tooltip snapshot, never {@code null}
     * @return the collected lines, never {@code null}
     */
    List<String> tooltipLines(TooltipContext context);
}
