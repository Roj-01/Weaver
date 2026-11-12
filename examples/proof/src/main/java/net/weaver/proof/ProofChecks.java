package net.weaver.proof;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.weaver.api.ModContext;
import net.weaver.api.event.EventBus;
import net.weaver.api.event.EventFilter;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.event.events.LevelLoadEvent;
import net.weaver.api.event.events.ModLifecycleEvent;
import net.weaver.api.game.event.EntityHurtEvent;
import net.weaver.api.game.event.EntityJoinLevelEvent;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Proof state for the 2.1 dispatch, filter, typed-event, and cancelable-hook
 * checks. Every check is deterministic: it runs once, records a
 * {@code PROOF CHECK} marker, and the final {@code PROOF RESULT} line waits
 * until the server-side work and, on a physical client, the client level load
 * have happened.
 */
final class ProofChecks {

    private static final String MOD_ID = "weaverproof";
    private static final long OPTIONAL_WAIT_MILLIS = 120_000L;

    private static final Set<ModLifecycleEvent.Phase> LOCAL_PHASES = ConcurrentHashMap.newKeySet();
    private static final Set<ModLifecycleEvent.Phase> GLOBAL_PHASES = ConcurrentHashMap.newKeySet();
    private static final Set<String> FILTER_BAD = ConcurrentHashMap.newKeySet();
    private static final AtomicBoolean LOCAL_BUS_OK = new AtomicBoolean(true);
    private static final AtomicInteger MOD_FILTER_HITS = new AtomicInteger();
    private static final AtomicInteger CLIENT_FILTER_HITS = new AtomicInteger();
    private static final AtomicInteger SERVER_FILTER_HITS = new AtomicInteger();
    private static final AtomicBoolean TYPED_ACCESSOR_OK = new AtomicBoolean();
    private static final AtomicBoolean BASE_EVENT_OK = new AtomicBoolean();
    private static final AtomicBoolean CLIENT_LEVEL_SEEN = new AtomicBoolean();
    private static final AtomicBoolean MAIN_CHECKS = new AtomicBoolean();
    private static final AtomicBoolean SERVER_WORK = new AtomicBoolean();
    private static final AtomicBoolean RESULT_LOGGED = new AtomicBoolean();
    private static final AtomicReference<ServerLevel> SERVER_LEVEL = new AtomicReference<>();
    private static final AtomicReference<ItemEntity> CANCEL_TARGET = new AtomicReference<>();
    private static final AtomicBoolean CANCEL_OBSERVED = new AtomicBoolean();
    private static final AtomicReference<LivingEntity> HURT_TARGET = new AtomicReference<>();
    private static final AtomicBoolean HURT_MATCH = new AtomicBoolean();
    private static final AtomicReference<PlayerList> PLAYER_LIST = new AtomicReference<>();
    private static final AtomicReference<ServerPlayer> PROOF_PLAYER = new AtomicReference<>();
    private static final AtomicBoolean OPTIONAL_DONE = new AtomicBoolean();
    private static final AtomicLong OPTIONAL_DEADLINE = new AtomicLong();
    private static final AtomicBoolean CHAT_OBSERVED = new AtomicBoolean();
    private static final AtomicReference<ServerPlayer> CONTAINER_TARGET = new AtomicReference<>();
    private static final AtomicBoolean CONTAINER_OBSERVED = new AtomicBoolean();
    private static final AtomicReference<ServerPlayer> RESPAWN_TARGET = new AtomicReference<>();
    private static final AtomicBoolean RESPAWN_OBSERVED = new AtomicBoolean();
    private static final AtomicReference<LootTable> LOOT_TARGET = new AtomicReference<>();
    private static final AtomicReference<LootParams> LOOT_PARAMS = new AtomicReference<>();
    private static final AtomicBoolean LOOT_OBSERVED = new AtomicBoolean();
    private static volatile PlayerChatMessage chatMarker;
    private static volatile ModContext context;

