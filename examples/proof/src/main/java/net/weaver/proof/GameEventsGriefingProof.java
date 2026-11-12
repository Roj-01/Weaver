package net.weaver.proof;

import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.RemoveBlockGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.weaver.api.game.event.world.MobGriefingEvent;

/**
 * Mob-griefing part of {@link GameEventsProof}. Checks the three ways the
 * hooks name the griefing entity: the mob itself (item pickup in
 * {@code Mob#aiStep}), an AI goal's mob ({@code RemoveBlockGoal#canUse}), and
 * an explosion's source ({@code ServerLevel#explode}, denied, so no block
 * breaks).
 */
final class GameEventsGriefingProof {
    private final ServerLevel level;
    private final BlockPos base;
    private final GameEventsProof proof;

    GameEventsGriefingProof(ServerLevel level, BlockPos base, GameEventsProof proof) {
        this.level = level;
        this.base = base;
        this.proof = proof;
    }

    void run() {
        Mob zombie = EntityTypes.ZOMBIE.spawn(level, base.east(12), EntitySpawnReason.COMMAND);
        if (zombie == null) {
            proof.check(false, "griefing spawn zombie");
            return;
        }
        try {
            zombie.setNoAi(true);
            checkSelf(zombie);
            proof.clearListeners();
            checkGoal(zombie);
            proof.clearListeners();
            checkExplosion(zombie);
        } catch (Throwable failure) {
            proof.check(false, "griefing unexpected " + failure);
        } finally {
            proof.clearListeners();
            zombie.discard();
        }
    }

    private void checkSelf(Mob zombie) {
        AtomicInteger fired = new AtomicInteger();
        proof.listen(MobGriefingEvent.class, event -> {
            if (event.entity() == zombie) {
                fired.incrementAndGet();
            }
        });
        zombie.setCanPickUpLoot(true);
        zombie.aiStep();
        proof.check(fired.get() >= 1, "mob griefing self fired=" + fired.get());
    }

    private void checkGoal(Mob zombie) {
        AtomicInteger fired = new AtomicInteger();
        proof.listen(MobGriefingEvent.class, event -> {
            if (event.entity() == zombie) {
                fired.incrementAndGet();
                event.setCanGrief(false);
            }
        });
        boolean usable = new RemoveBlockGoal(Blocks.TURTLE_EGG, (PathfinderMob) zombie, 1.0, 3).canUse();
        proof.check(fired.get() == 1 && !usable, "mob griefing goal fired=" + fired.get() + " canUse=" + usable);
    }

    private void checkExplosion(Mob zombie) {
        AtomicInteger fired = new AtomicInteger();
        proof.listen(MobGriefingEvent.class, event -> {
            if (event.entity() == zombie) {
                fired.incrementAndGet();
                event.setCanGrief(false);
            }
        });
        BlockPos at = zombie.blockPosition();
        level.explode(zombie, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 0.5F, Level.ExplosionInteraction.MOB);
        proof.check(fired.get() >= 1, "mob griefing explosion fired=" + fired.get());
    }
}
