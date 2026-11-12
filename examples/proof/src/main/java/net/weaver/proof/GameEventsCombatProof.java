package net.weaver.proof;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.weaver.api.game.event.living.LivingChangeTargetEvent;
import net.weaver.api.game.event.living.LivingConversionPostEvent;
import net.weaver.api.game.event.living.LivingConversionPreEvent;
import net.weaver.api.game.event.living.LivingDamagePostEvent;
import net.weaver.api.game.event.living.LivingDamagePreEvent;
import net.weaver.api.game.event.living.LivingDeathEvent;
import net.weaver.api.game.event.living.LivingDropsEvent;
import net.weaver.api.game.event.living.LivingUseItemStartEvent;
import net.weaver.api.game.event.living.LivingUseTotemEvent;
import net.weaver.api.game.event.living.MobDespawnEvent;
import net.weaver.api.game.event.living.MobEffectExpiredEvent;
import net.weaver.api.game.event.living.MobSplitEvent;
import net.weaver.api.game.event.player.ArrowLooseEvent;
import net.weaver.api.game.event.world.EntityInvulnerabilityCheckEvent;
import net.weaver.api.game.event.world.EntityTeleportEvent;
import net.weaver.api.game.event.world.EntityTravelToDimensionEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Batch 3 (entities and combat) part of {@link GameEventsProof}. Every check
 * spawns fresh mobs high above spawn and discards them afterwards; events that
 * need a real player are covered by the mixin-applied check only.
 */
final class GameEventsCombatProof {
    private final ServerLevel level;
    private final BlockPos base;
    private final GameEventsProof proof;
    private final List<Entity> spawned = new ArrayList<>();

    GameEventsCombatProof(ServerLevel level, BlockPos base, GameEventsProof proof) {
        this.level = level;
        this.base = base;
        this.proof = proof;
    }

    void run() {
        try {
            checkAreaVisible();
            proof.clearListeners();
            checkDamage();
            proof.clearListeners();
            checkInvulnerability();
            proof.clearListeners();
            checkTotem();
            proof.clearListeners();
            checkDrops();
            proof.clearListeners();
            checkDeathCanceled();
            proof.clearListeners();
            checkItemUse();
            proof.clearListeners();
            checkTarget();
            proof.clearListeners();
            checkDespawn();
            proof.clearListeners();
            checkEffectExpiry();
            proof.clearListeners();
            checkConversion();
            proof.clearListeners();
            checkSplit();
            proof.clearListeners();
            checkBowRelease();
            proof.clearListeners();
            checkRandomTeleport();
            proof.clearListeners();
            checkDimensionTravel();
            proof.clearListeners();
            checkEquipmentChange();
            proof.clearListeners();
            checkBreathe();
            proof.clearListeners();
            checkFinalizeSpawn();
            proof.clearListeners();
        } finally {
            proof.clearListeners();
            for (Entity entity : spawned) {
                entity.discard();
            }
            AABB area = new AABB(base).inflate(40.0);
            for (Entity entity : level.getEntitiesOfClass(Entity.class, area,
                    e -> e instanceof ItemEntity || e instanceof Slime)) {
                entity.discard();
            }
        }
    }

    // World queries (getEntitiesOfClass, getEntity) only see entities in chunks that are
    // fully loaded; checks that look for drops or children must run in such a chunk.
    private void checkAreaVisible() {
        Mob probe = spawn(EntityTypes.ZOMBIE, 0);
        proof.check(probe != null && level.getEntity(probe.getId()) == probe, "combat proof area entity-visible");
        if (probe != null) {
            probe.discard();
        }
    }

    private <T extends Mob> T spawn(EntityType<T> type, int offset) {
        T mob = type.spawn(level, base.east(offset), EntitySpawnReason.COMMAND);
        if (mob != null) {
            mob.setNoAi(true);
            if (mob instanceof net.minecraft.world.entity.monster.zombie.Zombie zombie) {
                zombie.setBaby(false);
            }
            spawned.add(mob);
        }
        return mob;
    }

