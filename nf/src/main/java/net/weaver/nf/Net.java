package net.weaver.nf;

import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.weaver.api.ModContext;
import net.weaver.api.game.network.EntityTracking;
import net.weaver.api.game.network.GameNetworkBridge;
import net.weaver.api.network.NetworkConnection;
import net.weaver.api.network.NetworkPhase;
import net.weaver.api.network.NetworkProtocol;
import net.weaver.api.network.NetworkRegistry;
import net.weaver.api.network.NetworkSide;
import net.weaver.api.network.PayloadBuffer;
import net.weaver.api.network.PayloadCodec;
import net.weaver.api.network.PayloadContext;
import net.weaver.api.network.PayloadType;

/**
 * Play-phase payloads with vanilla {@link CustomPacketPayload} types and
 * {@link StreamCodec}s, as NeoForge's payload registrar and
 * {@code PacketDistributor}, carried by Weaver's networking.
 *
 * <p>A payload belongs to the mod named by its type's namespace. Handlers run on
 * the network thread; {@link Context#enqueueWork} moves work to the game
 * thread.</p>
 */
public final class Net {
    /** The client's registry access; set by Weaver NF on the client, or by a mod. */
    public static volatile Supplier<RegistryAccess> clientAccess;
    /** The client's player; set by Weaver NF on the client, or by a mod. */
    public static volatile Supplier<Player> clientPlayer;
    /** The client's connection to the server; Weaver NF finds it when this is not set. */
    public static volatile Supplier<NetworkConnection> clientConnection;

    private static final Map<CustomPacketPayload.Type<?>, PayloadType<?>> TYPES = new ConcurrentHashMap<>();

    /** What a handler gets with a payload. */
    public interface Context {
        /** The sending player on the server; the local player on the client. */
        Player player();

        /** Runs {@code work} on the game thread of the side handling the payload. */
        default void enqueueWork(Runnable work) {
            work.run();
        }

        /** {@code SERVERBOUND} on the server, {@code CLIENTBOUND} on the client. */
        default PacketFlow flow() {
            return PacketFlow.CLIENTBOUND;
        }
    }

    private Net() {
    }

    /** Kept for mods that call it from {@code onInitialize}; payloads need no setup. */
    public static void init(ModContext context) {
    }

    /** The running server, or null. */
    public static MinecraftServer server() {
        return Servers.get();
    }

    public static <T extends CustomPacketPayload> void playToServer(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, BiConsumer<T, Context> handler) {
        PayloadType<T> payloadType = type(type, codec);
        NetworkRegistry.global().registerServerbound(payloadType.modId(), payloadType, NetworkPhase.PLAY,
                (payload, context) -> handler.accept(payload, context(context)));
    }

    public static <T extends CustomPacketPayload> void playToClient(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, BiConsumer<T, Context> handler) {
        PayloadType<T> payloadType = type(type, codec);
        NetworkRegistry.global().registerClientbound(payloadType.modId(), payloadType, NetworkPhase.PLAY,
                (payload, context) -> handler.accept(payload, context(context)));
    }

    public static <T extends CustomPacketPayload> void playBidirectional(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, BiConsumer<T, Context> handler) {
        PayloadType<T> payloadType = type(type, codec);
        NetworkRegistry.global().registerServerbound(payloadType.modId(), payloadType, NetworkPhase.PLAY,
                (payload, context) -> handler.accept(payload, context(context)));
        NetworkRegistry.global().registerClientbound(payloadType.modId(), payloadType, NetworkPhase.PLAY,
                (payload, context) -> handler.accept(payload, context(context)));
    }

    /** Sends {@code payload} from the client to the server. */
    public static void sendToServer(CustomPacketPayload payload) {
        NetworkConnection connection = clientConnection();
        if (connection != null) {
            send(connection, payload);
        }
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        sendTo(player, payload);
    }

    public static void sendToPlayers(List<? extends ServerPlayer> players, CustomPacketPayload payload) {
        for (ServerPlayer player : players) {
            sendTo(player, payload);
        }
    }

    public static void sendToAllPlayers(CustomPacketPayload payload) {
        MinecraftServer server = Servers.get();
        if (server != null) {
            sendToPlayers(server.getPlayerList().getPlayers(), payload);
        }
    }

    public static void sendToPlayersInDimension(ServerLevel level, CustomPacketPayload payload) {
        sendToPlayers(level.players(), payload);
    }

