package net.weaver.nf;

import io.netty.buffer.Unpooled;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.weaver.api.ModContext;
import net.weaver.api.event.EventBus;
import net.weaver.api.game.event.player.PlayerStartTrackingEvent;

/**
 * Sends the spawn data of {@link IEntityWithComplexSpawn} entities. Each mod's
 * entities use the channel {@code <modid>:nf_complex_spawn}, opened by
 * {@link #init} or by the mod's entity {@code DeferredRegister}, on both sides
 * before players join.
 */
public final class ComplexSpawn {
    private static final String PATH = "nf_complex_spawn";
    private static final Map<String, CustomPacketPayload.Type<Data>> CHANNELS = new ConcurrentHashMap<>();

    static {
        EventBus.GAME.addListener(PlayerStartTrackingEvent.class, event -> send(event));
    }

    /** One entity's spawn data on a mod's channel. */
    private record Data(CustomPacketPayload.Type<Data> type, int entity, byte[] bytes) implements CustomPacketPayload {
    }

    private ComplexSpawn() {
    }

    /** Opens the spawn data channel of {@code context}'s mod. */
    public static void init(ModContext context) {
        open(context.id());
    }

    /** Opens the spawn data channel of the mod {@code namespace}; safe to call more than once. */
    static void open(String namespace) {
        CHANNELS.computeIfAbsent(namespace, ns -> {
            CustomPacketPayload.Type<Data> type = new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(ns, PATH));
            StreamCodec<RegistryFriendlyByteBuf, Data> codec = StreamCodec.of(
                    (buffer, data) -> {
                        buffer.writeVarInt(data.entity());
                        buffer.writeByteArray(data.bytes());
                    },
                    buffer -> new Data(type, buffer.readVarInt(), buffer.readByteArray()));
            Net.playToClient(type, codec, (data, context) -> context.enqueueWork(() -> receive(data, context.player())));
            return type;
        });
    }

    private static void send(PlayerStartTrackingEvent event) {
        Entity target = event.target();
        if (!(target instanceof IEntityWithComplexSpawn complex)) {
            return;
        }
        String namespace = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).getNamespace();
        CustomPacketPayload.Type<Data> type = CHANNELS.get(namespace);
        if (type == null) {
            System.out.println("[weaver] NF COMPLEX SPAWN NO CHANNEL entity=" + target.getType()
                    + " (call ComplexSpawn.init in onInitialize)");
            return;
        }
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), target.registryAccess());
        try {
            complex.writeSpawnData(buffer);
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            Net.sendToPlayer(event.player(), new Data(type, target.getId(), bytes));
        } finally {
            buffer.release();
        }
    }

    private static void receive(Data data, Player player) {
        if (player == null) {
            return;
        }
        Entity entity = player.level().getEntity(data.entity());
        if (entity instanceof IEntityWithComplexSpawn complex) {
            RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(data.bytes()),
                    player.registryAccess());
            try {
                complex.readSpawnData(buffer);
            } finally {
                buffer.release();
            }
        }
    }
}
