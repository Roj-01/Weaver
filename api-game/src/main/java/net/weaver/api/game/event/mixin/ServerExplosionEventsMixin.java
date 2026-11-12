package net.weaver.api.game.event.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.phys.Vec3;
import net.weaver.api.game.event.support.GameEvents;
import net.weaver.api.game.event.world.ExplosionDetonateEvent;
import net.weaver.api.game.event.world.ExplosionKnockbackEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.List;

/**
 * Explosion detonation and knockback. {@code explode()} computes the block list
 * first and then calls {@code hurtEntities()}; the block list is made mutable
 * where it is computed and handed to the detonate event together with the
 * entity list, so changes to either reach the vanilla code.
 */
@Mixin(ServerExplosion.class)
public abstract class ServerExplosionEventsMixin {
    @Shadow
    @Final
    private ServerLevel level;

    @Unique
    private List<BlockPos> weaver$blocks;

    @ModifyExpressionValue(method = "explode()I",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/ServerExplosion;calculateExplodedPositions()Ljava/util/List;"))
    private List<BlockPos> weaver$mutableBlocks(List<BlockPos> blocks) {
        if (!GameEvents.hasListeners(ExplosionDetonateEvent.class)) {
            weaver$blocks = null;
            return blocks;
        }
        weaver$blocks = new ArrayList<>(blocks);
        return weaver$blocks;
    }

    @ModifyExpressionValue(method = "hurtEntities()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
    private List<Entity> weaver$detonate(List<Entity> entities) {
        List<BlockPos> blocks = weaver$blocks;
        weaver$blocks = null;
        if (blocks == null) {
            return entities;
        }
        List<Entity> mutable = new ArrayList<>(entities);
        GameEvents.post(new ExplosionDetonateEvent(level, (ServerExplosion) (Object) this, blocks, mutable), level);
        blocks.removeIf(java.util.Objects::isNull);
        mutable.removeIf(java.util.Objects::isNull);
        return mutable;
    }

    @WrapOperation(method = "hurtEntities()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;pushFromExplosion(Lnet/minecraft/world/phys/Vec3;)V"))
    private void weaver$knockback(Entity entity, Vec3 knockback, Operation<Void> original) {
        if (!GameEvents.hasListeners(ExplosionKnockbackEvent.class)) {
            original.call(entity, knockback);
            return;
        }
        ExplosionKnockbackEvent event = GameEvents.post(
                new ExplosionKnockbackEvent(level, (ServerExplosion) (Object) this, entity, knockback), level);
        original.call(entity, event.knockback());
    }
}
