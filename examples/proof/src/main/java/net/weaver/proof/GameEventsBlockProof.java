package net.weaver.proof;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.weaver.api.game.event.block.BlockDropsEvent;
import net.weaver.api.game.event.block.BlockGrowFeatureEvent;
import net.weaver.api.game.event.block.BonemealEvent;
import net.weaver.api.game.event.block.CropGrowPostEvent;
import net.weaver.api.game.event.block.CropGrowPreEvent;
import net.weaver.api.game.event.block.FarmlandTrampleEvent;
import net.weaver.api.game.event.block.NeighborNotifyEvent;
import net.weaver.api.game.event.block.PistonMovePreEvent;
import net.weaver.api.game.event.block.PortalSpawnEvent;
import net.weaver.api.game.event.world.ExplosionDetonateEvent;
import net.weaver.api.game.event.world.ExplosionKnockbackEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Batch 2 (blocks and world) part of {@link GameEventsProof}. Every check builds
 * what it needs from real blocks high above the spawn point and restores every
 * position it touched to air afterwards.
 */
final class GameEventsBlockProof {
    private final ServerLevel level;
    private final BlockPos base;
    private final GameEventsProof proof;
    private final List<BlockPos> touched = new ArrayList<>();

    GameEventsBlockProof(ServerLevel level, BlockPos base, GameEventsProof proof) {
        this.level = level;
        this.base = base;
        this.proof = proof;
    }

    void run() {
        try {
            checkBlockDrops(base);
            proof.clearListeners();
            checkCropGrowth(base.east(4));
            proof.clearListeners();
            checkBonemeal(base.east(8));
            proof.clearListeners();
            checkTreeGrowth(base.east(12));
            proof.clearListeners();
            checkFarmlandTrample(base.east(16));
            proof.clearListeners();
            checkNeighborNotify(base.east(20));
            proof.clearListeners();
            checkPiston(base.east(24));
            proof.clearListeners();
            checkPortal(base.east(28));
            proof.clearListeners();
            checkExplosionDetonate(base.east(40));
            proof.clearListeners();
        } finally {
            proof.clearListeners();
            for (BlockPos pos : touched) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            discardItems(new AABB(base).inflate(60.0));
        }
    }

    private void set(BlockPos pos, BlockState state) {
        touched.add(pos.immutable());
        level.setBlock(pos, state, Block.UPDATE_ALL);
    }

    private void checkBlockDrops(BlockPos pos) {
        AtomicBoolean cancel = new AtomicBoolean(true);
        AtomicInteger fired = new AtomicInteger();
        proof.listen(BlockDropsEvent.class, event -> {
            if (event.pos().equals(pos)) {
                fired.incrementAndGet();
                if (cancel.get()) {
                    event.setCanceled(true);
                } else {
                    event.drops().clear();
                    event.drops().add(new ItemStack(Items.DIAMOND));
                }
            }
        });
        set(pos, Blocks.DIRT.defaultBlockState());
        Block.dropResources(level.getBlockState(pos), level, pos);
        proof.check(fired.get() == 1 && itemsAt(pos).isEmpty(), "block drops canceled");
        cancel.set(false);
        Block.dropResources(level.getBlockState(pos), level, pos);
        List<ItemEntity> items = itemsAt(pos);
        proof.check(items.size() == 1 && items.get(0).getItem().is(Items.DIAMOND),
                "block drops replaced count=" + items.size());
        discardItems(new AABB(pos).inflate(3.0));
    }

    private void checkCropGrowth(BlockPos pos) {
        set(pos.below(), Blocks.SAND.defaultBlockState());
        set(pos, Blocks.CACTUS.defaultBlockState().setValue(CactusBlock.AGE, 15));
        touched.add(pos.above().immutable());
        touched.add(pos.above(2).immutable());
        AtomicBoolean cancel = new AtomicBoolean(true);
        AtomicInteger post = new AtomicInteger();
        AtomicInteger pre = new AtomicInteger();
        proof.listen(CropGrowPreEvent.class, event -> {
            if (event.pos().equals(pos)) {
                pre.incrementAndGet();
                if (cancel.get()) {
                    event.setCanceled(true);
                }
            }
        });
        String placed = level.getBlockState(pos.below()) + " / " + level.getBlockState(pos);
        proof.listen(CropGrowPostEvent.class, event -> {
            if (event.pos().equals(pos)) {
                post.incrementAndGet();
            }
        });
        level.getBlockState(pos).randomTick(level, pos, level.getRandom());
        proof.check(level.getBlockState(pos.above()).isAir() && post.get() == 0, "crop growth canceled pre=" + pre.get() + " placed=" + placed + " above=" + level.getBlockState(pos.above()));
        cancel.set(false);
        level.getBlockState(pos).randomTick(level, pos, level.getRandom());
        proof.check(level.getBlockState(pos.above()).is(Blocks.CACTUS) && post.get() == 1,
                "crop growth post fired=" + post.get() + " pre=" + pre.get() + " above=" + level.getBlockState(pos.above()));
    }