    private void checkDamage() {
        Mob zombie = spawn(EntityTypes.ZOMBIE, 0);
        AtomicInteger post = new AtomicInteger();
        AtomicBoolean cancel = new AtomicBoolean();
        proof.listen(LivingDamagePreEvent.class, event -> {
            if (event.entity() == zombie) {
                if (cancel.get()) {
                    event.setCanceled(true);
                } else {
                    event.setDamage(1.0F);
                }
            }
        });
        proof.listen(LivingDamagePostEvent.class, event -> {
            if (event.entity() == zombie && event.damage() == 1.0F) {
                post.incrementAndGet();
            }
        });
        zombie.setHealth(20.0F);
        zombie.hurtServer(level, level.damageSources().generic(), 6.0F);
        proof.check(zombie.getHealth() == 19.0F && post.get() == 1,
                "damage pre changed health=" + zombie.getHealth() + " post=" + post.get());
        cancel.set(true);
        Mob fresh = spawn(EntityTypes.ZOMBIE, 1);
        proof.listen(LivingDamagePreEvent.class, event -> {
            if (event.entity() == fresh) {
                event.setCanceled(true);
            }
        });
        fresh.setHealth(20.0F);
        fresh.hurtServer(level, level.damageSources().generic(), 6.0F);
        proof.check(fresh.getHealth() == 20.0F, "damage pre canceled health=" + fresh.getHealth());
    }

    private void checkInvulnerability() {
        Mob zombie = spawn(EntityTypes.ZOMBIE, 2);
        proof.listen(EntityInvulnerabilityCheckEvent.class, event -> {
            if (event.entity() == zombie) {
                event.setInvulnerable(true);
            }
        });
        zombie.setHealth(20.0F);
        boolean hurt = zombie.hurtServer(level, level.damageSources().generic(), 6.0F);
        proof.check(!hurt && zombie.getHealth() == 20.0F, "invulnerability granted");
    }

    private void checkTotem() {
        Mob saved = spawn(EntityTypes.ZOMBIE, 4);
        Mob doomed = spawn(EntityTypes.ZOMBIE, 6);
        saved.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        doomed.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        proof.listen(LivingUseTotemEvent.class, event -> {
            if (event.entity() == doomed) {
                event.setCanceled(true);
            }
        });
        saved.hurtServer(level, level.damageSources().generic(), 100.0F);
        doomed.hurtServer(level, level.damageSources().generic(), 100.0F);
        proof.check(saved.isAlive() && !doomed.isAlive(), "totem allowed and canceled saved="
                + saved.isAlive() + " doomed=" + doomed.isAlive());
    }

    private void checkDrops() {
        Mob zombie = spawn(EntityTypes.ZOMBIE, 8);
        AtomicInteger fired = new AtomicInteger();
        AtomicInteger original = new AtomicInteger();
        List<ItemEntity> added = new ArrayList<>();
        proof.listen(LivingDropsEvent.class, event -> {
            if (event.entity() == zombie) {
                fired.incrementAndGet();
                original.set(event.drops().size());
                event.drops().clear();
                ItemEntity diamond = new ItemEntity(level, zombie.getX(), zombie.getY(), zombie.getZ(),
                        new ItemStack(Items.DIAMOND));
                added.add(diamond);
                event.drops().add(diamond);
            }
        });
        AABB around = zombie.getBoundingBox().inflate(3.0);
        List<ItemEntity> before = level.getEntitiesOfClass(ItemEntity.class, around);
        zombie.kill(level);
        List<ItemEntity> items = new ArrayList<>(level.getEntitiesOfClass(ItemEntity.class, around));
        items.removeAll(before);
        boolean onlyDiamond = items.size() == 1 && items.get(0).getItem().is(Items.DIAMOND);
        proof.check(fired.get() == 1 && onlyDiamond, "death drops replaced fired=" + fired.get()
                + " items=" + items.size() + " original=" + original.get()
                + (added.isEmpty() ? "" : " diamondRemoved=" + added.get(0).isRemoved()
                        + " inLevel=" + (level.getEntity(added.get(0).getId()) != null)
                        + " at=" + added.get(0).blockPosition() + " zombieAt=" + zombie.blockPosition()));
    }

    private void checkDeathCanceled() {
        Mob zombie = spawn(EntityTypes.ZOMBIE, 22);
        AtomicInteger fired = new AtomicInteger();
        proof.listen(LivingDeathEvent.class, event -> {
            if (event.entity() == zombie) {
                fired.incrementAndGet();
                event.setCanceled(true);
                zombie.setHealth(5.0F);
            }
        });
        zombie.hurtServer(level, level.damageSources().generic(), 100.0F);
        proof.check(fired.get() == 1 && zombie.isAlive() && zombie.getHealth() == 5.0F && zombie.deathTime == 0,
                "death canceled fired=" + fired.get() + " alive=" + zombie.isAlive() + " health=" + zombie.getHealth());
    }

