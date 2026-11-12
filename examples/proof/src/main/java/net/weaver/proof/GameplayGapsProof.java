package net.weaver.proof;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.weaver.api.game.entity.EntityAttributes;
import net.weaver.api.game.entity.MultipartEntity;
import net.weaver.api.game.entity.PartEntity;
import net.weaver.api.game.entity.SpawnPlacementRules;
import net.weaver.api.game.event.living.FinalizeSpawnEvent;
import net.weaver.api.game.event.living.LivingIncomingDamageEvent;

/**
 * Entity attributes and spawn rules for any type, incoming damage changes,
 * cancelled spawns, and multipart entities, on a real server level.
 */
final class GameplayGapsProof {
    private final ServerLevel level;
    private final BlockPos base;
    private final GameEventsProof proof;

    GameplayGapsProof(ServerLevel level, BlockPos base, GameEventsProof proof) {
        this.level = level;
        this.base = base;
        this.proof = proof;
    }

    void run() {
        guard("entity attributes", this::checkAttributes);
        guard("spawn checks", this::checkSpawnRules);
        guard("incoming damage", this::checkIncomingDamage);
        guard("spawn cancelled", this::checkSpawnCancelled);
        guard("multipart", this::checkMultipart);
    }

    private static final AtomicBoolean ATTRIBUTES_ADDED = new AtomicBoolean();
    private static final AtomicBoolean RULE_ADDED = new AtomicBoolean();

    /** Pigs gain an attack damage attribute they do not have in vanilla. */
    private void checkAttributes() {
        if (ATTRIBUTES_ADDED.compareAndSet(false, true)) {
            EntityAttributes.add(EntityTypes.PIG, Attributes.ATTACK_DAMAGE, 3.0);
        }
        Pig pig = spawnPig(base.above(30));
        try {
            boolean has = pig.getAttribute(Attributes.ATTACK_DAMAGE) != null
                    && pig.getAttributeValue(Attributes.ATTACK_DAMAGE) == 3.0;
            boolean kept = pig.getAttributeValue(Attributes.MAX_HEALTH) == 10.0;
            proof.check(has && kept, "entity attributes added=" + has + " vanillaKept=" + kept);
        } finally {
            pig.discard();
        }
    }

    /**
     * An added condition decides for a type without a vanilla rule (armor stands): it is
     * asked once per check, and its answer is the result.
     */
    private void checkSpawnRules() {
        if (RULE_ADDED.compareAndSet(false, true)) {
            SpawnPlacementRules.addCheck(EntityTypes.ARMOR_STAND, (type, world, reason, pos, random) -> {
                RULE_CALLS.incrementAndGet();
                return pos.getY() < 0;
            });
        }
        RandomSource random = RandomSource.create(1);
        int before = RULE_CALLS.get();
        boolean refused = !SpawnPlacements.checkSpawnRules(EntityTypes.ARMOR_STAND, level, EntitySpawnReason.SPAWNER,
                base.above(2), random);
        boolean allowed = SpawnPlacements.checkSpawnRules(EntityTypes.ARMOR_STAND, level, EntitySpawnReason.SPAWNER,
                new BlockPos(base.getX(), -10, base.getZ()), random);
        int asked = RULE_CALLS.get() - before;
        proof.check(refused && allowed && asked == 2, "spawn checks refused=" + refused + " allowed=" + allowed
                + " asked=" + asked);
    }

    private static final AtomicInteger RULE_CALLS = new AtomicInteger();

    /** A listener halves the incoming damage of a hit before armor. */
    private void checkIncomingDamage() {
        Pig pig = spawnPig(base.above(32));
        try {
            proof.listen(LivingIncomingDamageEvent.class, event -> {
                if (event.entity() == pig) {
                    event.setAmount(event.amount() / 2.0F);
                }
            });
            float before = pig.getHealth();
            pig.hurtServer(level, level.damageSources().generic(), 4.0F);
            float taken = before - pig.getHealth();
            proof.check(Math.abs(taken - 2.0F) < 0.01F, "incoming damage before=" + before + " taken=" + taken);
        } finally {
            pig.discard();
        }
    }

    /** A cancelled spawn never enters the level. */
    private void checkSpawnCancelled() {
        AtomicInteger fired = new AtomicInteger();
        proof.listen(FinalizeSpawnEvent.class, event -> {
            if (event.mob() instanceof Zombie) {
                fired.incrementAndGet();
                event.setSpawnCancelled(true);
            }
        });
        Zombie zombie = EntityTypes.ZOMBIE.spawn(level, base.above(34), EntitySpawnReason.COMMAND);
        boolean absent = zombie == null || level.getEntity(zombie.getUUID()) == null;
        proof.check(fired.get() == 1 && absent, "spawn cancelled fired=" + fired.get() + " absent=" + absent);
        if (zombie != null) {
            zombie.discard();
        }
    }

    /** A pig with a head part: the part is found by box and by id, hits reach the parent, and it leaves with it. */
    private void checkMultipart() {
        ProofSerpent serpent = new ProofSerpent(EntityTypes.PIG, level);
        BlockPos at = base.above(36);
        serpent.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        level.addFreshEntity(serpent);
        try {
            ProofSerpent.Head head = serpent.head;
            head.setPos(at.getX() + 0.5, at.getY() + 2, at.getZ() + 0.5);
            boolean byId = level.getEntityOrPart(head.getId()) == head;
            List<Entity> found = level.getEntities((Entity) null, head.getBoundingBox().inflate(0.1), entity -> true);
            boolean byBox = found.contains(head);
            boolean hit = head.hurtServer(level, level.damageSources().generic(), 3.0F) && serpent.partHits == 1;
            serpent.discard();
            boolean gone = level.getEntityOrPart(head.getId()) == null;
            proof.check(byId && byBox && hit && gone, "multipart byId=" + byId + " byBox=" + byBox + " hit=" + hit
                    + " gone=" + gone);
        } finally {
            if (!serpent.isRemoved()) {
                serpent.discard();
            }
        }
    }

    private Pig spawnPig(BlockPos pos) {
        Pig pig = EntityTypes.PIG.spawn(level, pos, EntitySpawnReason.COMMAND);
        if (pig == null) {
            throw new IllegalStateException("pig did not spawn");
        }
        return pig;
    }

    private void guard(String name, Runnable check) {
        try {
            check.run();
        } catch (Throwable failure) {
            proof.check(false, name + " unexpected " + failure);
        } finally {
            proof.clearListeners();
        }
    }

    /** A pig with one extra hitbox above it. */
    static final class ProofSerpent extends Pig implements MultipartEntity {
        final Head head;
        int partHits;

        ProofSerpent(EntityType<? extends Pig> type, net.minecraft.world.level.Level level) {
            super(type, level);
            head = new Head(this);
        }

        @Override
        public PartEntity<?>[] getParts() {
            return new PartEntity<?>[] {head};
        }

        @Override
        public boolean hurtFromPart(PartEntity<?> part, ServerLevel level, DamageSource source, float amount) {
            partHits++;
            return hurtServer(level, source, amount);
        }

        static final class Head extends PartEntity<ProofSerpent> {
            Head(ProofSerpent parent) {
                super(parent, "head", 0.8F, 0.8F);
            }
        }
    }
}
