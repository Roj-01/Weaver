package net.weaver.api.game.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.world.entity.Entity;
import net.weaver.api.game.event.mixin.ChunkMapAccessor;
import net.weaver.api.game.event.mixin.TrackedEntityAccessor;
import net.weaver.api.network.NetworkConnection;
import net.weaver.api.network.PayloadType;

/**
 * Sends a payload to the players that see an entity: the players the server is
 * tracking it for (within their view, in the same level), as vanilla does for the
 * entity's own packets. Use it for animations, effects, and state of one entity
 * (a machine's rotation, a boss phase) instead of sending to everyone.
 *
 * <pre>{@code
 * EntityTracking.sendToTracking(boss, PHASE, new Phase(boss.getId(), 2));
 * EntityTracking.sendToTrackingAndSelf(player, MANA, player.getMana()); // also the player itself
 * }</pre>
 *
 * <p>Server thread. Players without Weaver's connection (vanilla clients) are skipped.</p>
 */
public final class EntityTracking {
    private EntityTracking() {
    }

    /** The players the server tracks {@code entity} for (never the entity itself). */
    public static List<ServerPlayer> trackingPlayers(Entity entity) {
        List<ServerPlayer> out = new ArrayList<>();
        if (!(entity.level() instanceof ServerLevel level)) {
            return out;
        }
        Object tracked = ((ChunkMapAccessor) level.getChunkSource().chunkMap).weaver$entityMap().get(entity.getId());
        if (tracked == null) {
            return out;
        }
        for (ServerPlayerConnection connection : List.copyOf(((TrackedEntityAccessor) tracked).weaver$seenBy())) {
            out.add(connection.getPlayer());
        }
        return out;
    }

    /** Sends to every player that sees {@code entity}; returns how many were sent to. */
    public static <T> int sendToTracking(Entity entity, PayloadType<T> type, T payload) {
        return send(trackingPlayers(entity), type, payload);
    }

    /** As {@link #sendToTracking}, plus the entity itself when it is a player. */
    public static <T> int sendToTrackingAndSelf(Entity entity, PayloadType<T> type, T payload) {
        List<ServerPlayer> players = trackingPlayers(entity);
        if (entity instanceof ServerPlayer self && !players.contains(self)) {
            players.add(self);
        }
        return send(players, type, payload);
    }

    /** Sends to one player; false when the player has no Weaver connection. */
    public static <T> boolean sendTo(ServerPlayer player, PayloadType<T> type, T payload) {
        NetworkConnection connection = connectionOf(player.getUUID());
        if (connection == null) {
            return false;
        }
        connection.send(type, payload);
        return true;
    }

    /** The Weaver connection of the player with {@code id}, or null. */
    public static NetworkConnection connectionOf(UUID id) {
        for (NetworkConnection connection : GameNetworkBridge.connections()) {
            if (connection instanceof BridgeSession session && session.player() != null
                    && id.equals(session.player().id())) {
                return connection;
            }
        }
        return null;
    }

    private static <T> int send(List<ServerPlayer> players, PayloadType<T> type, T payload) {
        if (players.isEmpty()) {
            return 0;
        }
        java.util.Map<UUID, NetworkConnection> byPlayer = new java.util.HashMap<>();
        for (NetworkConnection connection : GameNetworkBridge.connections()) {
            if (connection instanceof BridgeSession session && session.player() != null) {
                byPlayer.put(session.player().id(), connection);
            }
        }
        int sent = 0;
        for (ServerPlayer player : players) {
            NetworkConnection connection = byPlayer.get(player.getUUID());
            if (connection != null) {
                connection.send(type, payload);
                sent++;
            }
        }
        return sent;
    }
}
