package net.weaver.proof;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.weaver.api.event.Event;
import net.weaver.api.event.EventBus;
import net.weaver.api.event.EventListener;
import net.weaver.api.event.ListenerHandle;
import net.weaver.api.game.event.world.EntityMountEvent;
import net.weaver.api.game.event.world.EntityStruckByLightningEvent;
import net.weaver.api.game.event.world.ExplosionStartEvent;
import net.weaver.api.game.event.living.LivingFallEvent;
import net.weaver.api.game.event.living.LivingHealEvent;
import net.weaver.api.game.event.living.LivingJumpEvent;
import net.weaver.api.game.event.living.LivingKnockBackEvent;
import net.weaver.api.game.event.living.MobEffectAddedEvent;
import net.weaver.api.game.event.living.MobEffectApplicableEvent;
import net.weaver.api.game.event.living.MobEffectRemoveEvent;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-game proof for the typed game events in {@code weaver.events.mixins.json}.
 *
 * <p>Runs once on the dedicated server. It first checks that every event mixin
 * was merged into its target class, then drives the events that need no player
 * on a spawned zombie and pig: that each event fires, that a canceled event
 * blocks the action, and that changed values are applied. Results are logged as
 * {@code GAME EVENTS PROOF CHECK} lines and one {@code GAME EVENTS PROOF RESULT}
 * line, separate from the main proof result.</p>
 */
