package net.weaver.api.client;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Thread-safe static facade for the client extension API.
 *
 * <p>This package is client-only. It is safe to load on a dedicated server
 * because every game class is reached reflectively and only after confirming
 * that {@code net.minecraft.client.Minecraft} is present. Registration, screen,
 * HUD, and tooltip calls without a usable client throw
 * {@link ClientUnavailableException}; nothing is forwarded to a server and no
 * server-side state is created.
 *
 * <p>A backend is either installed by the loader or a test through
 * {@link #install(ClientBackend)}, or resolved once from
 * {@code net.weaver.core.client.CoreClientBackend} on the first call that needs
 * it. Resolution is attempted only after a non-initializing presence check for
 * the client game class, and every resolution failure is reported as
 * {@link ClientUnavailableException} with the original cause.
 *
 * <p>All methods synchronize on the facade class monitor, so installation,
 * registration, counting, and teardown are serialized. {@link #isAvailable()}
 * never triggers reflective resolution and never throws; it reports only an
 * installed or previously resolved backend, so the loader should install a
 * backend or a caller should use {@link #backend()} when an early availability
 * probe must force resolution.
 *
 * <p>Except for {@link #install(ClientBackend)}, {@link #uninstall()},
 * {@link #reset()}, {@link #isAvailable()}, {@link #backend()}, and
 * {@link #registrationCount()}, calls must be made on the client/render thread.
 * The backend enforces this with {@link ClientThreadException}; the facade
 * itself does not guess the thread.
 *
 * <p>{@link #renderHud(HudRenderContext)} is called automatically by the
 * built-in client HUD mixin when the optional game API is present.
 * {@link #tooltipLines(TooltipContext)} remains a facade entry point for manual
 * hooks; the built-in tooltip mixin reaches the same backend through
 * {@link ClientTooltipHook}. The chunk dispatch methods are integration entry
 * points for the built-in chunk mixin and are no-ops until a backend is
 * installed.
 */
public final class ClientRegistry
{
    private static final String MINECRAFT_CLIENT_CLASS = "net.minecraft.client.Minecraft";
    private static final String CORE_BACKEND_CLASS = "net.weaver.core.client.CoreClientBackend";

    private static final Set<ClientRegistration> REGISTRATIONS =
            Collections.newSetFromMap(new IdentityHashMap<>());

    private static ClientBackend backend;

    private ClientRegistry()
    {
    }

    /**
     * Installs a backend and calls its {@link ClientBackend#install()} method.
     *
     * <p>Installing the backend that is already active is a no-op, so
     * {@code install()} runs at most once per active backend. Installing a
     * different backend uninstalls the previous one, clears registrations
     * created through this facade, then installs the replacement. If the
     * replacement fails to install, the facade is left without a backend and
     * the failure is rethrown.
     *
     * @param newBackend the backend to install, never {@code null}
     * @throws NullPointerException when {@code newBackend} is {@code null}
     */
    public static synchronized void install(ClientBackend newBackend)
    {
        Objects.requireNonNull(newBackend, "backend");
        if (backend == newBackend)
        {
            return;
        }
        final ClientBackend previous = backend;
        backend = null;
        REGISTRATIONS.clear();
        if (previous != null)
        {
            uninstallQuietly(previous);
        }
        backend = newBackend;
        try
        {
            newBackend.install();
        }
        catch (RuntimeException | Error failure)
        {
            backend = null;
            uninstallQuietly(newBackend);
            throw failure;
        }
    }

    /**
     * Uninstalls the active backend, whether it was installed explicitly or
     * resolved reflectively, and clears facade-tracked registrations.
     *
     * <p>Calling this method when no backend is active is a no-op. Failures
     * raised by the backend's {@link ClientBackend#uninstall()} are reported to
     * standard error and swallowed so loader shutdown can continue. This method
     * may be called from any thread.
     */
    public static synchronized void uninstall()
    {
        final ClientBackend current = backend;
        backend = null;
        REGISTRATIONS.clear();
        if (current != null)
        {
            uninstallQuietly(current);
        }
    }

    /**
     * Reports whether a client backend is active and usable.
     *
     * <p>This method never throws, never loads client game classes, and never
     * triggers reflective resolution. It returns {@code false} when no backend
     * is installed or cached, or when the backend reports itself unavailable.
     *
     * @return {@code true} only when a backend exists and is available
     */
    public static synchronized boolean isAvailable()
    {
        final ClientBackend current = backend;
        if (current == null)
        {
            return false;
        }
        try
        {
            return current.isAvailable();
        }
        catch (Throwable ignored)
        {
            return false;
        }
    }

    /**
     * Returns the installed or reflectively resolved backend.
     *
     * <p>This is the first-use entry point that triggers lazy resolution. It
     * must be called on the client/render thread when the result will be used
     * for client operations.
     *
     * @return the active backend, never {@code null}
     * @throws ClientUnavailableException when the client is absent or the core
     *                                    backend cannot be resolved
     */
    public static synchronized ClientBackend backend()
    {
        return requireBackend();
    }

    /**
     * Registers a key mapping and its activation action.
     *
     * <p>Must be called on the client/render thread. The action runs on the
     * client thread when the mapping activates.
     *
     * @param spec   the mapping description, never {@code null}
     * @param action the action run on activation, never {@code null}
     * @return the backend registration handle, never {@code null}
     * @throws ClientUnavailableException when no client backend is available
     */
    public static synchronized ClientRegistration registerKeyMapping(KeyMappingSpec spec,
                                                                      Runnable action)
    {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(action, "action");
        return track(requireBackend().registerKeyMapping(spec, action));
    }

    /**
     * Registers a key mapping that opens the spec's screen factory.
     *
     * <p>Equivalent to registering an action that calls
     * {@link #openScreen(String)} with {@link KeyMappingSpec#screenFactoryId()}.
     * Must be called on the client/render thread.
     *
     * @param spec the mapping description with a non-null screen factory id
     * @return the backend registration handle, never {@code null}
     * @throws IllegalArgumentException when the spec has no screen factory id
     * @throws ClientUnavailableException when no client backend is available
     */
    public static synchronized ClientRegistration registerKeyMapping(KeyMappingSpec spec)
    {
        Objects.requireNonNull(spec, "spec");
        final String screenFactoryId = spec.screenFactoryId();
        if (screenFactoryId == null)
        {
            throw new IllegalArgumentException("spec.screenFactoryId() must not be null; "
                    + "use the two-argument overload or Builder.screenFactory(String)");
        }
        return registerKeyMapping(spec, () -> openScreen(screenFactoryId));
    }

    /**
     * Registers a screen factory.
     *
     * <p>Must be called on the client/render thread.
     *
     * @param factory the factory, never {@code null}
     * @return the backend registration handle, never {@code null}
     * @throws ClientUnavailableException when no client backend is available
     */
    public static synchronized ClientRegistration registerScreenFactory(ScreenFactory factory)
    {
        Objects.requireNonNull(factory, "factory");
        return track(requireBackend().registerScreenFactory(factory));
    }

    /**
     * Registers a HUD layer.
     *
     * <p>Must be called on the client/render thread. Lower
     * {@link HudLayer#order()} values render first.
     *
     * @param layer the layer, never {@code null}
     * @return the backend registration handle, never {@code null}
     * @throws ClientUnavailableException when no client backend is available
     */
    public static synchronized ClientRegistration registerHudLayer(HudLayer layer)
    {
        Objects.requireNonNull(layer, "layer");
        return track(requireBackend().registerHudLayer(layer));
    }

    /**
     * Registers an item tooltip provider.
     *
     * <p>Must be called on the client/render thread.
     *
     * @param provider the provider, never {@code null}
     * @return the backend registration handle, never {@code null}
     * @throws ClientUnavailableException when no client backend is available
     */
    public static synchronized ClientRegistration registerTooltipProvider(
            TooltipProvider provider)
    {
        Objects.requireNonNull(provider, "provider");
        return track(requireBackend().registerTooltipProvider(provider));
    }

    /**
     * Registers a client chunk listener.
     *
     * <p>Must be called on the client/render thread. The built-in client chunk
     * mixin dispatches load, unload, and block-update events on the client
     * thread.
     *
     * @param listener the listener, never {@code null}
     * @return the backend registration handle, never {@code null}
     * @throws ClientException            when the same listener instance is
     *                                    already registered
     * @throws ClientUnavailableException when no client backend is available
     */
    public static synchronized ClientRegistration registerChunkListener(
            ClientChunkListener listener)
    {
        Objects.requireNonNull(listener, "listener");
        return track(requireBackend().registerChunkListener(listener));
    }

    /**
     * Dispatches a chunk-load event to the installed backend.
     *
     * <p>Integration entry point for the built-in client chunk mixin. No-op
     * when no backend has been installed; it never installs one. Must be
     * called on the client thread.
     *
     * @param context the immutable chunk snapshot, never {@code null}
     */
    @net.weaver.api.annotation.Internal
    public static synchronized void chunkLoaded(ClientChunkContext context)
    {
        Objects.requireNonNull(context, "context");
        final ClientBackend current = backend;
        if (current != null)
        {
            current.chunkLoaded(context);
        }
    }

    /**
     * Dispatches a chunk-unload event to the installed backend.
     *
     * <p>Integration entry point for the built-in client chunk mixin. No-op
     * when no backend has been installed; it never installs one. Must be
     * called on the client thread.
     *
     * @param context the immutable chunk snapshot, never {@code null}
     */
    @net.weaver.api.annotation.Internal
    public static synchronized void chunkUnloaded(ClientChunkContext context)
    {
        Objects.requireNonNull(context, "context");
        final ClientBackend current = backend;
        if (current != null)
        {
            current.chunkUnloaded(context);
        }
    }

    /**
     * Dispatches a block-update event to the installed backend.
     *
     * <p>Integration entry point for the built-in client chunk mixin. No-op
     * when no backend has been installed; it never installs one. Must be
     * called on the client thread.
     *
     * @param context the immutable block snapshot, never {@code null}
     */
    @net.weaver.api.annotation.Internal
    public static synchronized void blockUpdated(ClientBlockContext context)
    {
        Objects.requireNonNull(context, "context");
        final ClientBackend current = backend;
        if (current != null)
        {
            current.blockUpdated(context);
        }
    }

    /**
     * Registers a client tick listener for the start-of-tick phase.
     *
     * <p>Must be called on the client/render thread. Same as
     * {@code registerTickListener(listener, true)}.
     *
     * @param listener the listener, never {@code null}
     * @return the backend registration handle, never {@code null}
     * @throws ClientUnavailableException when no client backend is available
     */
    public static synchronized ClientRegistration registerTickListener(
            ClientTickListener listener)
    {
        return registerTickListener(listener, true);
    }

    /**
     * Registers a client tick listener for one phase.
     *
     * <p>Must be called on the client/render thread.
     *
     * @param listener the listener, never {@code null}
     * @param pre      {@code true} for the start-of-tick phase, {@code false}
     *                 for the end-of-tick phase
     * @return the backend registration handle, never {@code null}
     * @throws ClientUnavailableException when no client backend is available
     */
    public static synchronized ClientRegistration registerTickListener(
            ClientTickListener listener, boolean pre)
    {
        Objects.requireNonNull(listener, "listener");
        return track(requireBackend().registerTickListener(listener, pre));
    }

    /**
     * Registers a client resource reload listener.
     *
     * <p>Must be called on the client/render thread.
     *
     * @param listener the listener, never {@code null}
     * @return the backend registration handle, never {@code null}
     * @throws ClientUnavailableException when no client backend is available
     */
    public static synchronized ClientRegistration registerResourceReloadListener(
            ResourceReloadListener listener)
    {
        Objects.requireNonNull(listener, "listener");
        return track(requireBackend().registerResourceReloadListener(listener));
    }

    /**
     * Opens the screen produced by a registered screen factory.
     *
     * <p>Must be called on the client/render thread. An unknown id has no
     * effect.
     *
     * @param screenFactoryId the non-blank factory id
     * @throws IllegalArgumentException when the id is {@code null} or blank
     * @throws ClientUnavailableException when no client backend is available
     */
    public static synchronized void openScreen(String screenFactoryId)
    {
        ClientValidation.requireNonBlank(screenFactoryId, "screenFactoryId");
        requireBackend().openScreen(screenFactoryId);
    }

    /**
     * Renders all active HUD layers.
     *
     * <p>Integration entry point for the injected render hook. The built-in
     * client HUD mixin calls this method on the render thread.
     *
     * @param context the immutable render snapshot, never {@code null}
     * @throws ClientUnavailableException when no client backend is available
     */
    @net.weaver.api.annotation.Internal
    public static synchronized void renderHud(HudRenderContext context)
    {
        Objects.requireNonNull(context, "context");
        requireBackend().renderHud(context);
    }

    /**
     * Collects tooltip lines from the active providers.
     *
     * <p>Integration entry point for a future injected tooltip hook. Must be
     * called on the render thread by the loader or a client mixin until that
     * hook exists. The returned list is an unmodifiable copy and is never
     * {@code null}.
     *
     * @param context the immutable tooltip snapshot, never {@code null}
     * @return the collected plain-text lines, never {@code null}
     * @throws ClientUnavailableException when no client backend is available
     */
    @net.weaver.api.annotation.Internal
    public static synchronized List<String> tooltipLines(TooltipContext context)
    {
        Objects.requireNonNull(context, "context");
        final List<String> lines = requireBackend().tooltipLines(context);
        if (lines == null || lines.isEmpty())
        {
            return List.of();
        }
        return List.copyOf(lines);
    }

    /**
     * Counts facade registrations that are currently active.
     *
     * <p>Only registrations created through this facade are counted; direct
     * calls on a {@link ClientBackend} are not tracked. Inactive handles are
     * pruned from the internal set. Returns {@code 0} when no backend is
     * active.
     *
     * @return the number of active facade registrations
     */
    public static synchronized int registrationCount()
    {
        if (backend == null)
        {
            return 0;
        }
        int count = 0;
        final Iterator<ClientRegistration> iterator = REGISTRATIONS.iterator();
        while (iterator.hasNext())
        {
            final ClientRegistration registration = iterator.next();
            boolean active;
            try
            {
                active = registration.isActive();
            }
            catch (RuntimeException ignored)
            {
                active = false;
            }
            if (active)
            {
                count++;
            }
            else
            {
                iterator.remove();
            }
        }
        return count;
    }

    /**
     * Uninstalls the active backend and clears all cached resolution state.
     *
     * <p>Intended for tests and loader shutdown. The next backend-needing call
     * resolves or installs a fresh backend. This method is fail-soft and may be
     * called from any thread.
     */
    public static synchronized void reset()
    {
        final ClientBackend current = backend;
        backend = null;
        REGISTRATIONS.clear();
        if (current != null)
        {
            uninstallQuietly(current);
        }
    }

    private static ClientRegistration track(ClientRegistration registration)
    {
        Objects.requireNonNull(registration, "registration");
        REGISTRATIONS.add(registration);
        return registration;
    }

    private static ClientBackend requireBackend()
    {
        final ClientBackend current = backend;
        if (current != null)
        {
            return current;
        }
        final ClientBackend resolved = resolveBackend();
        backend = resolved;
        return resolved;
    }

    private static ClientBackend resolveBackend()
    {
        final ClassLoader loader = ClientRegistry.class.getClassLoader();
        try
        {
            Class.forName(MINECRAFT_CLIENT_CLASS, false, loader);
        }
        catch (ClassNotFoundException | LinkageError | RuntimeException absent)
        {
            throw new ClientUnavailableException("client API is not available on this side: "
                    + MINECRAFT_CLIENT_CLASS + " is not present", absent);
        }
        try
        {
            final Class<?> backendClass = Class.forName(CORE_BACKEND_CLASS, true, loader);
            final Method instanceMethod = backendClass.getMethod("instance");
            final Object instance = instanceMethod.invoke(null);
            if (!(instance instanceof ClientBackend resolved))
            {
                throw new ClientUnavailableException(CORE_BACKEND_CLASS
                        + " does not provide a ClientBackend instance");
            }
            return resolved;
        }
        catch (ClientUnavailableException failure)
        {
            throw failure;
        }
        catch (InvocationTargetException failure)
        {
            final Throwable cause = failure.getCause();
            throw new ClientUnavailableException("failed to resolve client backend "
                    + CORE_BACKEND_CLASS, cause == null ? failure : cause);
        }
        catch (ReflectiveOperationException | LinkageError | RuntimeException failure)
        {
            throw new ClientUnavailableException("failed to resolve client backend "
                    + CORE_BACKEND_CLASS, failure);
        }
    }

    private static void uninstallQuietly(ClientBackend target)
    {
        try
        {
            target.uninstall();
        }
        catch (Throwable failure)
        {
            System.err.println("[weaver] CLIENT BACKEND UNINSTALL FAILED backend="
                    + target.getClass().getName() + " reason=" + failure);
        }
    }
}
