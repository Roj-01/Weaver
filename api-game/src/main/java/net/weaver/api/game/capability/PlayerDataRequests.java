package net.weaver.api.game.capability;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiPredicate;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.weaver.api.capability.AttachmentRegistry;
import net.weaver.api.capability.AttachmentType;
import net.weaver.api.event.EventBus;
import net.weaver.api.event.events.network.NetworkConnectedEvent;
import net.weaver.api.event.events.network.NetworkDisconnectedEvent;
import net.weaver.api.game.event.ServerStartingEvent;
import net.weaver.api.game.event.ServerStoppedEvent;
import net.weaver.api.network.NetworkConnection;
import net.weaver.api.network.NetworkPhase;
import net.weaver.api.network.NetworkRegistry;
import net.weaver.api.network.NetworkSide;
import net.weaver.api.network.PayloadBuffer;
import net.weaver.api.network.PayloadCodec;
import net.weaver.api.network.PayloadType;

/**
 * Lets a client ask the server to change a capability value on its own player,
 * for choices made in the client's interface (a selected mode, a toggled
 * overlay, a chosen slot). The server is the authority: it decodes the value with
 * the capability's codec, runs the mod's check with the player and the value,
 * and sets it on the server player only when the check passes. The capability's
 * own sync then brings the value back to the client.
 *
 * <pre>{@code
 * // Both sides, during onInitialize:
 * PlayerDataRequests.allow(MODE, (player, mode) -> mode >= 0 && mode < 4);
 * // Client, from a button:
 * PlayerDataRequests.request(MODE, 2);
 * }</pre>
 *
 * <p>Only capabilities registered with {@link #allow} on the server are changed;
 * a request for any other id, a payload that does not decode, or a value the
 * check rejects is dropped and logged once per capability. There is no generic
 * client write access to other holders: a mod that needs that sends its own
 * payload and checks it on the server.</p>
 */
public final class PlayerDataRequests {
    private record Request(String capability, byte[] value) {
    }

    private record Rule<T>(AttachmentType<T> capability, BiPredicate<ServerPlayer, T> check) {
        boolean apply(ServerPlayer player, byte[] encoded) {
            T value = capability.codec().decode(encoded);
            if (value == null || !capability.valueType().isInstance(value) || !check.test(player, value)) {
                return false;
            }
            AttachmentRegistry.global().set(player, capability, value);
            return true;
        }
    }

    private static final int MAX_VALUE_BYTES = 32 * 1024;
    private static final PayloadType<Request> TYPE = PayloadType.of("weaver", "player_data_request",
            new PayloadCodec<>() {
                @Override
                public void encode(Request request, PayloadBuffer out) {
                    out.writeString(request.capability(), 256);
                    out.writeByteArray(request.value(), MAX_VALUE_BYTES);
                }

                @Override
                public Request decode(PayloadBuffer in) {
                    return new Request(in.readString(256), in.readByteArray(MAX_VALUE_BYTES));
                }
            });
    private static final Map<String, Rule<?>> RULES = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> REPORTED = new ConcurrentHashMap<>();
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static volatile MinecraftServer server;
    private static volatile NetworkConnection clientConnection;

    private PlayerDataRequests() {
    }

    /**
     * Allows clients to request {@code capability} on their own player. Call on both
     * sides during initialization, before connections open (the channel is
     * advertised in the join handshake).
     *
     * @param capability a capability with a codec
     * @param check      the server-side check of the player and the requested value
     */
    public static <T> void allow(AttachmentType<T> capability, BiPredicate<ServerPlayer, T> check) {
        Objects.requireNonNull(capability, "capability");
        Objects.requireNonNull(check, "check");
        if (capability.codec() == null) {
            throw new IllegalArgumentException("capability " + capability.id() + " has no codec");
        }
        install();
        RULES.put(capability.id(), new Rule<>(capability, check));
    }

    /**
     * Asks the server to set {@code value} on this client's player. Returns false
     * when not connected or when the value does not encode; the server's answer is
     * the synced value itself.
     */
    public static <T> boolean request(AttachmentType<T> capability, T value) {
        install();
        NetworkConnection connection = clientConnection;
        if (connection == null || capability.codec() == null || value == null) {
            return false;
        }
        try {
            connection.send(TYPE, new Request(capability.id(), capability.codec().encode(value)));
            return true;
        } catch (RuntimeException failure) {
            report(capability.id(), "send failed: " + failure);
            return false;
        }
    }

    /** Whether {@code capability} may be requested by clients. */
    public static boolean allowed(AttachmentType<?> capability) {
        return RULES.containsKey(capability.id());
    }

    private static void install() {
        if (!INSTALLED.compareAndSet(false, true)) {
            return;
        }
        NetworkRegistry.global().registerServerbound("weaver", TYPE, NetworkPhase.PLAY, (request, context) -> {
            MinecraftServer current = server;
            if (current == null || context.player() == null) {
                return;
            }
            current.execute(() -> handle(current, context.player().id(), request));
        });
        EventBus.GAME.addListener(ServerStartingEvent.class, event -> server = event.server());
        EventBus.GAME.addListener(ServerStoppedEvent.class, event -> server = null);
        EventBus.GAME.addListener(NetworkConnectedEvent.class, event -> {
            if (event.side() == NetworkSide.CLIENT) {
                clientConnection = event.connection();
            }
        });
        EventBus.GAME.addListener(NetworkDisconnectedEvent.class, event -> {
            if (event.connection() == clientConnection) {
                clientConnection = null;
            }
        });
    }

    private static void handle(MinecraftServer current, java.util.UUID playerId, Request request) {
        Rule<?> rule = RULES.get(request.capability());
        if (rule == null) {
            // The id comes from the client: never a key of its own (unbounded) nor logged raw.
            report("<not allowed>", "not allowed: " + printable(request.capability()));
            return;
        }
        ServerPlayer player = current.getPlayerList().getPlayer(playerId);
        if (player == null) {
            return;
        }
        try {
            if (!rule.apply(player, request.value())) {
                report(request.capability(), "rejected by the check");
            }
        } catch (RuntimeException failure) {
            report(request.capability(), "invalid value: " + failure);
        }
    }

    private static String printable(String text) {
        String cut = text.length() > 64 ? text.substring(0, 64) + "..." : text;
        StringBuilder out = new StringBuilder(cut.length());
        for (int i = 0; i < cut.length(); i++) {
            char c = cut.charAt(i);
            out.append(c >= 0x20 && c < 0x7F ? c : '?');
        }
        return out.toString();
    }

    private static void report(String capability, String reason) {
        if (REPORTED.putIfAbsent(capability + "|" + reason.split(":", 2)[0], Boolean.TRUE) == null) {
            System.out.println("[weaver] PLAYER DATA REQUEST DROPPED capability=" + capability + " reason=" + reason);
        }
    }
}