    private void checkBonemeal(BlockPos pos) {
        set(pos.below(), Blocks.FARMLAND.defaultBlockState());
        set(pos, Blocks.WHEAT.defaultBlockState());
        AtomicBoolean cancel = new AtomicBoolean(true);
        proof.listen(BonemealEvent.class, event -> {
            if (event.pos().equals(pos) && cancel.get()) {
                event.setCanceled(true);
            }
        });
        ItemStack boneMeal = new ItemStack(Items.BONE_MEAL, 4);
        boolean grewCanceled = BoneMealItem.growCrop(boneMeal, level, pos);
        CropBlock wheat = (CropBlock) Blocks.WHEAT;
        proof.check(!grewCanceled && wheat.getAge(level.getBlockState(pos)) == 0 && boneMeal.getCount() == 4,
                "bonemeal canceled");
        cancel.set(false);
        boolean grew = BoneMealItem.growCrop(boneMeal, level, pos);
        proof.check(grew && wheat.getAge(level.getBlockState(pos)) > 0, "bonemeal applied");
    }

    private void checkTreeGrowth(BlockPos pos) {
        set(pos.below(), Blocks.DIRT.defaultBlockState());
        BlockState sapling = Blocks.OAK_SAPLING.defaultBlockState().setValue(SaplingBlock.STAGE, 1);
        set(pos, sapling);
        AtomicInteger fired = new AtomicInteger();
        proof.listen(BlockGrowFeatureEvent.class, event -> {
            if (event.pos().equals(pos)) {
                fired.incrementAndGet();
                event.setCanceled(true);
            }
        });
        ((SaplingBlock) Blocks.OAK_SAPLING).advanceTree(level, pos, level.getBlockState(pos), level.getRandom());
        proof.check(fired.get() == 1 && level.getBlockState(pos).is(Blocks.OAK_SAPLING),
                "tree growth canceled fired=" + fired.get());
    }

    private void checkFarmlandTrample(BlockPos pos) {
        set(pos, Blocks.FARMLAND.defaultBlockState());
        LivingEntity zombie = EntityTypes.ZOMBIE.spawn(level, pos.above(), EntitySpawnReason.COMMAND);
        if (zombie == null) {
            proof.check(false, "farmland zombie spawned");
            return;
        }
        // Spawned zombies are sometimes babies, which are too small to trample farmland.
        ((net.minecraft.world.entity.monster.zombie.Zombie) zombie).setBaby(false);
        try {
            AtomicBoolean cancel = new AtomicBoolean(true);
            AtomicInteger fired = new AtomicInteger();
            proof.listen(FarmlandTrampleEvent.class, event -> {
                if (event.pos().equals(pos)) {
                    fired.incrementAndGet();
                    if (cancel.get()) {
                        event.setCanceled(true);
                    }
                }
            });
            BlockState farmland = level.getBlockState(pos);
            farmland.getBlock().fallOn(level, farmland, pos, zombie, 100.0);
            proof.check(fired.get() == 1 && level.getBlockState(pos).is(Blocks.FARMLAND), "farmland trample canceled fired=" + fired.get());
            cancel.set(false);
            farmland.getBlock().fallOn(level, level.getBlockState(pos), pos, zombie, 100.0);
            proof.check(fired.get() == 2 && level.getBlockState(pos).is(Blocks.DIRT), "farmland trampled fired=" + fired.get());
        } finally {
            zombie.discard();
        }
    }

