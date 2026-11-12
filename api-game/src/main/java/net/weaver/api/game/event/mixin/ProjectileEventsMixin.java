package net.weaver.api.game.event.mixin;

import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.phys.HitResult;
import net.weaver.api.game.event.support.GameEvents;
import net.weaver.api.game.event.world.ProjectileImpactEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Projectile impacts; every vanilla projectile hits through this method. */
@Mixin(Projectile.class)
public abstract class ProjectileEventsMixin {
    @Inject(method = "hitTargetOrDeflectSelf(Lnet/minecraft/world/phys/HitResult;)Lnet/minecraft/world/entity/projectile/ProjectileDeflection;",
            at = @At("HEAD"), cancellable = true)
    private void weaver$impact(HitResult hit, CallbackInfoReturnable<ProjectileDeflection> callback) {
        if (hit == null || hit.getType() == HitResult.Type.MISS) {
            return;
        }
        Projectile self = (Projectile) (Object) this;
        if (GameEvents.canceled(GameEvents.post(new ProjectileImpactEvent(self, hit), self.level()))) {
            callback.setReturnValue(ProjectileDeflection.NONE);
        }
    }
}