final class GameEventsProof {
    /** Classes that {@code weaver.events.mixins.json} targets. */
    private static final List<String> TARGETS = List.of(
            "net.minecraft.world.entity.LivingEntity",
            "net.minecraft.server.level.ServerPlayer",
            "net.minecraft.world.entity.player.Player",
            "net.minecraft.world.entity.player.Inventory",
            "net.minecraft.world.entity.EntityEquipment",
            "net.minecraft.world.entity.Entity",
            "net.minecraft.world.entity.item.ItemEntity",
            "net.minecraft.world.entity.ExperienceOrb",
            "net.minecraft.world.inventory.ResultSlot",
            "net.minecraft.world.inventory.FurnaceResultSlot",
            "net.minecraft.server.level.ServerLevel",
            "net.minecraft.world.entity.TamableAnimal",
            "net.minecraft.world.entity.animal.equine.AbstractHorse",
            "net.minecraft.world.entity.LightningBolt",
            "net.minecraft.server.PlayerAdvancements",
            "net.minecraft.world.level.block.Block",
            "net.minecraft.world.level.block.FarmlandBlock",
            "net.minecraft.world.level.block.BaseFireBlock",
            "net.minecraft.world.level.block.grower.TreeGrower",
            "net.minecraft.world.level.block.MushroomBlock",
            "net.minecraft.world.level.block.NetherFungusBlock",
            "net.minecraft.world.item.BoneMealItem",
            "net.minecraft.world.level.material.FlowingFluid",
            "net.minecraft.world.level.block.CropBlock",
            "net.minecraft.world.level.block.StemBlock",
            "net.minecraft.world.level.block.BambooStalkBlock",
            "net.minecraft.world.level.block.CactusBlock",
            "net.minecraft.world.level.block.SugarCaneBlock",
            "net.minecraft.world.level.block.CocoaBlock",
            "net.minecraft.world.level.block.NetherWartBlock",
            "net.minecraft.world.level.block.SweetBerryBushBlock",
            "net.minecraft.world.level.block.GrowingPlantHeadBlock",
            "net.minecraft.world.level.block.ChorusFlowerBlock",
            "net.minecraft.world.level.block.NoteBlock",
            "net.minecraft.world.level.block.piston.PistonBaseBlock",
            "net.minecraft.world.level.block.entity.BrewingStandBlockEntity",
            "net.minecraft.world.level.ServerExplosion",
            "net.minecraft.world.entity.Mob",
            "net.minecraft.world.entity.monster.cubemob.AbstractCubeMob",
            "net.minecraft.world.entity.monster.Enderman",
            "net.minecraft.world.entity.projectile.Projectile",
            "net.minecraft.world.item.BowItem",
            "net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl",
            "net.minecraft.world.entity.boss.wither.WitherBoss",
            "net.minecraft.stats.StatsCounter",
            "net.minecraft.server.players.PlayerList",
            "net.minecraft.server.level.ServerEntity",
            "net.minecraft.world.level.storage.PlayerDataStorage",
            "net.minecraft.server.network.config.PrepareSpawnTask$Ready",
            "net.minecraft.world.level.levelgen.PhantomSpawner",
            "net.minecraft.world.inventory.AnvilMenu",
            "net.minecraft.world.inventory.SmithingMenu",
            "net.minecraft.world.inventory.GrindstoneMenu",
            "net.minecraft.world.inventory.GrindstoneMenu$4",
            "net.minecraft.world.inventory.BrewingStandMenu$PotionSlot",
            "net.minecraft.world.inventory.AbstractContainerMenu",
            "net.minecraft.world.entity.npc.villager.AbstractVillager",
            "net.minecraft.world.level.block.state.BlockBehaviour$BlockStateBase",
            "net.minecraft.world.item.ItemStack",
            "net.minecraft.server.network.ServerGamePacketListenerImpl",
            "net.minecraft.world.level.chunk.storage.SerializableChunkData",
            "net.minecraft.server.level.ChunkMap",
            "net.minecraft.server.network.PlayerChunkSender",
            "net.minecraft.server.network.ServerCommonPacketListenerImpl",
            "net.minecraft.world.inventory.EnchantmentMenu",
            "net.minecraft.server.MinecraftServer",
            "net.minecraft.world.level.NaturalSpawner",
            "net.minecraft.server.ReloadableServerResources",
            "net.minecraft.world.entity.ai.village.VillageSiege",
            "net.minecraft.server.dedicated.DedicatedServer",
            "net.minecraft.world.item.CreativeModeTab",
            "net.minecraft.server.packs.repository.BuiltInPackSource",
            "net.minecraft.server.packs.repository.PackRepository",
            "net.minecraft.world.entity.animal.golem.SnowGolem",
            "net.minecraft.world.entity.monster.Ravager",
            "net.minecraft.world.entity.animal.allay.Allay",
            "net.minecraft.world.entity.monster.piglin.Piglin",
            "net.minecraft.world.entity.boss.enderdragon.EnderDragon",
            "net.minecraft.world.entity.monster.cubemob.SulfurCube",
            "net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball",
            "net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball",
            "net.minecraft.world.entity.ai.goal.BreakDoorGoal",
            "net.minecraft.world.entity.ai.goal.RemoveBlockGoal",
            "net.minecraft.world.entity.animal.rabbit.Rabbit$RaidGardenGoal",
            "net.minecraft.world.entity.monster.Enderman$EndermanLeaveBlockGoal",
            "net.minecraft.world.entity.monster.Enderman$EndermanTakeBlockGoal",
            "net.minecraft.world.entity.monster.Silverfish$SilverfishMergeWithStoneGoal",
            "net.minecraft.world.entity.monster.illager.Evoker$EvokerWololoSpellGoal",
            "net.minecraft.world.entity.ai.goal.EatBlockGoal",
            "net.minecraft.world.entity.monster.Silverfish$SilverfishWakeUpFriendsGoal",
            "net.minecraft.world.entity.animal.fox.Fox$FoxEatBerriesGoal",
            "net.minecraft.world.effect.WeavingMobEffect",
            "net.minecraft.world.entity.ai.behavior.HarvestFarmland",
            "net.minecraft.world.entity.animal.goat.GoatAi",
            "net.minecraft.world.entity.animal.nautilus.NautilusAi",
            "net.minecraft.world.entity.decoration.ArmorStand",
            "net.minecraft.world.entity.decoration.BlockAttachedEntity",
            "net.minecraft.world.entity.vehicle.VehicleEntity",
            "net.minecraft.world.level.block.PitcherCropBlock",
            "net.minecraft.world.level.block.PowderSnowBlock",
            "net.minecraft.world.level.block.TurtleEggBlock",
            "net.minecraft.world.level.levelgen.feature.treedecorators.AlterGroundDecorator",
            "net.minecraft.world.level.storage.loot.functions.EnchantedCountIncreaseFunction",
            "net.minecraft.world.level.storage.loot.functions.ApplyBonusCount",
            "net.minecraft.world.level.material.LavaFluid",
            "net.minecraft.world.level.block.LiquidBlock",
            "net.minecraft.server.commands.SpreadPlayersCommand",
            "net.minecraft.world.item.enchantment.EnchantmentHelper",
            "net.minecraft.world.entity.monster.Monster",
            "net.minecraft.world.level.entity.PersistentEntitySectionManager$Callback",
            "net.minecraft.world.level.entity.TransientEntitySectionManager$Callback",
            "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity",
            "net.minecraft.world.level.chunk.ChunkGenerator",
            "net.minecraft.world.level.levelgen.structure.StructureStart",
            "net.minecraft.world.level.Level");

    private static final AtomicBoolean RAN = new AtomicBoolean();

    private final List<String> failures = new ArrayList<>();
    private final List<ListenerHandle> handles = new ArrayList<>();
    private int passed;

    private GameEventsProof() {
    }

    static void runOnce(ServerLevel level, BlockPos position) {
        if (RAN.compareAndSet(false, true)) {
            new GameEventsProof().run(level, position);
        }
    }

