package net.weaver.hooksproof;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.weaver.api.ModContext;
import net.weaver.api.event.EventBus;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.event.events.LevelLoadEvent;
import net.weaver.api.event.events.ServerTickEvent;
import net.weaver.api.game.event.AnvilUpdateEvent;
import net.weaver.api.game.event.BlockBreakEvent;
import net.weaver.api.game.event.ChunkLoadEvent;
import net.weaver.api.game.event.ContainerCloseEvent;
import net.weaver.api.game.event.EnchantEvent;
import net.weaver.api.game.event.EntityAttackEvent;
import net.weaver.api.game.event.EntityHurtEvent;
import net.weaver.api.game.event.EntityTickEvent;
import net.weaver.api.game.event.LivingEntityDeathEvent;
import net.weaver.api.game.event.PlayerJoinEvent;
import net.weaver.api.game.event.PlayerTeleportEvent;

import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * State machine and listeners for the Weaver 2.2 hooks proof.
 *
 * <p>The required checks run on the first server tick after a
 * {@link ServerLevel} is known. Player-dependent checks wait for a server
 * player for up to {@link #PLAYER_WAIT_MILLIS} and then fall back to
 * audit-only checks so a dedicated run never blocks.
 */
final class HooksProofChecks {

    private static final long PLAYER_WAIT_MILLIS = 120_000L;

    private static final AtomicReference<ModContext> CONTEXT = new AtomicReference<>();
    private static final AtomicReference<ServerLevel> SERVER_LEVEL = new AtomicReference<>();
    private static final AtomicBoolean CLIENT_LEVEL_SEEN = new AtomicBoolean();
    private static final AtomicBoolean REQUIRED_DONE = new AtomicBoolean();
    private static final AtomicBoolean OPTIONAL_DONE = new AtomicBoolean();
    private static final AtomicLong OPTIONAL_DEADLINE = new AtomicLong();
    private static final AtomicBoolean RESULT_LOGGED = new AtomicBoolean();

    private static final AtomicReference<LivingEntity> HURT_TARGET = new AtomicReference<>();
    private static final AtomicBoolean HURT_CANCEL = new AtomicBoolean();
    private static final AtomicBoolean HURT_OBSERVED = new AtomicBoolean();

    private static final AtomicReference<LivingEntity> DEATH_TARGET = new AtomicReference<>();
    private static final AtomicBoolean DEATH_OBSERVED = new AtomicBoolean();

    private static final AtomicReference<ServerPlayer> PLAYER = new AtomicReference<>();

    private static final AtomicReference<ServerPlayer> CONTAINER_TARGET = new AtomicReference<>();
    private static final AtomicReference<AbstractContainerMenu> CONTAINER_MENU = new AtomicReference<>();
    private static final AtomicBoolean CONTAINER_OBSERVED = new AtomicBoolean();

    private static final AtomicReference<AnvilMenu> ANVIL_TARGET = new AtomicReference<>();
    private static final AtomicBoolean ANVIL_OBSERVED = new AtomicBoolean();

    private static final AtomicReference<EnchantmentMenu> ENCHANT_TARGET = new AtomicReference<>();
    private static final AtomicBoolean ENCHANT_OBSERVED = new AtomicBoolean();

    private static final AtomicReference<ChunkPos> CHUNK_LOAD_TARGET = new AtomicReference<>();
    private static final AtomicBoolean CHUNK_LOAD_OBSERVED = new AtomicBoolean();

    private static final AtomicReference<Entity> ENTITY_TICK_TARGET = new AtomicReference<>();
    private static final AtomicReference<ChunkPos> ENTITY_TICK_CHUNK = new AtomicReference<>();
    private static final AtomicBoolean ENTITY_TICK_OBSERVED = new AtomicBoolean();
    private static final AtomicBoolean ENTITY_TICK_CHECKED = new AtomicBoolean();
    private static final java.util.concurrent.atomic.AtomicInteger ENTITY_TICK_WAIT =
            new java.util.concurrent.atomic.AtomicInteger();

    private static final AtomicReference<LivingEntity> ATTACK_TARGET = new AtomicReference<>();
    private static final AtomicBoolean ATTACK_OBSERVED = new AtomicBoolean();

    private static final AtomicReference<ServerPlayer> BREAK_TARGET = new AtomicReference<>();
    private static final AtomicBoolean BREAK_OBSERVED = new AtomicBoolean();

    private HooksProofChecks() {
    }

    static void install(ModContext modContext) {
        CONTEXT.set(modContext);
        EventBus bus = modContext.events();
        bus.addListener(LevelLoadEvent.class, HooksProofChecks::onLevelLoad);
        bus.addListener(EntityHurtEvent.class, HooksProofChecks::onEntityHurt);
        bus.addListener(LivingEntityDeathEvent.class, HooksProofChecks::onLivingEntityDeath);
        bus.addListener(ContainerCloseEvent.class, HooksProofChecks::onContainerClose);
        bus.addListener(AnvilUpdateEvent.class, HooksProofChecks::onAnvilUpdate);
        bus.addListener(EnchantEvent.class, HooksProofChecks::onEnchant);
        bus.addListener(PlayerJoinEvent.class, HooksProofChecks::onPlayerJoin);
        bus.addListener(PlayerTeleportEvent.class, HooksProofChecks::onPlayerTeleport);
        bus.addListener(EntityAttackEvent.class, HooksProofChecks::onEntityAttack);
        bus.addListener(BlockBreakEvent.class, HooksProofChecks::onBlockBreak);
        bus.addListener(ChunkLoadEvent.class, HooksProofChecks::onChunkLoad);
        bus.addListener(EntityTickEvent.class, HooksProofChecks::onEntityTick);
        bus.addListener(ServerTickEvent.Post.class, HooksProofChecks::onServerTickPost);
    }

    static void onServerTick(ModContext context, long tick) {
        CONTEXT.set(context);
        ServerLevel level = SERVER_LEVEL.get();
        if (level == null) {
            return;
        }
        if (REQUIRED_DONE.compareAndSet(false, true)) {
            HooksProof.event("ServerWork tick=" + tick);
            try {
                runRequired(level);
            } catch (Throwable failure) {
                HooksProof.failIfNotChecked("Entity hurt cancellation", failure);
                HooksProof.failIfNotChecked("Entity hurt allowed", failure);
                HooksProof.failIfNotChecked("Living entity death hook", failure);
                HooksProof.failIfNotChecked("Chunk load hook", failure);
                HooksProof.failIfNotChecked("Entity tick hook", failure);
            }
        }
        runOptional(level);
        maybeFinish(context);
    }

    private static void onLevelLoad(LevelLoadEvent event) {
        if (event.level() instanceof ServerLevel serverLevel
                && SERVER_LEVEL.compareAndSet(null, serverLevel)) {
            HooksProof.event("ServerLevelLoad side=" + event.logicalSide());
        }
        if (event.logicalSide() == LogicalSide.CLIENT) {
            CLIENT_LEVEL_SEEN.set(true);
            HooksProof.eventOnce("ClientLevelLoad");
        }
    }

    private static void onEntityHurt(EntityHurtEvent event) {
        LivingEntity target = HURT_TARGET.get();
        if (target == null || event.entity() != target) {
            return;
        }
        HURT_OBSERVED.set(true);
        boolean cancel = HURT_CANCEL.get();
        if (cancel) {
            event.setCanceled(true);
        }
        HooksProof.event("EntityHurt side=" + event.logicalSide()
                + " canceled=" + cancel);
    }

    private static void onLivingEntityDeath(LivingEntityDeathEvent event) {
        LivingEntity target = DEATH_TARGET.get();
        if (target != null && event.entity() == target) {
            DEATH_OBSERVED.set(true);
            HooksProof.event("LivingEntityDeath side=" + event.logicalSide());
        }
    }

    private static void onContainerClose(ContainerCloseEvent event) {
        ServerPlayer target = CONTAINER_TARGET.get();
        if (target != null && event.player() == target) {
            CONTAINER_MENU.set(event.menu());
            CONTAINER_OBSERVED.set(true);
            HooksProof.event("ContainerClose side=" + event.logicalSide()
                    + " menu=" + (event.menu() != null));
        }
    }

    private static void onAnvilUpdate(AnvilUpdateEvent event) {
        AnvilMenu target = ANVIL_TARGET.get();
        if (target != null && event.menu() == target) {
            ANVIL_OBSERVED.set(true);
            HooksProof.event("AnvilUpdate side=" + event.logicalSide());
        }
    }

    private static void onEnchant(EnchantEvent event) {
        EnchantmentMenu target = ENCHANT_TARGET.get();
        if (target != null && event.menu() == target) {
            ENCHANT_OBSERVED.set(true);
            HooksProof.event("Enchant side=" + event.logicalSide());
        }
    }

    private static void onPlayerJoin(PlayerJoinEvent event) {
        if (event.logicalSide() != LogicalSide.SERVER) {
            return;
        }
        PLAYER.compareAndSet(null, event.player());
        HooksProof.eventOnce("PlayerJoin");
    }

    private static void onPlayerTeleport(PlayerTeleportEvent event) {
        HooksProof.eventOnce("PlayerTeleport side=" + event.logicalSide());
    }

    private static void onEntityAttack(EntityAttackEvent event) {
        LivingEntity target = ATTACK_TARGET.get();
        if (target != null && event.target() == target) {
            ATTACK_OBSERVED.set(true);
            HooksProof.event("EntityAttack side=" + event.logicalSide());
        }
    }

    private static void onBlockBreak(BlockBreakEvent event) {
        ServerPlayer target = BREAK_TARGET.get();
        if (target != null && event.player() == target) {
            BREAK_OBSERVED.set(true);
            HooksProof.event("BlockBreak side=" + event.logicalSide()
                    + " pos=" + event.pos());
        }
    }

    private static void onChunkLoad(ChunkLoadEvent event) {
        ChunkPos target = CHUNK_LOAD_TARGET.get();
        if (target != null && event.chunk().getPos().equals(target)) {
            CHUNK_LOAD_OBSERVED.set(true);
            HooksProof.event("ChunkLoad side=" + event.logicalSide()
                    + " chunk=" + target);
        }
    }

    private static void onEntityTick(EntityTickEvent event) {
        if (event.logicalSide() != LogicalSide.SERVER) {
            return;
        }
        Entity target = ENTITY_TICK_TARGET.get();
        if (target != null && event.entity() == target) {
            ENTITY_TICK_OBSERVED.set(true);
            HooksProof.eventOnce("EntityTick side=" + event.side());
        }
    }

    private static void onServerTickPost(ServerTickEvent.Post event) {
        Entity target = ENTITY_TICK_TARGET.get();
        if (target == null || ENTITY_TICK_CHECKED.get()) {
            return;
        }
        if (ENTITY_TICK_WAIT.get() > 0) {
            ENTITY_TICK_WAIT.decrementAndGet();
            return;
        }
        if (!ENTITY_TICK_CHECKED.compareAndSet(false, true)) {
            return;
        }
        boolean observed = ENTITY_TICK_OBSERVED.get();
        HooksProof.event("EntityTickCheck observed=" + observed);
        HooksProof.check(observed, "Entity tick hook");
        ENTITY_TICK_TARGET.set(null);
        ChunkPos chunk = ENTITY_TICK_CHUNK.getAndSet(null);
        ServerLevel serverLevel = SERVER_LEVEL.get();
        if (chunk != null && serverLevel != null) {
            serverLevel.setChunkForced(chunk.x(), chunk.z(), false);
        }
        if (target instanceof LivingEntity living) {
            discard(living);
        }
    }

    private static void runRequired(ServerLevel level) {
        try {
            runEntityHurt(level);
        } catch (Throwable failure) {
            HooksProof.failIfNotChecked("Entity hurt cancellation", failure);
            HooksProof.failIfNotChecked("Entity hurt allowed", failure);
        }
        try {
            runLivingEntityDeath(level);
        } catch (Throwable failure) {
            HooksProof.failIfNotChecked("Living entity death hook", failure);
        }
        try {
            runChunkLoad(level);
        } catch (Throwable failure) {
            HooksProof.failIfNotChecked("Chunk load hook", failure);
        }
        try {
            runEntityTick(level);
        } catch (Throwable failure) {
            HooksProof.failIfNotChecked("Entity tick hook", failure);
        }
    }

    private static void runChunkLoad(ServerLevel level) {
        BlockPos far = testPosition(level).offset(512, 0, 512);
        ChunkPos target = ChunkPos.containing(far);
        CHUNK_LOAD_TARGET.set(target);
        CHUNK_LOAD_OBSERVED.set(false);
        try {
            LevelChunk chunk = level.getChunk(target.x(), target.z());
            boolean observed = CHUNK_LOAD_OBSERVED.get();
            if (!observed) {
                level.startTickingChunk(chunk);
                observed = CHUNK_LOAD_OBSERVED.get();
            }
            HooksProof.event("ChunkLoadCall chunk=" + target
                    + " observed=" + observed + " same=" + chunk.getPos().equals(target));
            HooksProof.check(observed && chunk.getPos().equals(target), "Chunk load hook");
        } catch (Throwable failure) {
            HooksProof.fail("Chunk load hook", failure);
        } finally {
            CHUNK_LOAD_TARGET.set(null);
            CHUNK_LOAD_OBSERVED.set(false);
        }
    }

    private static void runEntityTick(ServerLevel level) {
        LivingEntity target = spawnTarget(level);
        if (target == null) {
            ENTITY_TICK_CHECKED.set(true);
            HooksProof.fail("Entity tick hook",
                    new IllegalStateException("target spawn failed"));
            return;
        }
        ENTITY_TICK_OBSERVED.set(false);
        ENTITY_TICK_CHECKED.set(false);
        ENTITY_TICK_WAIT.set(40);
        ENTITY_TICK_TARGET.set(target);
        ChunkPos chunk = ChunkPos.containing(target.blockPosition());
        ENTITY_TICK_CHUNK.set(chunk);
        level.setChunkForced(chunk.x(), chunk.z(), true);
        HooksProof.event("EntityTickArmed entity=" + target.getId()
                + " chunk=" + chunk + " entityTicking="
                + level.isPositionEntityTicking(target.blockPosition()));
    }

    private static void runEntityHurt(ServerLevel level) {
        LivingEntity target = spawnTarget(level);
        if (target == null) {
            IllegalStateException failure = new IllegalStateException("target spawn failed");
            HooksProof.fail("Entity hurt cancellation", failure);
            HooksProof.fail("Entity hurt allowed", failure);
            return;
        }
        try {
            DamageSource source = damageSource(level, target);

            HURT_TARGET.set(target);
            HURT_CANCEL.set(true);
            HURT_OBSERVED.set(false);
            float beforeCancel = target.getHealth();
            boolean canceled = target.hurtServer(level, source, 1.0F);
            float afterCancel = target.getHealth();
            HooksProof.event("EntityHurtCancellation result=" + canceled
                    + " observed=" + HURT_OBSERVED.get()
                    + " before=" + beforeCancel + " after=" + afterCancel);
            HooksProof.check(!canceled && HURT_OBSERVED.get()
                    && afterCancel == beforeCancel, "Entity hurt cancellation");

            HURT_CANCEL.set(false);
            HURT_OBSERVED.set(false);
            float beforeAllowed = target.getHealth();
            boolean allowed = target.hurtServer(level, source, 1.0F);
            float afterAllowed = target.getHealth();
            HooksProof.event("EntityHurtAllowed result=" + allowed
                    + " observed=" + HURT_OBSERVED.get()
                    + " before=" + beforeAllowed + " after=" + afterAllowed);
            HooksProof.check(allowed && HURT_OBSERVED.get()
                    && afterAllowed < beforeAllowed, "Entity hurt allowed");
        } catch (Throwable failure) {
            HooksProof.failIfNotChecked("Entity hurt cancellation", failure);
            HooksProof.failIfNotChecked("Entity hurt allowed", failure);
        } finally {
            HURT_TARGET.set(null);
            HURT_CANCEL.set(false);
            HURT_OBSERVED.set(false);
            discard(target);
        }
    }

    private static void runLivingEntityDeath(ServerLevel level) {
        LivingEntity target = spawnTarget(level);
        if (target == null) {
            HooksProof.fail("Living entity death hook",
                    new IllegalStateException("target spawn failed"));
            return;
        }
        DEATH_TARGET.set(target);
        DEATH_OBSERVED.set(false);
        try {
            target.die(level.damageSources().generic());
            HooksProof.event("LivingEntityDeathCall observed=" + DEATH_OBSERVED.get());
            HooksProof.check(DEATH_OBSERVED.get(), "Living entity death hook");
        } catch (Throwable failure) {
            HooksProof.fail("Living entity death hook", failure);
        } finally {
            DEATH_TARGET.set(null);
            DEATH_OBSERVED.set(false);
            discard(target);
        }
    }

    private static void runOptional(ServerLevel level) {
        if (OPTIONAL_DONE.get()) {
            return;
        }
        ModContext context = CONTEXT.get();
        if (context == null) {
            return;
        }
        if (!context.isPhysicalClient()) {
            if (OPTIONAL_DONE.compareAndSet(false, true)) {
                runPlayerChecks(null);
            }
            return;
        }
        ServerPlayer player = PLAYER.get();
        if (player == null) {
            player = firstPlayer(level);
        }
        if (player == null) {
            long deadline = OPTIONAL_DEADLINE.get();
            if (deadline == 0L) {
                if (OPTIONAL_DEADLINE.compareAndSet(0L,
                        System.currentTimeMillis() + PLAYER_WAIT_MILLIS)) {
                    HooksProof.event("PlayerWait deadline_ms=" + PLAYER_WAIT_MILLIS);
                }
                return;
            }
            if (System.currentTimeMillis() < deadline) {
                return;
            }
        }
        if (OPTIONAL_DONE.compareAndSet(false, true)) {
            runPlayerChecks(player);
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
        PLAYER.compareAndSet(null, player);
        return player;
    }

    private static void runPlayerChecks(ServerPlayer player) {
        if (player == null) {
            HooksProof.check("Container close audit-only reason=no-player");
            HooksProof.check("Anvil update audit-only reason=no-player");
            HooksProof.check("Enchant hook audit-only reason=no-player");
            HooksProof.check("Entity attack audit-only reason=no-player");
            HooksProof.check("Block break audit-only reason=no-player");
            return;
        }
        runContainerClose(player);
        runAnvilUpdate(player);
        runEnchant(player);
        runEntityAttack(player);
        runBlockBreak(player);
    }

    private static void runEntityAttack(ServerPlayer player) {
        ServerLevel level = player.level() instanceof ServerLevel serverLevel
                ? serverLevel : null;
        LivingEntity target = level == null ? null : spawnTarget(level);
        if (target == null) {
            HooksProof.fail("Entity attack hook",
                    new IllegalStateException("target spawn failed"));
            return;
        }
        ATTACK_TARGET.set(target);
        ATTACK_OBSERVED.set(false);
        try {
            player.attack(target);
            boolean observed = ATTACK_OBSERVED.get();
            HooksProof.event("EntityAttackCall observed=" + observed);
            HooksProof.check(observed, "Entity attack hook");
        } catch (Throwable failure) {
            HooksProof.fail("Entity attack hook", failure);
        } finally {
            ATTACK_TARGET.set(null);
            ATTACK_OBSERVED.set(false);
            discard(target);
        }
    }

    private static void runBlockBreak(ServerPlayer player) {
        ServerLevel level = player.level() instanceof ServerLevel serverLevel
                ? serverLevel : null;
        if (level == null) {
            HooksProof.fail("Block break hook",
                    new IllegalStateException("server level unavailable"));
            return;
        }
        BlockPos pos = testPosition(level).below();
        BREAK_TARGET.set(player);
        BREAK_OBSERVED.set(false);
        try {
            boolean destroyed = player.gameMode.destroyBlock(pos);
            boolean observed = BREAK_OBSERVED.get();
            HooksProof.event("BlockBreakCall pos=" + pos
                    + " destroyed=" + destroyed + " observed=" + observed);
            HooksProof.check(observed, "Block break hook");
        } catch (Throwable failure) {
            HooksProof.fail("Block break hook", failure);
        } finally {
            BREAK_TARGET.set(null);
            BREAK_OBSERVED.set(false);
        }
    }

    private static void runContainerClose(ServerPlayer player) {
        try {
            player.closeContainer();
        } catch (Throwable failure) {
            HooksProof.event("ContainerClose preclose failed reason=" + reason(failure));
        }

        SimpleMenuProvider provider = new SimpleMenuProvider(
                (id, inventory, menuPlayer) -> ChestMenu.oneRow(id, inventory),
                Component.literal("Weaver Hooks Proof"));
        CONTAINER_TARGET.set(player);
        CONTAINER_MENU.set(null);
        CONTAINER_OBSERVED.set(false);
        try {
            OptionalInt opened = player.openMenu(provider);
            boolean openedOk = opened.isPresent();
            player.closeContainer();
            AbstractContainerMenu menu = CONTAINER_MENU.get();
            boolean observed = CONTAINER_OBSERVED.get();
            HooksProof.event("ContainerClose opened=" + openedOk
                    + " observed=" + observed + " menu=" + (menu != null));
            HooksProof.check(openedOk && observed && menu != null, "Container close hook");
        } catch (Throwable failure) {
            HooksProof.fail("Container close hook", failure);
        } finally {
            CONTAINER_TARGET.set(null);
            CONTAINER_MENU.set(null);
            CONTAINER_OBSERVED.set(false);
            try {
                player.closeContainer();
            } catch (Throwable failure) {
                HooksProof.event("ContainerClose cleanup failed reason=" + reason(failure));
            }
        }
    }

    private static void runAnvilUpdate(ServerPlayer player) {
        AnvilMenu menu = null;
        try {
            menu = new AnvilMenu(0, player.getInventory());
            ANVIL_TARGET.set(menu);
            ANVIL_OBSERVED.set(false);
            menu.createResult();
            boolean observed = ANVIL_OBSERVED.get();
            HooksProof.event("AnvilUpdateCall observed=" + observed);
            if (observed) {
                HooksProof.check("Anvil update hook");
            } else {
                HooksProof.check("Anvil update audit-only reason=hook-not-observed");
            }
        } catch (Throwable failure) {
            HooksProof.event("AnvilUpdate skipped reason=" + reason(failure));
            if (ANVIL_OBSERVED.get()) {
                HooksProof.check("Anvil update hook");
            } else {
                HooksProof.check("Anvil update audit-only reason=" + reason(failure));
            }
        } finally {
            ANVIL_TARGET.set(null);
            ANVIL_OBSERVED.set(false);
        }
    }

    private static void runEnchant(ServerPlayer player) {
        EnchantmentMenu menu = null;
        try {
            menu = new EnchantmentMenu(0, player.getInventory());
            ENCHANT_TARGET.set(menu);
            ENCHANT_OBSERVED.set(false);
            menu.clickMenuButton(player, 0);
            boolean observed = ENCHANT_OBSERVED.get();
            HooksProof.event("EnchantCall observed=" + observed);
            if (observed) {
                HooksProof.check("Enchant hook");
            } else {
                HooksProof.check("Enchant hook audit-only reason=hook-not-observed");
            }
        } catch (Throwable failure) {
            HooksProof.event("Enchant skipped reason=" + reason(failure));
            if (ENCHANT_OBSERVED.get()) {
                HooksProof.check("Enchant hook");
            } else {
                HooksProof.check("Enchant hook audit-only reason=" + reason(failure));
            }
        } finally {
            ENCHANT_TARGET.set(null);
            ENCHANT_OBSERVED.set(false);
        }
    }

    private static LivingEntity spawnTarget(ServerLevel level) {
        BlockPos position = testPosition(level);
        LivingEntity living = null;
        try {
            living = EntityTypes.ZOMBIE.spawn(level, position, EntitySpawnReason.COMMAND);
        } catch (Throwable failure) {
            HooksProof.event("ZombieSpawn failed reason=" + reason(failure));
        }
        if (living == null) {
            try {
                living = EntityTypes.ARMOR_STAND.spawn(level, position,
                        EntitySpawnReason.COMMAND);
            } catch (Throwable failure) {
                HooksProof.event("ArmorStandSpawn failed reason=" + reason(failure));
            }
        }
        return living;
    }

    private static DamageSource damageSource(ServerLevel level, LivingEntity target) {
        if (target instanceof ArmorStand) {
            try {
                Arrow arrow = new Arrow(EntityTypes.ARROW, level);
                return level.damageSources().arrow(arrow, null);
            } catch (Throwable failure) {
                HooksProof.event("ArmorStandDamageSource fallback reason=" + reason(failure));
            }
        }
        return level.damageSources().generic();
    }

    private static BlockPos testPosition(ServerLevel level) {
        BlockPos spawn = level.getRespawnData().pos();
        int x = spawn.getX();
        int z = spawn.getZ();
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 1;
        return new BlockPos(x, y, z);
    }

    private static void discard(LivingEntity entity) {
        try {
            entity.discard();
        } catch (Throwable failure) {
            HooksProof.event("Discard failed reason=" + reason(failure));
        }
    }

    private static void maybeFinish(ModContext context) {
        if (!REQUIRED_DONE.get() || !OPTIONAL_DONE.get() || !ENTITY_TICK_CHECKED.get()) {
            return;
        }
        if (context.isPhysicalClient() && !CLIENT_LEVEL_SEEN.get()) {
            return;
        }
        if (SERVER_LEVEL.get() == null) {
            return;
        }
        if (!RESULT_LOGGED.compareAndSet(false, true)) {
            return;
        }
        HooksProof.result(context);
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
}