    /** Every player within {@code radius} of the point, except {@code excluded} (may be null). */
    public static void sendToPlayersNear(ServerLevel level, ServerPlayer excluded, double x, double y, double z,
            double radius, CustomPacketPayload payload) {
        double squared = radius * radius;
        for (ServerPlayer player : level.players()) {
            if (player != excluded && player.distanceToSqr(x, y, z) <= squared) {
                sendTo(player, payload);
            }
        }
    }

    public static void sendToPlayersTrackingEntity(Entity entity, CustomPacketPayload payload) {
        for (ServerPlayer player : EntityTracking.trackingPlayers(entity)) {
            sendTo(player, payload);
        }
    }

    public static void sendToPlayersTrackingEntityAndSelf(Entity entity, CustomPacketPayload payload) {
        sendToPlayersTrackingEntity(entity, payload);
        if (entity instanceof ServerPlayer self) {
            sendTo(self, payload);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends CustomPacketPayload> PayloadType<T> type(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        return (PayloadType<T>) TYPES.computeIfAbsent(type, ignored -> PayloadType.of(type.id().getNamespace(),
                type.id().getPath(), new StreamPayloadCodec<T>(codec)));
    }

    @SuppressWarnings("unchecked")
    private static <T extends CustomPacketPayload> void send(NetworkConnection connection, T payload) {
        PayloadType<T> type = (PayloadType<T>) TYPES.get(payload.type());
        if (type == null) {
            throw new IllegalStateException("payload type " + payload.type().id() + " is not registered with Net");
        }
        if (connection.isOpen()) {
            connection.send(type, payload);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends CustomPacketPayload> void sendTo(ServerPlayer player, T payload) {
        PayloadType<T> type = (PayloadType<T>) TYPES.get(payload.type());
        if (type == null) {
            throw new IllegalStateException("payload type " + payload.type().id() + " is not registered with Net");
        }
        EntityTracking.sendTo(player, type, payload);
    }

    private static NetworkConnection clientConnection() {
        Supplier<NetworkConnection> supplier = clientConnection;
        if (supplier != null && supplier.get() != null) {
            return supplier.get();
        }
        for (NetworkConnection connection : GameNetworkBridge.connections()) {
            if (connection.side() == NetworkSide.CLIENT && connection.isOpen()) {
                return connection;
            }
        }
        return null;
    }

    private static Context context(PayloadContext context) {
        boolean server = context.side() == NetworkSide.SERVER;
        return new Context() {
            @Override
            public Player player() {
                if (server) {
                    MinecraftServer running = Servers.get();
                    return running == null || context.player() == null ? null
                            : running.getPlayerList().getPlayer(context.player().id());
                }
                Supplier<Player> supplier = clientPlayer;
                return supplier != null ? supplier.get() : NfClient.player();
            }

            @Override
            public void enqueueWork(Runnable work) {
                if (server) {
                    MinecraftServer running = Servers.get();
                    if (running != null) {
                        running.execute(work);
                        return;
                    }
                    work.run();
                } else {
                    NfClient.execute(work);
                }
            }

            @Override
            public PacketFlow flow() {
                return server ? PacketFlow.SERVERBOUND : PacketFlow.CLIENTBOUND;
            }
        };
    }

    /** The registry access to encode and decode with: the server's on its thread, else the client's. */
    static RegistryAccess access() {
        MinecraftServer server = Servers.get();
        if (server != null && server.isSameThread()) {
            return server.registryAccess();
        }
        Supplier<RegistryAccess> supplier = clientAccess;
        RegistryAccess client = supplier != null ? supplier.get() : NfClient.registryAccess();
        if (client != null) {
            return client;
        }
        return server != null ? server.registryAccess() : RegistryAccess.EMPTY;
    }

    /** Carries a vanilla stream codec inside a Weaver payload as bytes. */
    private record StreamPayloadCodec<T>(StreamCodec<? super RegistryFriendlyByteBuf, T> codec) implements PayloadCodec<T> {
        @Override
        public void encode(T value, PayloadBuffer out) {
            RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), access());
            try {
                codec.encode(buffer, value);
                byte[] bytes = new byte[buffer.readableBytes()];
                buffer.readBytes(bytes);
                out.writeByteArray(bytes, NetworkProtocol.MAX_PAYLOAD_BYTES - 16);
            } finally {
                buffer.release();
            }
        }

        @Override
        public T decode(PayloadBuffer in) {
            byte[] bytes = in.readByteArray(NetworkProtocol.MAX_PAYLOAD_BYTES - 16);
            RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(bytes), access());
            try {
                return codec.decode(buffer);
            } finally {
                buffer.release();
            }
        }
    }
}