    private void run(ServerLevel level, BlockPos position) {
        try {
            checkMixinsApplied(level);
            LivingEntity zombie = EntityTypes.ZOMBIE.spawn(level, position, EntitySpawnReason.COMMAND);
            Pig pig = EntityTypes.PIG.spawn(level, position, EntitySpawnReason.COMMAND);
            if (zombie == null || pig == null) {
                check(false, "spawn proof entities");
            } else {
                try {
                    checkHeal(zombie);
                    clearListeners();
                    checkKnockBack(zombie, level);
                    clearListeners();
                    checkFall(zombie, level);
                    clearListeners();
                    checkJump(zombie);
                    clearListeners();
                    checkEffects(zombie);
                    clearListeners();
                    checkMount(zombie, pig);
                    clearListeners();
                    checkLightning(pig, level);
                    clearListeners();
                    checkExplosion(level, position);
                    clearListeners();
                } finally {
                    zombie.discard();
                    pig.discard();
                }
            }
            new GameEventsBlockProof(level, position.above(80), this).run();
            new GameEventsCombatProof(level, position.above(100), this).run();
            new GameEventsPlayerProof(level, position.above(110), this).run();
            new GameEventsWorldProof(level, position.above(110), this).run();
            new GameEventsGriefingProof(level, position.above(100), this).run();
            new GameEventsAttachmentsProof(level, position.above(120), this).run();
            new GameEventsLookupProof(level, position.above(125), this).run();
            new GameEventsModifiersProof(level, position.above(125), this).run();
            new GameEventsExtrasProof(level, position.above(130), this).run();
            new GameplayGapsProof(level, position.above(135), this).run();
            new ExtensionsProof(level, position.above(100).north(40), this).run();
            new NfProof(level, position.above(140), this).run();
        } catch (Throwable failure) {
            check(false, "unexpected " + failure);
        } finally {
            clearListeners();
        }
        boolean ok = failures.isEmpty();
        System.out.println("GAME EVENTS PROOF RESULT ok=" + ok + " checks=" + passed
                + (ok ? "" : " failed=" + String.join("|", failures)));
    }

    private void checkMixinsApplied(ServerLevel level) throws ClassNotFoundException {
        ClassLoader loader = level.getClass().getClassLoader();
        for (String target : TARGETS) {
            Class<?> type = Class.forName(target, false, loader);
            boolean merged = false;
            for (Method method : type.getDeclaredMethods()) {
                if (method.getName().contains("weaver$")) {
                    merged = true;
                    break;
                }
            }
            check(merged, "mixin applied " + type.getSimpleName());
        }
    }

    private void checkHeal(LivingEntity zombie) {
        zombie.setHealth(10.0F);
        AtomicBoolean cancel = new AtomicBoolean();
        listen(LivingHealEvent.class, event -> {
            if (event.entity() == zombie) {
                if (cancel.get()) {
                    event.setCanceled(true);
                } else {
                    event.setAmount(3.0F);
                }
            }
        });
        zombie.heal(1.0F);
        check(zombie.getHealth() == 13.0F, "heal amount changed health=" + zombie.getHealth());
        cancel.set(true);
        zombie.heal(5.0F);
        check(zombie.getHealth() == 13.0F, "heal canceled health=" + zombie.getHealth());
    }

    private void checkKnockBack(LivingEntity zombie, ServerLevel level) {
        DamageSource source = level.damageSources().generic();
        AtomicBoolean cancel = new AtomicBoolean(true);
        listen(LivingKnockBackEvent.class, event -> {
            if (event.entity() == zombie && cancel.get()) {
                event.setCanceled(true);
            }
        });
        zombie.setDeltaMovement(Vec3.ZERO);
        zombie.knockback(1.0, 1.0, 0.0, source, 1.0F, false);
        check(zombie.getDeltaMovement().equals(Vec3.ZERO), "knockback canceled");
        cancel.set(false);
        zombie.knockback(1.0, 1.0, 0.0, source, 1.0F, false);
        check(!zombie.getDeltaMovement().equals(Vec3.ZERO), "knockback applied");
        zombie.setDeltaMovement(Vec3.ZERO);
    }

    private void checkFall(LivingEntity zombie, ServerLevel level) {
        DamageSource source = level.damageSources().fall();
        AtomicInteger fired = new AtomicInteger();
        listen(LivingFallEvent.class, event -> {
            if (event.entity() == zombie) {
                fired.incrementAndGet();
                event.setCanceled(true);
            }
        });
        zombie.setHealth(20.0F);
        boolean damaged = zombie.causeFallDamage(30.0, 1.0F, source);
        check(fired.get() == 1 && !damaged && zombie.getHealth() == 20.0F,
                "fall canceled fired=" + fired.get() + " health=" + zombie.getHealth());
    }

