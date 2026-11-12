package net.weaver.proof;

import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Either;
import java.io.File;
import java.lang.reflect.Field;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.PlayerDataStorage;
import net.weaver.api.game.event.living.CanContinueSleepingEvent;
import net.weaver.api.game.event.player.CanPlayerSleepEvent;
import net.weaver.api.game.event.player.ItemPickupPostEvent;
import net.weaver.api.game.event.player.PermissionsChangedEvent;
import net.weaver.api.game.event.player.PlayerBreakSpeedEvent;
import net.weaver.api.game.event.player.PlayerFlyableFallEvent;
import net.weaver.api.game.event.player.PlayerHarvestCheckEvent;
import net.weaver.api.game.event.player.PlayerNameFormatEvent;
import net.weaver.api.game.event.player.PlayerSaveToFileEvent;
import net.weaver.api.game.event.player.PlayerSetSpawnEvent;
import net.weaver.api.game.event.player.PlayerTabListNameFormatEvent;
import net.weaver.api.game.event.player.StatAwardEvent;
import net.weaver.api.game.event.world.ItemExpireEvent;

/**
 * Batch 4 (players and items) part of {@link GameEventsProof}. Uses a
 * detached {@link ServerPlayer} with no connection, never added to the level
 * or the player list, so only code paths that do not send packets are driven.
 * Joining, tracking, and phantom spawning need a connected player and are
 * covered by the mixin-applied check.
 */
final class GameEventsPlayerProof {
    private static final String NAME = "weaver_proof";

    private final ServerLevel level;
    private final BlockPos base;
    private final GameEventsProof proof;
    private ServerPlayer player;

    GameEventsPlayerProof(ServerLevel level, BlockPos base, GameEventsProof proof) {
        this.level = level;
        this.base = base;
        this.proof = proof;
    }

