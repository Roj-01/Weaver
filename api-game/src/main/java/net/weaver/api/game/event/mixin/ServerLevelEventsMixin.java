package net.weaver.api.game.event.mixin;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ExplosionParticleInfo;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.redstone.Orientation;
import net.weaver.api.game.event.block.NeighborNotifyEvent;
import net.weaver.api.game.event.world.ExplosionStartEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Server level events: explosions and neighbor notifications. */
@Mixin(ServerLevel.class)
public abstract class ServerLevelEventsMixin {
    // The two-argument updateNeighborsAt delegates to the three-argument one.
    @Inject(method = "updateNeighborsAt(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;Lnet/minecraft/world/level/redstone/Orientation;)V",
            at = @At("HEAD"), cancellable = true)
    private void weaver$neighborNotify(BlockPos pos, Block source, Orientation orientation, CallbackInfo callback) {
        if (GameEvents.hasListeners(NeighborNotifyEvent.class)) {
            ServerLevel self = (ServerLevel) (Object) this;
            if (GameEvents.canceled(GameEvents.post(new NeighborNotifyEvent(self, pos, source, null), self))) {
                callback.cancel();
            }
        }
    }

    @Inject(method = "updateNeighborsAtExceptFromFacing(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;Lnet/minecraft/core/Direction;Lnet/minecraft/world/level/redstone/Orientation;)V",
            at = @At("HEAD"), cancellable = true)
    private void weaver$neighborNotifyExcept(BlockPos pos, Block source, Direction skipped, Orientation orientation,
                                             CallbackInfo callback) {
        if (GameEvents.hasListeners(NeighborNotifyEvent.class)) {
            ServerLevel self = (ServerLevel) (Object) this;
            if (GameEvents.canceled(GameEvents.post(new NeighborNotifyEvent(self, pos, source, skipped), self))) {
                callback.cancel();
            }
        }
    }

    @Inject(method = "explode(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;"
            + "Lnet/minecraft/world/level/ExplosionDamageCalculator;DDDFZ"
            + "Lnet/minecraft/world/level/Level$ExplosionInteraction;"
            + "Lnet/minecraft/core/particles/ParticleOptions;Lnet/minecraft/core/particles/ParticleOptions;"
            + "Lnet/minecraft/util/random/WeightedList;Lnet/minecraft/core/Holder;)V",
            at = @At("HEAD"), cancellable = true)
    private void weaver$explosionStart(Entity source, DamageSource damageSource,
                                       ExplosionDamageCalculator calculator, double x, double y, double z,
                                       float radius, boolean fire, Level.ExplosionInteraction interaction,
                                       ParticleOptions smallParticles, ParticleOptions largeParticles,
                                       WeightedList<ExplosionParticleInfo> blockParticles,
                                       Holder<SoundEvent> sound, CallbackInfo callback) {
        ServerLevel self = (ServerLevel) (Object) this;
        ExplosionStartEvent event = new ExplosionStartEvent(self, source, new Vec3(x, y, z), radius, fire);
        if (GameEvents.canceled(GameEvents.post(event, self))) {
            callback.cancel();
        }
    }
}