    private void checkJump(LivingEntity zombie) {
        AtomicInteger fired = new AtomicInteger();
        listen(LivingJumpEvent.class, event -> {
            if (event.entity() == zombie) {
                fired.incrementAndGet();
            }
        });
        zombie.jumpFromGround();
        check(fired.get() == 1, "jump fired=" + fired.get());
        zombie.setDeltaMovement(Vec3.ZERO);
    }

    private void checkEffects(LivingEntity zombie) {
        AtomicBoolean block = new AtomicBoolean(true);
        AtomicInteger added = new AtomicInteger();
        AtomicBoolean keep = new AtomicBoolean(true);
        listen(MobEffectApplicableEvent.class, event -> {
            if (event.entity() == zombie && block.get()) {
                event.setCanceled(true);
            }
        });
        listen(MobEffectAddedEvent.class, event -> {
            if (event.entity() == zombie) {
                added.incrementAndGet();
            }
        });
        listen(MobEffectRemoveEvent.class, event -> {
            if (event.entity() == zombie && keep.get()) {
                event.setCanceled(true);
            }
        });
        boolean blocked = !zombie.addEffect(new MobEffectInstance(MobEffects.SPEED, 200));
        check(blocked && !zombie.hasEffect(MobEffects.SPEED) && added.get() == 0, "effect blocked");
        block.set(false);
        boolean applied = zombie.addEffect(new MobEffectInstance(MobEffects.SPEED, 200));
        check(applied && zombie.hasEffect(MobEffects.SPEED) && added.get() == 1,
                "effect added events=" + added.get());
        check(!zombie.removeEffect(MobEffects.SPEED) && zombie.hasEffect(MobEffects.SPEED),
                "effect removal canceled");
        keep.set(false);
        check(zombie.removeEffect(MobEffects.SPEED) && !zombie.hasEffect(MobEffects.SPEED),
                "effect removed");
    }

    private void checkMount(LivingEntity zombie, Pig pig) {
        AtomicBoolean cancel = new AtomicBoolean(true);
        listen(EntityMountEvent.class, event -> {
            if (event.rider() == zombie && cancel.get()) {
                event.setCanceled(true);
            }
        });
        check(!zombie.startRiding(pig, true, false) && !zombie.isPassenger(), "mount canceled");
        cancel.set(false);
        check(zombie.startRiding(pig, true, false) && zombie.isPassenger(), "mount allowed");
        cancel.set(true);
        zombie.stopRiding();
        check(zombie.isPassenger(), "dismount canceled");
        cancel.set(false);
        zombie.stopRiding();
        check(!zombie.isPassenger(), "dismount allowed");
    }

    private void checkLightning(Pig pig, ServerLevel level) {
        LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.COMMAND);
        if (bolt == null) {
            check(false, "lightning bolt created");
            return;
        }
        AtomicInteger fired = new AtomicInteger();
        listen(EntityStruckByLightningEvent.class, event -> {
            // Cancel every strike so the proof never converts or burns anything.
            event.setCanceled(true);
            if (event.entity() == pig) {
                fired.incrementAndGet();
            }
        });
        // High in the air, so the bolt cannot place fire in the test world.
        Vec3 sky = pig.position().add(0.0, 60.0, 0.0);
        pig.setPos(sky.x, sky.y, sky.z);
        bolt.setPos(sky.x, sky.y, sky.z);
        bolt.tick();
        check(fired.get() == 1 && !pig.isRemoved() && pig.isAlive(),
                "lightning strike canceled fired=" + fired.get());
        bolt.discard();
    }

    private void checkExplosion(ServerLevel level, BlockPos position) {
        AtomicInteger fired = new AtomicInteger();
        listen(ExplosionStartEvent.class, event -> {
            if (event.level() == level) {
                fired.incrementAndGet();
                event.setCanceled(true);
            }
        });
        level.explode(null, position.getX() + 0.5, position.getY(), position.getZ() + 0.5, 2.0F,
                Level.ExplosionInteraction.NONE);
        check(fired.get() == 1, "explosion start canceled fired=" + fired.get());
    }

    <T extends Event> void listen(Class<T> type, EventListener<? super T> listener) {
        handles.add(EventBus.GAME.addListener(type, listener));
    }

    /** Unregisters every listener added so far, so one check cannot affect the next. */
    void clearListeners() {
        handles.forEach(ListenerHandle::unregister);
        handles.clear();
    }

    void check(boolean ok, String name) {
        System.out.println("GAME EVENTS PROOF CHECK ok=" + ok + " " + name);
        if (ok) {
            passed++;
        } else {
            failures.add(name);
        }
    }
}