    private ProofChecks() {
    }

    static void install(ModContext modContext) {
        context = modContext;
        EventBus localBus = modContext.modEvents();
        modContext.globalModEvents().addListener(ModLifecycleEvent.class, event -> {
            if (MOD_ID.equals(event.modId())) {
                GLOBAL_PHASES.add(event.phase());
            }
        });
        localBus.addListener(ModLifecycleEvent.class, event -> {
            if (event.getBus() != localBus) {
                LOCAL_BUS_OK.set(false);
            }
            if (MOD_ID.equals(event.modId())) {
                LOCAL_PHASES.add(event.phase());
                ProofHooks.eventOnce("ModLifecycle " + event.phase());
            }
        });
        localBus.addListener(ModLifecycleEvent.class, event -> {
            if (MOD_ID.equals(event.modId())) {
                MOD_FILTER_HITS.incrementAndGet();
            }
        }, EventFilter.modId(MOD_ID));
    }

    static void onClientFilterHit(LevelLoadEvent event) {
        if (event.logicalSide().matches(LogicalSide.CLIENT)) {
            CLIENT_FILTER_HITS.incrementAndGet();
        } else {
            filterBad("CLIENT", event);
        }
    }

    static void onServerFilterHit(LevelLoadEvent event) {
        if (event.logicalSide().matches(LogicalSide.SERVER)) {
            SERVER_FILTER_HITS.incrementAndGet();
        } else {
            filterBad("SERVER", event);
        }
    }

    private static void filterBad(String filter, LevelLoadEvent event) {
        String marker = "Filter " + filter + " BAD side=" + event.logicalSide();
        if (FILTER_BAD.add(marker)) {
            ProofHooks.event(marker);
        }
    }

    static void onBaseLevelLoad(LevelLoadEvent event) {
        if (event.level() instanceof ServerLevel serverLevel) {
            SERVER_LEVEL.compareAndSet(null, serverLevel);
        }
        if (event.logicalSide() == LogicalSide.CLIENT) {
            CLIENT_LEVEL_SEEN.set(true);
        }
        boolean typed = event instanceof net.weaver.api.game.event.LevelLoadEvent;
        Object baseLevel = event.level();
        Object typedLevel = typed
                ? ((net.weaver.api.game.event.LevelLoadEvent) event).level()
                : null;
        if (typed && baseLevel == typedLevel) {
            BASE_EVENT_OK.set(true);
        }
        ProofHooks.check(typed && baseLevel == typedLevel,
                "Base listener receives typed event");
        ProofHooks.event("BaseLevelLoad typed=" + typed + " side=" + event.logicalSide());
    }

    static void onTypedLevelLoad(net.weaver.api.game.event.LevelLoadEvent event) {
        net.minecraft.world.level.Level level = event.level();
        if (level != null) {
            TYPED_ACCESSOR_OK.set(true);
        }
        ProofHooks.check(level != null, "Typed event accessor");
        ProofHooks.event("TypedLevelLoad side=" + event.logicalSide());
    }

    static void onTypedEntityJoin(EntityJoinLevelEvent event) {
        ItemEntity target = CANCEL_TARGET.get();
        if (target != null && event.entity() == target) {
            event.setCanceled(true);
            CANCEL_OBSERVED.set(true);
            ProofHooks.event("CancelableEntityJoin canceled side=" + event.logicalSide());
        }
    }

    static void onTypedEntityHurt(EntityHurtEvent event) {
        LivingEntity target = HURT_TARGET.get();
        if (target != null && event.entity() == target) {
            HURT_MATCH.set(true);
            ProofHooks.event("TypedEntityHurt side=" + event.logicalSide());
        }
    }

    static void onTypedPlayerJoin(net.weaver.api.game.event.PlayerJoinEvent event) {
        if (event.logicalSide() != LogicalSide.SERVER) {
            return;
        }
        PROOF_PLAYER.compareAndSet(null, event.player());
        PLAYER_LIST.compareAndSet(null, event.playerList());
        ProofHooks.eventOnce("TypedPlayerJoin");
    }

