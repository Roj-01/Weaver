package net.weaver.mptest;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.weaver.api.ModContext;
import net.weaver.api.capability.AttachmentRegistry;
import net.weaver.api.capability.AttachmentType;
import net.weaver.api.capability.Capability;
import net.weaver.api.capability.CapabilitySync;
import net.weaver.api.client.ClientRegistry;
import net.weaver.api.client.ClientTickContext;
import net.weaver.api.client.ClientTickListener;
import net.weaver.api.data.DataCodecs;
import net.weaver.api.registry.DataRegistry;
import net.weaver.api.registry.DataRegistryManager;
import net.weaver.api.event.EventBus;
import net.weaver.api.event.events.network.NetworkConnectedEvent;
import net.weaver.api.game.GameWeaverMod;
import net.weaver.api.game.data.DataMapType;
import net.weaver.api.game.data.DataMaps;
import net.weaver.api.game.event.PlayerJoinEvent;
import net.weaver.api.game.network.RegistrySyncCheck;
import net.weaver.api.network.NetworkPhase;
import net.weaver.api.network.NetworkRegistry;
import net.weaver.api.network.NetworkSide;
import net.weaver.api.network.PayloadBuffer;
import net.weaver.api.network.PayloadCodec;
import net.weaver.api.network.PayloadType;

/**
 * Multiplayer test mod: a dedicated server and a separate client with this mod
 * check what only shows across two processes. The registry check, data
 * registries, a synced data map, a synced player capability, and a channel in
 * both directions. With {@code WEAVER_MP_EXTRA_ITEM=1} the mod registers one
 * more item, so the registry check must refuse the join.
 */
public final class MultiplayerTestMod implements GameWeaverMod {
    record Gem(String color, int hardness) {
    }

    private static final PayloadCodec<String> TEXT = new PayloadCodec<>() {
        @Override
        public void encode(String value, PayloadBuffer out) {
            out.writeString(value);
        }

        @Override
        public String decode(PayloadBuffer in) {
            return in.readString();
        }
    };
    private static final PayloadType<String> PING = PayloadType.of("weavermp", "ping", TEXT);
    private static final PayloadType<String> PONG = PayloadType.of("weavermp", "pong", TEXT);
    private static final Capability<String> MARK = AttachmentType.builder("weavermp:mark", String.class)
            .codec(DataCodecs.utf8String(64), 64).sync(CapabilitySync.RUNTIME).build();
    static final DataMapType<Item, Integer> WORTH = DataMapType.create(
            Identifier.fromNamespaceAndPath("weavermp", "worth"), Registries.ITEM, Codec.INT).synced();

    private static volatile DataRegistry<Gem> gems;
    private static final AtomicReference<ServerPlayer> MARK_PLAYER = new AtomicReference<>();
    private static final AtomicInteger MARK_DELAY = new AtomicInteger(-1);
    private static final AtomicBoolean PONG_SEEN = new AtomicBoolean();
    private static final AtomicBoolean REPORTED = new AtomicBoolean();
    private static final AtomicLong JOINED_AT = new AtomicLong();

    @Override
    public void onInitialize(ModContext context) {
        register("ruby");
        if ("1".equals(System.getenv("WEAVER_MP_EXTRA_ITEM"))) {
            register("extra");
        }
        DataMaps.register(WORTH);
        gems = DataRegistryManager.create("weavermp:gems", Gem.class, MultiplayerTestMod::decode);
        AttachmentRegistry.global().register(MARK);

        NetworkRegistry.global().registerServerbound("weavermp", PING, NetworkPhase.PLAY,
                (payload, reply) -> reply.reply(PONG, "pong:" + payload));
        NetworkRegistry.global().registerClientbound("weavermp", PONG, NetworkPhase.PLAY, (payload, reply) -> {
            if ("pong:ping".equals(payload)) {
                PONG_SEEN.set(true);
            }
        });
        EventBus.GAME.addListener(NetworkConnectedEvent.class, event -> {
            if (event.side() == NetworkSide.CLIENT) {
                event.connection().send(PING, "ping");
            }
        });
        context.events().addListener(PlayerJoinEvent.class, event -> {
            if (event.player() != null) {
                MARK_PLAYER.set(event.player());
                MARK_DELAY.set(20);
            }
        });
        out("MP MOD ready side=" + (context.isPhysicalServer() ? "server" : "client")
                + " extra=" + "1".equals(System.getenv("WEAVER_MP_EXTRA_ITEM")));
    }

    private static void register(String path) {
        Identifier id = Identifier.fromNamespaceAndPath("weavermp", path);
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
        Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)));
    }

    private static Gem decode(String id, Map<String, Object> data) {
        return new Gem((String) data.get("color"), ((Number) data.get("hardness")).intValue());
    }

    @Override
    public void onServerTick(ModContext context, long tick) {
        int delay = MARK_DELAY.get();
        if (delay >= 0 && MARK_DELAY.compareAndSet(delay, delay - 1) && delay == 0) {
            ServerPlayer player = MARK_PLAYER.getAndSet(null);
            if (player != null) {
                AttachmentRegistry.global().set(player, MARK, "from-server");
                out("MP SERVER mark sent player=" + player.getUUID());
            }
        }
    }

    @Override
    public void onClientSetup(ModContext context) {
        ClientRegistry.registerTickListener(new ClientTickListener() {
            @Override
            public void onTick(ClientTickContext tick) {
                clientTick();
            }
        });
    }

    private static void clientTick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (REPORTED.get() || minecraft.player == null || minecraft.getConnection() == null) {
            return;
        }
        long now = System.currentTimeMillis();
        JOINED_AT.compareAndSet(0, now);
        boolean registry = "ok".equals(RegistrySyncCheck.lastResult());
        Integer ruby = WORTH.get(ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("weavermp", "ruby")));
        Integer diamond = WORTH.get(ResourceKey.create(Registries.ITEM, Identifier.withDefaultNamespace("diamond")));
        boolean worth = ruby != null && ruby == 7 && diamond != null && diamond == 100;
        DataRegistry<Gem> registryOfGems = gems;
        Gem gem = registryOfGems == null ? null : registryOfGems.get("weavermp:ruby");
        boolean gemsSynced = gem != null && "red".equals(gem.color()) && gem.hardness() == 7;
        boolean mark = "from-server".equals(AttachmentRegistry.global().get(minecraft.player, MARK));
        boolean pong = PONG_SEEN.get();
        String state = "registry=" + registry + " worth=" + worth + " gems=" + gemsSynced
                + " capability=" + mark + " channel=" + pong;
        if (registry && worth && gemsSynced && mark && pong) {
            if (REPORTED.compareAndSet(false, true)) {
                out("MP CLIENT RESULT ok=true " + state);
            }
        } else if (now - JOINED_AT.get() > 60_000 && REPORTED.compareAndSet(false, true)) {
            List<String> parts = new ArrayList<>(List.of(state.split(" ")));
            out("MP CLIENT RESULT ok=false " + String.join(" ", parts));
        }
    }

    private static void out(String line) {
        System.out.println(line);
    }
}
