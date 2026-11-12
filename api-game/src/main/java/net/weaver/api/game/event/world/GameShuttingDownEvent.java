package net.weaver.api.game.event.world;

import net.weaver.api.event.Event;

/**
 * Fired once when the game process is about to shut down: the client is
 * closing ({@code Minecraft#stop}), or a dedicated server is stopping
 * ({@code DedicatedServer#stopServer}). Save anything that must survive the
 * process. On the client, an integrated server may still be running.
 */
public final class GameShuttingDownEvent extends Event {
}