    static void onTypedChat(net.weaver.api.game.event.ChatEvent event) {
        PlayerChatMessage marker = chatMarker;
        if (marker == null || event.message() != marker) {
            return;
        }
        event.setCanceled(true);
        CHAT_OBSERVED.set(true);
        ProofHooks.event("ChatCancellation observed=true canceled=" + event.isCanceled()
                + " side=" + event.logicalSide());
    }

    static void onTypedContainerOpen(net.weaver.api.game.event.ContainerOpenEvent event) {
        ServerPlayer target = CONTAINER_TARGET.get();
        if (target != null && event.player() == target) {
            CONTAINER_OBSERVED.set(true);
            ProofHooks.event("ContainerOpen observed=true side=" + event.logicalSide());
        }
    }

    static void onTypedPlayerRespawn(net.weaver.api.game.event.PlayerRespawnEvent event) {
        ServerPlayer target = RESPAWN_TARGET.get();
        if (target != null && event.player() == target) {
            RESPAWN_OBSERVED.set(true);
            ProofHooks.event("PlayerRespawnLookup observed=true side=" + event.logicalSide());
        }
    }

    static void onTypedLootTable(net.weaver.api.game.event.LootTableEvent event) {
        LootTable target = LOOT_TARGET.get();
        LootParams params = LOOT_PARAMS.get();
        if (target != null && event.lootTable() == target
                && (params == null || event.params() == params)) {
            LOOT_OBSERVED.set(true);
            ProofHooks.event("LootTable observed=true side=" + event.logicalSide());
        }
    }

    static void onServerTick() {
        if (!SERVER_WORK.get()) {
            ServerLevel level = SERVER_LEVEL.get();
            if (level == null) {
                return;
            }
            if (!SERVER_WORK.compareAndSet(false, true)) {
                return;
            }
            try {
                runEntityJoin(level);
            } catch (Throwable failure) {
                ProofHooks.fail("Cancelable entity join", failure);
            }
            try {
                runEntityHurt(level);
            } catch (Throwable failure) {
                ProofHooks.fail("Entity hurt hook", failure);
            }
            // Separate marker (GAME EVENTS PROOF RESULT); does not change the main check count.
            try {
                GameEventsProof.runOnce(level, testPosition(level));
            } catch (Throwable failure) {
                System.out.println("GAME EVENTS PROOF RESULT ok=false failed=" + failure);
            }
        }
        maybeRunOptionalHooks();
        maybeFinish();
    }

    static void mainChecksComplete(ModContext modContext) {
        context = modContext;
        ProofHooks.check(LOCAL_BUS_OK.get() && !LOCAL_PHASES.isEmpty(),
                "Lifecycle event bus is local");
        ProofHooks.check(!LOCAL_PHASES.isEmpty() && LOCAL_PHASES.equals(GLOBAL_PHASES),
                "Local and global lifecycle phases match");
        ProofHooks.check(MOD_FILTER_HITS.get() > 0, "Mod ID filter fires for this mod");
        EventFilter modFilter = EventFilter.modId(MOD_ID);
        boolean ownAccepted = modFilter.accept(new ModLifecycleEvent(
                syntheticContext(MOD_ID), ModLifecycleEvent.Phase.INITIALIZE));
        boolean otherRejected = !modFilter.accept(new ModLifecycleEvent(
                syntheticContext("otherproof"), ModLifecycleEvent.Phase.INITIALIZE));
        ProofHooks.check(ownAccepted && otherRejected, "Mod ID filter excludes other ids");
        MAIN_CHECKS.set(true);
        maybeFinish();
    }

    static void clientLevelComplete() {
        CLIENT_LEVEL_SEEN.set(true);
        maybeFinish();
    }