    private void checkNeighborNotify(BlockPos pos) {
        AtomicInteger fired = new AtomicInteger();
        proof.listen(NeighborNotifyEvent.class, event -> {
            if (event.pos().equals(pos)) {
                fired.incrementAndGet();
            }
        });
        level.updateNeighborsAt(pos, Blocks.STONE);
        level.updateNeighborsAtExceptFromFacing(pos, Blocks.STONE, Direction.UP, null);
        proof.check(fired.get() == 2, "neighbor notify fired=" + fired.get());
    }

    private void checkPiston(BlockPos pos) {
        set(pos, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.UP));
        set(pos.above(), Blocks.STONE.defaultBlockState());
        touched.add(pos.above(2).immutable());
        AtomicInteger fired = new AtomicInteger();
        proof.listen(PistonMovePreEvent.class, event -> {
            if (event.pos().equals(pos)) {
                fired.incrementAndGet();
                event.setCanceled(true);
            }
        });
        boolean moved = level.getBlockState(pos).triggerEvent(level, pos, 0, Direction.UP.get3DDataValue());
        proof.check(!moved && fired.get() == 1 && level.getBlockState(pos.above()).is(Blocks.STONE)
                        && !level.getBlockState(pos).getValue(PistonBaseBlock.EXTENDED),
                "piston move canceled fired=" + fired.get());
    }

    private void checkPortal(BlockPos corner) {
        // Obsidian frame 4 wide and 5 tall along the X axis; the interior is 2 by 3.
        for (int x = 0; x < 4; x++) {
            set(corner.east(x), Blocks.OBSIDIAN.defaultBlockState());
            set(corner.east(x).above(4), Blocks.OBSIDIAN.defaultBlockState());
        }
        for (int y = 1; y < 4; y++) {
            set(corner.above(y), Blocks.OBSIDIAN.defaultBlockState());
            set(corner.east(3).above(y), Blocks.OBSIDIAN.defaultBlockState());
            touched.add(corner.east(1).above(y).immutable());
            touched.add(corner.east(2).above(y).immutable());
        }
        AtomicInteger fired = new AtomicInteger();
        proof.listen(PortalSpawnEvent.class, event -> {
            fired.incrementAndGet();
            event.setCanceled(true);
        });
        BlockPos inside = corner.east(1).above(1);
        level.setBlock(inside, Blocks.FIRE.defaultBlockState(), Block.UPDATE_ALL);
        proof.check(fired.get() == 1 && !level.getBlockState(inside).is(Blocks.NETHER_PORTAL),
                "portal spawn canceled fired=" + fired.get() + " inside=" + level.getBlockState(inside) + " below=" + level.getBlockState(inside.below()));
    }

    private void checkExplosionDetonate(BlockPos pos) {
        set(pos.below(), Blocks.STONE.defaultBlockState());
        LivingEntity zombie = EntityTypes.ZOMBIE.spawn(level, pos.east(), EntitySpawnReason.COMMAND);
        if (zombie == null) {
            proof.check(false, "explosion zombie spawned");
            return;
        }
        ((net.minecraft.world.entity.monster.zombie.Zombie) zombie).setBaby(false);
        try {
            zombie.setDeltaMovement(Vec3.ZERO);
            AtomicInteger detonated = new AtomicInteger();
            AtomicBoolean zombieListed = new AtomicBoolean();
            AtomicInteger pushed = new AtomicInteger();
            proof.listen(ExplosionDetonateEvent.class, event -> {
                detonated.incrementAndGet();
                zombieListed.set(event.entities().contains(zombie));
                event.blocks().clear();
            });
            proof.listen(ExplosionKnockbackEvent.class, event -> {
                if (event.entity() == zombie) {
                    pushed.incrementAndGet();
                    event.setKnockback(Vec3.ZERO);
                }
            });
            level.explode(null, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 2.0F, Level.ExplosionInteraction.TNT);
            proof.check(detonated.get() == 1 && level.getBlockState(pos.below()).is(Blocks.STONE),
                    "explosion blocks protected detonated=" + detonated.get());
            proof.check(pushed.get() == 1 && zombie.getDeltaMovement().equals(Vec3.ZERO),
                    "explosion knockback changed pushed=" + pushed.get() + " listed=" + zombieListed.get() + " alive=" + zombie.isAlive() + " motion=" + zombie.getDeltaMovement());
        } finally {
            zombie.discard();
        }
    }

    private List<ItemEntity> itemsAt(BlockPos pos) {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2.0));
    }

    private void discardItems(AABB area) {
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area)) {
            item.discard();
        }
    }
}
