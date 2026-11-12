package net.weaver.clientproof;

import java.util.concurrent.atomic.AtomicBoolean;

final class ProofState
{
    private static final AtomicBoolean COMMAND_ALLOWED = new AtomicBoolean();
    private static final AtomicBoolean COMMAND_DENIED = new AtomicBoolean();
    private static final AtomicBoolean COMMAND_ALIAS = new AtomicBoolean();
    private static final AtomicBoolean COMMAND_TREE = new AtomicBoolean();
    private static final AtomicBoolean KEYBIND = new AtomicBoolean();
    private static final AtomicBoolean SCREEN = new AtomicBoolean();
    private static final AtomicBoolean HUD = new AtomicBoolean();
    private static final AtomicBoolean HUD_STAGE = new AtomicBoolean();
    private static final AtomicBoolean SCREEN_PHASE = new AtomicBoolean();
    private static final AtomicBoolean HAND_RENDER = new AtomicBoolean();
    private static final AtomicBoolean FOG = new AtomicBoolean();
    private static final AtomicBoolean BLOCK_COLOR = new AtomicBoolean();
    private static final AtomicBoolean TOOLTIP = new AtomicBoolean();
    private static final AtomicBoolean NETWORK = new AtomicBoolean();
    private static final AtomicBoolean CAPABILITY_SYNC = new AtomicBoolean();
    private static final AtomicBoolean GAME_EVENTS = new AtomicBoolean();
    private static final AtomicBoolean CLIENT_RESULT = new AtomicBoolean();
    private static final AtomicBoolean SERVER_RESULT = new AtomicBoolean();
    private static volatile boolean physicalServer;
    private static volatile long clientDeadline;

    private ProofState()
    {
    }

    static void physicalServer(boolean server)
    {
        physicalServer = server;
    }

    static boolean physicalServer()
    {
        return physicalServer;
    }

    static void clientDeadline(long deadline)
    {
        clientDeadline = deadline;
    }

    static long clientDeadline()
    {
        return clientDeadline;
    }

    static void commandAllowed()
    {
        COMMAND_ALLOWED.set(true);
    }

    static void commandDenied()
    {
        COMMAND_DENIED.set(true);
    }

    static void commandAlias()
    {
        COMMAND_ALIAS.set(true);
    }

    static boolean commandsOk()
    {
        return COMMAND_ALLOWED.get() && COMMAND_DENIED.get() && COMMAND_ALIAS.get();
    }

    static boolean markCommandTree()
    {
        return COMMAND_TREE.compareAndSet(false, true);
    }

    static boolean commandTree()
    {
        return COMMAND_TREE.get();
    }

    static boolean markKeybind()
    {
        return KEYBIND.compareAndSet(false, true);
    }

    static boolean markScreen()
    {
        return SCREEN.compareAndSet(false, true);
    }

    static boolean markHud()
    {
        return HUD.compareAndSet(false, true);
    }

    static boolean markHudStage()
    {
        return HUD_STAGE.compareAndSet(false, true);
    }

    static boolean markScreenPhase()
    {
        return SCREEN_PHASE.compareAndSet(false, true);
    }

    static boolean markHandRender()
    {
        return HAND_RENDER.compareAndSet(false, true);
    }

    static boolean markFog()
    {
        return FOG.compareAndSet(false, true);
    }

    static boolean markBlockColor()
    {
        return BLOCK_COLOR.compareAndSet(false, true);
    }

    static boolean markTooltip()
    {
        return TOOLTIP.compareAndSet(false, true);
    }

    static boolean markNetwork()
    {
        return NETWORK.compareAndSet(false, true);
    }

    static boolean markCapabilitySync()
    {
        return CAPABILITY_SYNC.compareAndSet(false, true);
    }

    static void markGameEvents()
    {
        GAME_EVENTS.set(true);
    }

    static boolean screenShown()
    {
        return SCREEN.get();
    }

    static boolean hud()
    {
        return HUD.get();
    }

    static boolean clientOk()
    {
        return commandsOk() && COMMAND_TREE.get() && KEYBIND.get() && SCREEN.get() && HUD.get()
                && TOOLTIP.get() && NETWORK.get() && CAPABILITY_SYNC.get() && GAME_EVENTS.get();
    }

    static boolean claimClientResult()
    {
        return CLIENT_RESULT.compareAndSet(false, true);
    }

    static boolean claimServerResult()
    {
        return SERVER_RESULT.compareAndSet(false, true);
    }

    static String clientMissing()
    {
        StringBuilder missing = new StringBuilder();
        appendMissing(missing, COMMAND_ALLOWED.get(), "command-allowed");
        appendMissing(missing, COMMAND_DENIED.get(), "command-denied");
        appendMissing(missing, COMMAND_ALIAS.get(), "command-alias");
        appendMissing(missing, COMMAND_TREE.get(), "command-tree");
        appendMissing(missing, KEYBIND.get(), "keybind");
        appendMissing(missing, SCREEN.get(), "screen");
        appendMissing(missing, HUD.get(), "hud");
        appendMissing(missing, TOOLTIP.get(), "tooltip");
        appendMissing(missing, NETWORK.get(), "network");
        appendMissing(missing, CAPABILITY_SYNC.get(), "capability-sync");
        appendMissing(missing, GAME_EVENTS.get(), "game-events(" + ClientGameEventsProof.missing() + ")");
        return missing.length() == 0 ? "none" : missing.toString();
    }

    private static void appendMissing(StringBuilder builder, boolean present, String name)
    {
        if (present)
        {
            return;
        }
        if (builder.length() > 0)
        {
            builder.append(',');
        }
        builder.append(name);
    }
}