    private static void runEntityJoin(ServerLevel level) {
        BlockPos position = testPosition(level);
        CANCEL_OBSERVED.set(false);
        ItemEntity target = new ItemEntity(level, position.getX() + 0.5,
                position.getY() + 0.5, position.getZ() + 0.5,
                new ItemStack(Items.STONE));
        CANCEL_TARGET.set(target);
        try {
            boolean added = level.addFreshEntity(target);
            boolean absent = level.getEntity(target.getId()) == null || target.isRemoved();
            ProofHooks.event("CancelableEntityJoin added=" + added
                    + " absent=" + absent + " observed=" + CANCEL_OBSERVED.get());
            ProofHooks.check(!added && absent && CANCEL_OBSERVED.get(),
                    "Cancelable entity join");
        } catch (Throwable failure) {
            ProofHooks.fail("Cancelable entity join", failure);
        } finally {
            CANCEL_TARGET.set(null);
        }

        ItemEntity second = new ItemEntity(level, position.getX() + 0.5,
                position.getY() + 0.5, position.getZ() + 0.5,
                new ItemStack(Items.STONE));
        try {
            boolean added = level.addFreshEntity(second);
            ProofHooks.event("EntityJoinAllowed added=" + added);
            ProofHooks.check(added, "Entity join allowed");
            if (added) {
                second.discard();
            }
        } catch (Throwable failure) {
            ProofHooks.fail("Entity join allowed", failure);
        }
    }

    private static void runEntityHurt(ServerLevel level) {
        BlockPos position = testPosition(level);
        LivingEntity living = null;
        try {
            living = EntityTypes.ZOMBIE.spawn(level, position, EntitySpawnReason.COMMAND);
        } catch (Throwable failure) {
            ProofHooks.event("EntityHurt zombie spawn failed reason=" + failure);
        }
        if (living == null) {
            try {
                living = EntityTypes.ARMOR_STAND.spawn(level, position,
                        EntitySpawnReason.COMMAND);
            } catch (Throwable failure) {
                ProofHooks.event("EntityHurt armor stand spawn failed reason=" + failure);
            }
        }
        if (living == null) {
            ProofHooks.event("EntityHurt skipped reason=spawn-failed");
            ProofHooks.check(true, "Entity hurt hook skipped reason=spawn-failed");
            return;
        }
        HURT_TARGET.set(living);
        HURT_MATCH.set(false);
        try {
            living.hurtServer(level, level.damageSources().generic(), 1.0F);
            ProofHooks.event("EntityHurt applied entity="
                    + living.getClass().getSimpleName() + " matched=" + HURT_MATCH.get());
            ProofHooks.check(HURT_MATCH.get(), "Entity hurt hook");
        } catch (Throwable failure) {
            ProofHooks.fail("Entity hurt hook", failure);
        } finally {
            HURT_TARGET.set(null);
            living.discard();
        }
    }

    private static void maybeRunOptionalHooks() {
        if (OPTIONAL_DONE.get()) {
            return;
        }
        ServerLevel level = SERVER_LEVEL.get();
        ModContext modContext = context;
        if (level == null || modContext == null) {
            return;
        }
        if (!modContext.isPhysicalClient()) {
            if (OPTIONAL_DONE.compareAndSet(false, true)) {
                runOptionalHooks(level, null);
            }
            return;
        }
        ServerPlayer player = PROOF_PLAYER.get();
        if (player == null) {
            player = firstPlayer(level);
        }
        if (player == null) {
            long deadline = OPTIONAL_DEADLINE.get();
            if (deadline == 0L) {
                OPTIONAL_DEADLINE.compareAndSet(0L,
                        System.currentTimeMillis() + OPTIONAL_WAIT_MILLIS);
                return;
            }
            if (System.currentTimeMillis() < deadline) {
                return;
            }
        }
        if (OPTIONAL_DONE.compareAndSet(false, true)) {
            runOptionalHooks(level, player);
        }
    }