    void run() {
        UUID id = UUID.nameUUIDFromBytes(("weaver-proof:" + NAME).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        player = new ServerPlayer(level.getServer(), level, new GameProfile(id, NAME), ClientInformation.createDefault());
        player.setPos(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
        checkBreakSpeed();
        proof.clearListeners();
        checkHarvest();
        proof.clearListeners();
        checkNames();
        proof.clearListeners();
        checkSetSpawn();
        proof.clearListeners();
        checkFlyableFall();
        proof.clearListeners();
        checkStats();
        proof.clearListeners();
        checkPermissions();
        proof.clearListeners();
        checkSleep();
        proof.clearListeners();
        checkContinueSleeping();
        proof.clearListeners();
        checkItemExpire();
        proof.clearListeners();
        checkPickupPost();
        proof.clearListeners();
        checkSave();
        proof.clearListeners();
        new GameEventsMenuProof(level, base, proof, player).run();
    }

    private void checkBreakSpeed() {
        AtomicInteger fired = new AtomicInteger();
        proof.listen(PlayerBreakSpeedEvent.class, event -> {
            if (event.player() == player && event.state().is(Blocks.STONE)) {
                fired.incrementAndGet();
                event.setSpeed(42.0F);
            }
        });
        float speed = player.getDestroySpeed(Blocks.STONE.defaultBlockState());
        proof.check(fired.get() == 1 && speed == 42.0F, "break speed fired=" + fired.get() + " speed=" + speed);
    }

    private void checkHarvest() {
        AtomicInteger fired = new AtomicInteger();
        proof.listen(PlayerHarvestCheckEvent.class, event -> {
            if (event.player() == player && event.state().is(Blocks.OBSIDIAN)) {
                fired.incrementAndGet();
                event.setCanHarvest(true);
            }
        });
        boolean harvest = player.hasCorrectToolForDrops(Blocks.OBSIDIAN.defaultBlockState());
        proof.check(fired.get() == 1 && harvest, "harvest check fired=" + fired.get() + " harvest=" + harvest);
    }

    private void checkNames() {
        proof.listen(PlayerNameFormatEvent.class, event -> {
            if (event.player() == player) {
                event.setDisplayName(Component.literal("WeaverName"));
            }
        });
        proof.listen(PlayerTabListNameFormatEvent.class, event -> {
            if (event.player() == player) {
                event.setDisplayName(Component.literal("WeaverTab"));
            }
        });
        String display = player.getDisplayName().getString();
        Component tab = player.getTabListDisplayName();
        proof.check(display.contains("WeaverName"), "name format display=" + display);
        proof.check(tab != null && tab.getString().equals("WeaverTab"),
                "tab list name=" + (tab == null ? "null" : tab.getString()));
    }

    private void checkSetSpawn() {
        AtomicInteger fired = new AtomicInteger();
        proof.listen(PlayerSetSpawnEvent.class, event -> {
            if (event.player() == player) {
                fired.incrementAndGet();
                event.setCanceled(true);
            }
        });
        ServerPlayer.RespawnConfig before = player.getRespawnConfig();
        player.setRespawnPosition(new ServerPlayer.RespawnConfig(
                LevelData.RespawnData.of(level.dimension(), base, 0.0F, 0.0F), true), false);
        proof.check(fired.get() == 1 && player.getRespawnConfig() == before,
                "set spawn canceled fired=" + fired.get());
    }

    private void checkFlyableFall() {
        AtomicInteger fired = new AtomicInteger();
        proof.listen(PlayerFlyableFallEvent.class, event -> {
            if (event.player() == player && event.distance() == 5.0) {
                fired.incrementAndGet();
            }
        });
        player.getAbilities().mayfly = true;
        try {
            player.causeFallDamage(5.0, 1.0F, level.damageSources().fall());
        } finally {
            player.getAbilities().mayfly = false;
        }
        proof.check(fired.get() == 1, "flyable fall fired=" + fired.get());
    }

    private void checkStats() {
        Stat<?> jump = Stats.CUSTOM.get(Stats.JUMP);
        proof.listen(StatAwardEvent.class, event -> {
            if (event.player() == player && event.stat() == jump) {
                event.setValue(77);
            }
        });
        player.awardStat(jump);
        int changed = player.getStats().getValue(jump);
        proof.clearListeners();
        proof.listen(StatAwardEvent.class, event -> {
            if (event.player() == player && event.stat() == jump) {
                event.setCanceled(true);
            }
        });
        player.awardStat(jump);
        int kept = player.getStats().getValue(jump);
        proof.check(changed == 77, "stat award value=" + changed);
        proof.check(kept == 77, "stat award canceled value=" + kept);
    }

    private void checkPermissions() {
        NameAndId who = new NameAndId(player.getUUID(), NAME);
        AtomicInteger fired = new AtomicInteger();
        proof.listen(PermissionsChangedEvent.class, event -> {
            if (event.player().equals(who)) {
                fired.incrementAndGet();
                event.setCanceled(true);
            }
        });
        level.getServer().getPlayerList().op(who);
        boolean op = level.getServer().getPlayerList().isOp(who);
        proof.check(fired.get() == 1 && !op, "op canceled fired=" + fired.get() + " op=" + op);
    }

    private void checkSleep() {
        AtomicReference<Player.BedSleepingProblem> seen = new AtomicReference<>();
        AtomicInteger fired = new AtomicInteger();
        // The detached player has no connection; keep vanilla from sending the
        // "respawn point set" message.
        proof.listen(PlayerSetSpawnEvent.class, event -> event.setCanceled(true));
        proof.listen(CanPlayerSleepEvent.class, event -> {
            if (event.player() == player) {
                fired.incrementAndGet();
                seen.set(event.vanillaProblem());
                event.setProblem(Player.BedSleepingProblem.OBSTRUCTED);
            }
        });
        AbstractBedBlock bed = (AbstractBedBlock) Blocks.STRAW_BED;
        Either<Player.BedSleepingProblem, Unit> result = player.startSleepInBed(bed, bed.defaultBlockState(),
                bed.getBedRule(level, base), base);
        boolean refused = result.left().orElse(null) == Player.BedSleepingProblem.OBSTRUCTED;
        proof.check(fired.get() == 1 && refused && !player.isSleeping(),
                "can sleep fired=" + fired.get() + " vanilla=" + seen.get() + " result=" + result);
    }

    private void checkContinueSleeping() {
        Mob villager = EntityTypes.VILLAGER.spawn(level, base.east(4), EntitySpawnReason.COMMAND);
        if (villager == null) {
            proof.check(false, "continue sleeping spawn villager");
            return;
        }
        try {
            villager.setNoAi(true);
            // A mob's first tick re-seats it on its bed and wakes it if there is none.
            villager.tick();
            AtomicReference<Player.BedSleepingProblem> seen = new AtomicReference<>();
            AtomicInteger fired = new AtomicInteger();
            proof.listen(CanContinueSleepingEvent.class, event -> {
                if (event.entity() == villager) {
                    fired.incrementAndGet();
                    seen.set(event.problem());
                    event.setContinueSleeping(true);
                }
            });
            BlockPos bedPos = villager.blockPosition();
            level.setBlock(bedPos, Blocks.STRAW_BED.defaultBlockState(), 3);
            boolean started = villager.startSleeping(bedPos);
            boolean asleep = villager.isSleeping();
            // With the bed gone, vanilla wakes the villager on its next tick.
            level.setBlock(bedPos, Blocks.AIR.defaultBlockState(), 3);
            boolean beforeTick = villager.isSleeping();
            boolean interact = villager.canInteractWithLevel();
            villager.tick();
            proof.check(fired.get() >= 1 && seen.get() != null && villager.isSleeping(),
                    "continue sleeping started=" + started + " asleep=" + asleep + " beforeTick=" + beforeTick + " interact=" + interact + " fired=" + fired.get() + " problem=" + seen.get()
                            + " sleeping=" + villager.isSleeping());
        } finally {
            villager.discard();
        }
    }

    private void checkItemExpire() {
        ItemEntity item = new ItemEntity(level, base.getX() + 0.5, base.getY() + 1, base.getZ() + 8.5,
                new ItemStack(Items.STONE));
        level.addFreshEntity(item);
        try {
            AtomicInteger fired = new AtomicInteger();
            proof.listen(ItemExpireEvent.class, event -> {
                if (event.item() == item) {
                    fired.incrementAndGet();
                    event.setExtraLife(100);
                }
            });
            Field age = ItemEntity.class.getDeclaredField("age");
            age.setAccessible(true);
            age.setInt(item, 5999);
            item.tick();
            int after = age.getInt(item);
            proof.check(fired.get() == 1 && !item.isRemoved() && after == 5900,
                    "item expire fired=" + fired.get() + " removed=" + item.isRemoved() + " age=" + after);
        } catch (ReflectiveOperationException failure) {
            proof.check(false, "item expire " + failure);
        } finally {
            item.discard();
        }
    }

    private void checkPickupPost() {
        ItemEntity item = new ItemEntity(level, base.getX() + 0.5, base.getY() + 1, base.getZ() + 10.5,
                new ItemStack(Items.DIAMOND, 3));
        item.setNoPickUpDelay();
        level.addFreshEntity(item);
        try {
            AtomicInteger picked = new AtomicInteger(-1);
            proof.listen(ItemPickupPostEvent.class, event -> {
                if (event.item() == item && event.player() == player) {
                    picked.set(event.pickedUp());
                }
            });
            item.playerTouch(player);
            int held = player.getInventory().countItem(Items.DIAMOND);
            proof.check(picked.get() == 3 && held == 3, "pickup post picked=" + picked.get() + " held=" + held);
        } finally {
            item.discard();
            player.getInventory().clearContent();
        }
    }

    private void checkSave() {
        AtomicReference<File> directory = new AtomicReference<>();
        proof.listen(PlayerSaveToFileEvent.class, event -> {
            if (event.player() == player) {
                directory.set(event.playerDirectory());
            }
        });
        try {
            Field field = net.minecraft.server.MinecraftServer.class.getDeclaredField("playerDataStorage");
            field.setAccessible(true);
            PlayerDataStorage storage = (PlayerDataStorage) field.get(level.getServer());
            storage.save(player);
            File dir = directory.get();
            proof.check(dir != null && new File(dir, player.getStringUUID() + ".dat").isFile(),
                    "save to file dir=" + dir);
            if (dir != null) {
                new File(dir, player.getStringUUID() + ".dat").delete();
                new File(dir, player.getStringUUID() + ".dat_old").delete();
            }
        } catch (ReflectiveOperationException failure) {
            proof.check(false, "save to file " + failure);
        }
    }
}
