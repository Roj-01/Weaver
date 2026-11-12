package net.weaver.core.network;

import java.util.List;

/**
 * Install state and legacy target manifest for the Minecraft 26.3 network
 * integration.
 *
 * <p>The working bridge lives in {@code api-game} under
 * {@code net.weaver.api.game.network}. It registers a real
 * {@code weaver:bridge} payload codec during packet class initialisation, so
 * the 26.3 decoder no longer discards Weaver channel bytes. This class records
 * whether the loader armed the bridge mixin configuration and whether the
 * expected 26.3 classes were present, and keeps the historical listener
 * descriptors as metadata.</p>
 *
 * <p>{@link #targets()} returns the verified listener descriptors. They are
 * useful for audits and diagnostics, but the bridge itself is driven by the
 * mixin configuration and no longer depends on listener-level observation of
 * unknown payloads.</p>
 */
public final class NetworkFeature
{
    private static final String DEFAULT_BLOCKER =
            "Minecraft 26.3 discards unknown custom payload bytes before listener "
                    + "dispatch: DiscardedPayload keeps only the payload id, so listener hooks "
                    + "alone cannot deliver arbitrary Weaver channels. Codec registration or a "
                    + "pre-decode hook is required.";

    private static final String MIXINS_BLOCKER =
            "network bridge is not installed: weaver.network.mixins.json was not armed by "
                    + "the loader before the Mixin transformer was installed";

    private static final String GAME_CLASSES_BLOCKER =
            "network bridge is not installed: Minecraft 26.3 custom payload classes are "
                    + "not present on the game class path";

    private static final List<Target> TARGETS = List.of(
            new Target("CLIENT",
                    "net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl",
                    "handleCustomPayload",
                    "(Lnet/minecraft/network/protocol/common/ClientboundCustomPayloadPacket;)V",
                    "Observe clientbound custom payloads routed to the common listener."),
            new Target("SERVER",
                    "net.minecraft.server.network.ServerCommonPacketListenerImpl",
                    "handleCustomPayload",
                    "(Lnet/minecraft/network/protocol/common/ServerboundCustomPayloadPacket;)V",
                    "Observe serverbound custom payloads routed to the common listener."),
            new Target("SERVER",
                    "net.minecraft.server.network.ServerGamePacketListenerImpl",
                    "handleCustomPayload",
                    "(Lnet/minecraft/network/protocol/common/ServerboundCustomPayloadPacket;)V",
                    "Observe serverbound custom payloads routed to the game listener."),
            new Target("CLIENT",
                    "net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl",
                    "onDisconnect",
                    "(Lnet/minecraft/network/DisconnectionDetails;)V",
                    "Observe the client's disconnect notification."),
            new Target("SERVER",
                    "net.minecraft.server.network.ServerGamePacketListenerImpl",
                    "onDisconnect",
                    "(Lnet/minecraft/network/DisconnectionDetails;)V",
                    "Observe the server's per-connection disconnect notification."));

    private static volatile boolean installed;
    private static volatile boolean mixinsArmed;
    private static volatile boolean gameClassesPresent;
    private static volatile String blocker = DEFAULT_BLOCKER;

    private NetworkFeature()
    {
        throw new AssertionError("No NetworkFeature instances");
    }

    /**
     * One verified Minecraft hook target.
     *
     * @param side       the logical side as {@code CLIENT} or {@code SERVER}
     * @param owner      the binary class name that declares the method
     * @param method     the method name
     * @param descriptor the JVM method descriptor
     * @param purpose    a human-readable description of what the target
     *                   observes
     */
    public record Target(String side, String owner, String method, String descriptor,
                         String purpose)
    {
    }

    /**
     * Returns the verified Minecraft 26.3 hook targets.
     *
     * @return an immutable list of targets, never {@code null}
     */
    public static List<Target> targets()
    {
        return TARGETS;
    }

    /**
     * Explains the current install blocker, or returns an empty string.
     *
     * <p>The default text explains why listener-level custom payload hooks are
     * insufficient on their own: Minecraft 26.3 discards unknown custom payload
     * bytes before listener dispatch, and a {@code DiscardedPayload} keeps only
     * the payload id. After a failed install the text names the missing
     * prerequisite (mixin configuration or game classes). When the bridge is
     * installed the blocker is empty.</p>
     *
     * @return the blocker description, never {@code null}
     */
    public static String blocker()
    {
        return blocker;
    }

    /**
     * Records whether the Minecraft bridge was installed.
     *
     * <p>The bridge reports two facts: whether the loader armed
     * {@code weaver.network.mixins.json} before the Mixin transformer, and
     * whether the 26.3 custom-payload classes are present on the game class
     * path. Both are required. When the bridge is installed, {@link #blocker()}
     * returns an empty string; otherwise it explains the failing prerequisite.</p>
     *
     * @param mixinsArmed       whether the network mixin configuration was
     *                          armed
     * @param gameClassesPresent whether the 26.3 game classes were present
     * @return {@code true} when the bridge is installed
     */
    public static synchronized boolean install(boolean mixinsArmed,
                                               boolean gameClassesPresent)
    {
        NetworkFeature.mixinsArmed = mixinsArmed;
        NetworkFeature.gameClassesPresent = gameClassesPresent;
        boolean active = mixinsArmed && gameClassesPresent;
        installed = active;
        if (active)
        {
            blocker = "";
        }
        else if (!mixinsArmed)
        {
            blocker = MIXINS_BLOCKER;
        }
        else
        {
            blocker = GAME_CLASSES_BLOCKER;
        }
        return active;
    }

    /**
     * Clears the installed state and restores the default blocker.
     *
     * <p>This is used by tests and by an orderly shutdown. It does not unwire a
     * Mixin that was already applied.</p>
     */
    public static synchronized void uninstall()
    {
        installed = false;
        mixinsArmed = false;
        gameClassesPresent = false;
        blocker = DEFAULT_BLOCKER;
    }

    /**
     * Returns whether the Minecraft bridge is installed.
     *
     * @return {@code true} when the mixin configuration was armed and the game
     *         classes were present
     */
    public static boolean isInstalled()
    {
        return installed;
    }

    /**
     * Returns whether the network mixin configuration was armed.
     *
     * @return {@code true} when the loader added the config before the
     *         transformer
     */
    public static boolean mixinsArmed()
    {
        return mixinsArmed;
    }

    /**
     * Returns whether the 26.3 custom-payload classes were present.
     *
     * @return {@code true} when the class-loader resource probe succeeded
     */
    public static boolean gameClassesPresent()
    {
        return gameClassesPresent;
    }
}
