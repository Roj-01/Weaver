package net.weaver.nf;

import net.minecraft.server.MinecraftServer;
import net.weaver.api.ModContext;
import net.weaver.api.event.EventBus;
import net.weaver.api.game.event.ServerStartingEvent;
import net.weaver.api.game.event.ServerStoppedEvent;

/** The running server, as NeoForge's {@code ServerLifecycleHooks.getCurrentServer()}. */
public final class Servers {
    private static volatile MinecraftServer server;

    static {
        EventBus.GAME.addListener(ServerStartingEvent.class, event -> server = event.server());
        EventBus.GAME.addListener(ServerStoppedEvent.class, event -> server = null);
    }

    private Servers() {
    }

    /** Kept for mods that call it from {@code onInitialize}; the server is tracked without it. */
    public static void init(ModContext context) {
    }

    /** The running server (dedicated or integrated), or null when none runs. */
    public static MinecraftServer get() {
        return server;
    }
}