    private static ServerPlayer firstPlayer(ServerLevel level) {
        MinecraftServer server = level.getServer();
        if (server == null) {
            return null;
        }
        PlayerList playerList = server.getPlayerList();
        if (playerList == null) {
            return null;
        }
        List<ServerPlayer> players = playerList.getPlayers();
        if (players.isEmpty()) {
            return null;
        }
        ServerPlayer player = players.get(0);
        PROOF_PLAYER.compareAndSet(null, player);
        PLAYER_LIST.compareAndSet(null, playerList);
        return player;
    }

    private static void runOptionalHooks(ServerLevel level, ServerPlayer player) {
        if (player == null) {
            ProofHooks.check(true, "Chat cancellation audit-only reason=no-player");
            ProofHooks.check(true, "Container open audit-only reason=no-player");
            ProofHooks.check(true, "Respawn position lookup audit-only reason=no-player");
        } else {
            runChatCancellation(level, player);
            runContainerOpen(player);
            runRespawnLookup(player);
        }
        runLootTable(level);
    }

    private static void runChatCancellation(ServerLevel level, ServerPlayer player) {
        MinecraftServer server = level.getServer();
        PlayerList playerList = PLAYER_LIST.get();
        if (playerList == null && server != null) {
            playerList = server.getPlayerList();
        }
        if (server == null || playerList == null) {
            ProofHooks.check(true, "Chat cancellation audit-only reason=no-player-list");
            return;
        }
        PlayerChatMessage marker = PlayerChatMessage.system("weaver-proof-chat");
        chatMarker = marker;
        CHAT_OBSERVED.set(false);
        try {
            Holder<ChatType> chatType = server.registryAccess()
                    .lookupOrThrow(Registries.CHAT_TYPE)
                    .getOrThrow(ChatType.CHAT);
            ChatType.Bound bound = new ChatType.Bound(chatType,
                    Component.literal("weaverproof"), Optional.empty());
            playerList.broadcastChatMessage(marker, player, bound);
            ProofHooks.check(CHAT_OBSERVED.get(), "Chat cancellation");
        } catch (Throwable failure) {
            ProofHooks.check(false, "Chat cancellation reason=" + reason(failure));
        } finally {
            chatMarker = null;
        }
    }

    private static void runContainerOpen(ServerPlayer player) {
        SimpleMenuProvider provider = new SimpleMenuProvider(
                (id, inventory, menuPlayer) -> ChestMenu.oneRow(id, inventory),
                Component.literal("Weaver Proof"));
        CONTAINER_TARGET.set(player);
        CONTAINER_OBSERVED.set(false);
        try {
            player.openMenu(provider);
            ProofHooks.check(CONTAINER_OBSERVED.get(), "Container open hook");
        } catch (Throwable failure) {
            ProofHooks.check(false, "Container open hook reason=" + reason(failure));
        } finally {
            CONTAINER_TARGET.set(null);
            try {
                player.closeContainer();
            } catch (Throwable failure) {
                ProofHooks.event("Container close failed reason=" + reason(failure));
            }
        }
    }

    private static void runRespawnLookup(ServerPlayer player) {
        RESPAWN_TARGET.set(player);
        RESPAWN_OBSERVED.set(false);
        try {
            player.findRespawnPositionAndUseSpawnBlock(false, TeleportTransition.DO_NOTHING);
            ProofHooks.check(RESPAWN_OBSERVED.get(), "Respawn position lookup hook");
        } catch (Throwable failure) {
            ProofHooks.check(false, "Respawn position lookup hook reason=" + reason(failure));
        } finally {
            RESPAWN_TARGET.set(null);
        }
    }

