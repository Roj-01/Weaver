package net.weaver.core.network;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;
import net.weaver.api.network.NetworkModInfo;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Loader-side helper that arms and activates the optional game bridge.
 *
 * <p>The bridge lives in {@code api-game}, which the core module does not depend
 * on. This class therefore stays game-free and uses the bridge's public static
 * API reflectively. Integration is two calls:</p>
 *
 * <ol>
 *   <li>{@link #arm(ClassLoader, BiConsumer, Consumer)} reads
 *       {@code weaver.network.mixins.json} and hands it to the Mixin engine.
 *       It must run after {@code engine.boot} and before the Weaver mixin
 *       transformer is installed.</li>
 *   <li>{@link #activate(ClassLoader, boolean, Consumer)} runs after the
 *       transformer is installed and before game main. It loads
 *       {@code GameNetworkBridge}, which may reference packet classes, and
 *       records the installed state in {@link NetworkFeature}.</li>
 * </ol>
 *
 * <p>Both calls are failure-isolated: a missing {@code api-game} artifact or a
 * missing game class path logs one diagnostic and leaves {@code NetworkFeature}
 * uninstalled. Boot never fails because of the bridge.</p>
 */
public final class NetworkBridgeInstall
{
    /**
     * The bridge mixin resource owned by {@code api-game}.
     */
    public static final String NETWORK_MIXIN_CONFIG = "weaver.network.mixins.json";

    private static final String BRIDGE_CLASS = "net.weaver.api.game.network.GameNetworkBridge";

    private NetworkBridgeInstall()
    {
        throw new AssertionError("No NetworkBridgeInstall instances");
    }

    /**
     * Reads the bridge mixin configuration and passes it to the loader sink.
     *
     * <p>The resource is read with this class's loader, matching the existing
     * client feature installation. The sink is expected to register the bytes
     * with the Mixin engine under a stable mod id; it must not load any game
     * class.</p>
     *
     * @param gameLoader the game class loader, possibly {@code null}
     * @param configSink a sink that registers the resource name and bytes
     * @param log        the diagnostic sink
     * @return {@code true} when the resource was found and handed to the sink
     * @throws NullPointerException when {@code configSink} or {@code log} is
     *                              {@code null}
     */
    public static boolean arm(ClassLoader gameLoader, BiConsumer<String, byte[]> configSink,
                              Consumer<String> log)
    {
        Objects.requireNonNull(configSink, "configSink");
        Objects.requireNonNull(log, "log");
        InputStream opened = NetworkBridgeInstall.class.getClassLoader()
                .getResourceAsStream(NETWORK_MIXIN_CONFIG);
        if (opened == null && gameLoader != null)
        {
            opened = gameLoader.getResourceAsStream(NETWORK_MIXIN_CONFIG);
        }
        try (InputStream input = opened)
        {
            if (input == null)
            {
                log.accept("[weaver] NETWORK BRIDGE mixin config missing resource="
                        + NETWORK_MIXIN_CONFIG);
                return false;
            }
            byte[] bytes = input.readAllBytes();
            configSink.accept(NETWORK_MIXIN_CONFIG, bytes);
            log.accept("[weaver] NETWORK BRIDGE mixin config armed resource="
                    + NETWORK_MIXIN_CONFIG + " bytes=" + bytes.length);
            return true;
        }
        catch (Throwable failure)
        {
            log.accept("[weaver] NETWORK BRIDGE mixin config FAILED resource="
                    + NETWORK_MIXIN_CONFIG + " reason=" + failure);
            return false;
        }
    }

    /**
     * Loads and activates the bridge through its public API.
     *
     * <p>The bridge class is loaded with {@code initialize = true}. Call this
     * only after the Weaver mixin transformer is installed, so any packet class
     * that class verification touches is still transformable.</p>
     *
     * @param gameLoader the loader that sees {@code api-game} and Minecraft
     * @param armed      whether {@link #arm} succeeded
     * @param log        the diagnostic sink
     * @return {@code true} when the bridge reports that it is installed
     * @throws NullPointerException when {@code log} is {@code null}
     */
    public static boolean activate(ClassLoader gameLoader, boolean armed, Consumer<String> log)
    {
        return activate(gameLoader, armed, List.of(), log);
    }

    /**
     * Loads and activates the bridge, and advertises {@code mods} in its
     * handshake.
     *
     * <p>The handshake requires every advertised server mod on the client at
     * the same version, so only mods that must be present on both sides belong
     * here (metadata {@code side} {@code both}). An empty list advertises
     * nothing and leaves the bridge's current list unchanged.</p>
     *
     * @param gameLoader the loader that sees {@code api-game} and Minecraft
     * @param armed      whether {@link #arm} succeeded
     * @param mods       the mods to advertise, not {@code null}
     * @param log        the diagnostic sink
     * @return {@code true} when the bridge reports that it is installed
     */
    public static boolean activate(ClassLoader gameLoader, boolean armed, List<NetworkModInfo> mods,
                                   Consumer<String> log)
    {
        Objects.requireNonNull(mods, "mods");
        boolean installed = activateBridge(gameLoader, armed, log);
        if (installed && !mods.isEmpty())
        {
            try
            {
                Class<?> bridge = Class.forName(BRIDGE_CLASS, true, gameLoader);
                bridge.getMethod("setLocalMods", List.class).invoke(null, List.copyOf(mods));
                log.accept("[weaver] NETWORK BRIDGE mods advertised=" + mods.size());
            }
            catch (Throwable failure)
            {
                Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                log.accept("[weaver] NETWORK BRIDGE mods FAILED reason=" + cause);
            }
        }
        return installed;
    }

    private static boolean activateBridge(ClassLoader gameLoader, boolean armed, Consumer<String> log)
    {
        Objects.requireNonNull(log, "log");
        net.weaver.core.BootPhaseOrder.requireTransformersInstalled(
                "NetworkBridgeInstall.activate");
        if (gameLoader == null)
        {
            log.accept("[weaver] NETWORK BRIDGE activation skipped reason=no-game-loader");
            NetworkFeature.install(false, false);
            return false;
        }
        boolean installed = false;
        try
        {
            Class<?> bridge = Class.forName(BRIDGE_CLASS, true, gameLoader);
            Method install = bridge.getMethod("install", ClassLoader.class, boolean.class);
            Object result = install.invoke(null, gameLoader, armed);
            installed = result instanceof Boolean value && value;
            log.accept("[weaver] NETWORK BRIDGE activation installed=" + installed
                    + " armed=" + armed);
        }
        catch (ClassNotFoundException | LinkageError missing)
        {
            log.accept("[weaver] NETWORK BRIDGE unavailable reason=" + missing);
        }
        catch (Throwable failure)
        {
            Throwable cause = failure.getCause() == null ? failure : failure.getCause();
            log.accept("[weaver] NETWORK BRIDGE activation FAILED reason=" + cause);
        }
        NetworkFeature.install(armed, installed);
        return installed;
    }
}