    private void checkItemUse() {
        Mob zombie = spawn(EntityTypes.ZOMBIE, 10);
        zombie.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.APPLE));
        AtomicBoolean cancel = new AtomicBoolean(true);
        proof.listen(LivingUseItemStartEvent.class, event -> {
            if (event.entity() == zombie) {
                if (cancel.get()) {
                    event.setCanceled(true);
                } else {
                    event.setDuration(5);
                }
            }
        });
        zombie.startUsingItem(InteractionHand.MAIN_HAND);
        proof.check(!zombie.isUsingItem(), "item use start canceled");
        cancel.set(false);
        zombie.startUsingItem(InteractionHand.MAIN_HAND);
        proof.check(zombie.isUsingItem() && zombie.getUseItemRemainingTicks() == 5,
                "item use duration changed remaining=" + zombie.getUseItemRemainingTicks());
        zombie.stopUsingItem();
    }

    private void checkTarget() {
        Mob zombie = spawn(EntityTypes.ZOMBIE, 12);
        Mob pig = spawn(EntityTypes.PIG, 13);
        Mob other = spawn(EntityTypes.PIG, 14);
        AtomicBoolean cancel = new AtomicBoolean(true);
        proof.listen(LivingChangeTargetEvent.class, event -> {
            if (event.mob() == zombie && event.newTarget() == pig) {
                if (cancel.get()) {
                    event.setCanceled(true);
                } else {
                    event.setNewTarget(other);
                }
            }
        });
        zombie.setTarget(pig);
        proof.check(zombie.getTarget() == null, "target change canceled");
        cancel.set(false);
        zombie.setTarget(pig);
        proof.check(zombie.getTarget() == other, "target redirected");
        zombie.setTarget(null);
    }

    private void checkDespawn() {
        Mob kept = spawn(EntityTypes.ZOMBIE, 16);
        Mob removed = spawn(EntityTypes.ZOMBIE, 17);
        proof.listen(MobDespawnEvent.class, event -> {
            if (event.mob() == kept) {
                event.setDecision(MobDespawnEvent.Decision.DENY);
            } else if (event.mob() == removed) {
                event.setDecision(MobDespawnEvent.Decision.ALLOW);
            }
        });
        kept.checkDespawn();
        removed.checkDespawn();
        proof.check(!kept.isRemoved() && removed.isRemoved(), "despawn decided kept="
                + !kept.isRemoved() + " removed=" + removed.isRemoved());
    }

    private void checkEffectExpiry() {
        Mob zombie = spawn(EntityTypes.ZOMBIE, 18);
        AtomicInteger expired = new AtomicInteger();
        proof.listen(MobEffectExpiredEvent.class, event -> {
            if (event.entity() == zombie) {
                expired.incrementAndGet();
            }
        });
        zombie.addEffect(new MobEffectInstance(MobEffects.SPEED, 1));
        for (int i = 0; i < 5 && expired.get() == 0; i++) {
            zombie.baseTick();
        }
        proof.check(expired.get() == 1 && !zombie.hasEffect(MobEffects.SPEED), "effect expired fired=" + expired.get());
    }

    private void checkConversion() {
        Mob zombie = spawn(EntityTypes.ZOMBIE, 20);
        AtomicBoolean cancel = new AtomicBoolean(true);
        AtomicInteger post = new AtomicInteger();
        proof.listen(LivingConversionPreEvent.class, event -> {
            if (event.mob() == zombie && cancel.get()) {
                event.setCanceled(true);
            }
        });
        proof.listen(LivingConversionPostEvent.class, event -> {
            if (event.original() == zombie) {
                post.incrementAndGet();
            }
        });
        Mob blocked = zombie.convertTo(EntityTypes.SKELETON, ConversionParams.single(zombie, false, false), m -> {
        });
        proof.check(blocked == null && zombie.isAlive(), "conversion canceled");
        cancel.set(false);
        Mob converted = zombie.convertTo(EntityTypes.SKELETON, ConversionParams.single(zombie, false, false), m -> {
        });
        if (converted != null) {
            spawned.add(converted);
        }
        proof.check(converted != null && post.get() == 1, "conversion post fired=" + post.get());
    }

    private void checkSplit() {
        Slime slime = EntityTypes.SLIME.spawn(level, base.east(24), EntitySpawnReason.COMMAND);
        if (slime == null) {
            proof.check(false, "slime spawned");
            return;
        }
        spawned.add(slime);
        slime.setSize(2, true);
        AtomicInteger fired = new AtomicInteger();
        proof.listen(MobSplitEvent.class, event -> {
            if (event.parent() == slime) {
                fired.incrementAndGet();
                event.setCanceled(true);
            }
        });
        AABB area = slime.getBoundingBox().inflate(4.0);
        slime.setHealth(0.0F);
        slime.remove(Entity.RemovalReason.KILLED);
        List<Slime> children = level.getEntitiesOfClass(Slime.class, area, s -> s != slime && !s.isRemoved());
        proof.check(fired.get() == 1 && children.isEmpty(), "slime split canceled fired=" + fired.get()
                + " children=" + children.size());
    }

    private void checkBowRelease() {
        Skeleton skeleton = spawn(EntityTypes.SKELETON, 26);
        ItemStack bow = new ItemStack(Items.BOW);
        AtomicInteger fired = new AtomicInteger();
        proof.listen(ArrowLooseEvent.class, event -> {
            if (event.shooter() == skeleton) {
                fired.incrementAndGet();
                event.setCanceled(true);
            }
        });
        boolean shot = ((net.minecraft.world.item.BowItem) Items.BOW).releaseUsing(bow, level, skeleton, 0);
        proof.check(fired.get() == 1 && !shot, "bow release canceled fired=" + fired.get());
    }

    private void checkRandomTeleport() {
        Mob zombie = spawn(EntityTypes.ZOMBIE, 28);
        AtomicInteger fired = new AtomicInteger();
        proof.listen(EntityTeleportEvent.class, event -> {
            if (event.entity() == zombie) {
                fired.incrementAndGet();
                event.setCanceled(true);
            }
        });
        Vec3 before = zombie.position();
        boolean moved = zombie.randomTeleport(before.x + 3.0, before.y, before.z, false, state -> true);
        proof.check(fired.get() == 1 && !moved && zombie.position().equals(before),
                "random teleport canceled fired=" + fired.get());
    }

    private void checkDimensionTravel() {
        ServerLevel nether = level.getServer().getLevel(Level.NETHER);
        if (nether == null) {
            proof.check(true, "dimension travel skipped reason=no-nether");
            return;
        }
        Mob zombie = spawn(EntityTypes.ZOMBIE, 30);
        AtomicInteger fired = new AtomicInteger();
        proof.listen(EntityTravelToDimensionEvent.class, event -> {
            if (event.entity() == zombie) {
                fired.incrementAndGet();
                event.setCanceled(true);
            }
        });
        Entity result = zombie.teleport(new TeleportTransition(nether, new Vec3(0.5, 100.0, 0.5), Vec3.ZERO, 0.0F,
                0.0F, TeleportTransition.DO_NOTHING));
        proof.check(fired.get() == 1 && result == null && zombie.level() == level && !zombie.isRemoved(),
                "dimension travel canceled fired=" + fired.get());
    }

    private void checkEquipmentChange() {
        Mob zombie = spawn(EntityTypes.ZOMBIE, 32);
        AtomicInteger fired = new AtomicInteger();
        proof.listen(net.weaver.api.game.event.living.LivingEquipmentChangeEvent.class, event -> {
            if (event.entity() == zombie && event.slot() == net.minecraft.world.entity.EquipmentSlot.HEAD
                    && event.to().is(Items.IRON_HELMET)) {
                fired.incrementAndGet();
            }
        });
        zombie.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        zombie.tick();
        proof.check(fired.get() == 1, "equipment change fired=" + fired.get());
    }


    private void checkBreathe() {
        Mob zombie = spawn(EntityTypes.ZOMBIE, 36);
        AtomicInteger fired = new AtomicInteger();
        proof.listen(net.weaver.api.game.event.living.LivingBreatheEvent.class, event -> {
            if (event.entity() == zombie) {
                fired.incrementAndGet();
            }
        });
        zombie.baseTick();
        proof.check(fired.get() == 1, "breathe check fired=" + fired.get());
    }

    private void checkFinalizeSpawn() {
        AtomicInteger fired = new AtomicInteger();
        proof.listen(net.weaver.api.game.event.living.FinalizeSpawnEvent.class, event -> {
            if (event.reason() == EntitySpawnReason.COMMAND) {
                fired.incrementAndGet();
            }
        });
        Mob zombie = spawn(EntityTypes.ZOMBIE, 38);
        proof.check(zombie != null && fired.get() == 1, "finalize spawn fired=" + fired.get());
    }
}