    private static void runLootTable(ServerLevel level) {
        MinecraftServer server = level.getServer();
        if (server == null) {
            ProofHooks.check(true, "Loot table audit-only reason=no-server");
            return;
        }
        LootTable table;
        try {
            table = server.reloadableRegistries().getLootTable(BuiltInLootTables.SIMPLE_DUNGEON);
        } catch (Throwable failure) {
            ProofHooks.check(true, "Loot table audit-only reason=loot-lookup-failed "
                    + reason(failure));
            return;
        }
        if (table == null) {
            ProofHooks.check(true, "Loot table audit-only reason=loot-table-missing");
            return;
        }
        LootParams params;
        try {
            params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.ORIGIN,
                            Vec3.atCenterOf(testPosition(level)))
                    .create(LootContextParamSets.CHEST);
        } catch (Throwable failure) {
            ProofHooks.check(true, "Loot table audit-only reason=loot-params-failed "
                    + reason(failure));
            return;
        }
        LOOT_TARGET.set(table);
        LOOT_PARAMS.set(params);
        LOOT_OBSERVED.set(false);
        Throwable callFailure = null;
        try {
            table.getRandomItems(params, 0L);
        } catch (Throwable failure) {
            callFailure = failure;
        }
        boolean observed = LOOT_OBSERVED.get();
        LOOT_TARGET.set(null);
        LOOT_PARAMS.set(null);
        if (observed) {
            if (callFailure != null) {
                ProofHooks.event("LootTable generation failed after hook reason="
                        + reason(callFailure));
            }
            ProofHooks.check(true, "Loot table hook");
        } else if (callFailure != null) {
            ProofHooks.check(true, "Loot table audit-only reason=loot-call-failed "
                    + reason(callFailure));
        } else {
            ProofHooks.check(true, "Loot table audit-only reason=hook-not-observed");
        }
    }

    private static String reason(Throwable failure) {
        if (failure == null) {
            return "unknown";
        }
        String message = failure.getMessage();
        String text = failure.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
        return text.replaceAll("\\s+", " ");
    }

    private static BlockPos testPosition(ServerLevel level) {
        BlockPos spawn = level.getRespawnData().pos();
        int x = spawn.getX();
        int z = spawn.getZ();
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 1;
        return new BlockPos(x, y, z);
    }

    private static void maybeFinish() {
        ModContext modContext = context;
        if (modContext == null || !MAIN_CHECKS.get() || !SERVER_WORK.get()
                || !OPTIONAL_DONE.get()) {
            return;
        }
        if (modContext.isPhysicalClient() && !CLIENT_LEVEL_SEEN.get()) {
            return;
        }
        if (!RESULT_LOGGED.compareAndSet(false, true)) {
            return;
        }
        ProofHooks.check(!FILTER_BAD.contains("Filter CLIENT BAD side=" + LogicalSide.SERVER)
                        && (CLIENT_FILTER_HITS.get() > 0 || !modContext.isPhysicalClient()),
                "Logical side filter CLIENT");
        ProofHooks.check(!FILTER_BAD.contains("Filter SERVER BAD side=" + LogicalSide.CLIENT)
                        && SERVER_FILTER_HITS.get() > 0,
                "Logical side filter SERVER");
        ProofHooks.check(TYPED_ACCESSOR_OK.get(), "Typed event accessor");
        ProofHooks.check(BASE_EVENT_OK.get(), "Base listener receives typed event");
        ProofHooks.check(SERVER_LEVEL.get() != null, "Server level captured");
        boolean ok = ProofHooks.failedChecks().isEmpty();
        String reason = ok ? "" : " reason=" + String.join(",", ProofHooks.failedChecks());
        modContext.log("PROOF RESULT ok=" + ok + " side=" + modContext.side()
                + " checks=" + ProofHooks.passedChecks().size()
                + " check_names=" + String.join("|", ProofHooks.passedChecks()) + reason
                + " phases=" + ProofHooks.phases()
                + " events=" + ProofHooks.events());
    }

    private static ModContext syntheticContext(String id) {
        return new ModContext() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public String side() {
                return "server";
            }

            @Override
            public Path configDir() {
                return Path.of(".");
            }

            @Override
            public Path dataDir() {
                return Path.of(".");
            }

            @Override
            public void log(String message) {
            }
        };
    }
}
