package net.weaver.api.game.event.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.weaver.api.game.event.player.ExperiencePickupEvent;
import net.weaver.api.game.event.support.GameEvents;
import net.weaver.api.game.event.world.XpOrbTargetingEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Experience orb pickup by players, and the player an orb flies to. */
@Mixin(ExperienceOrb.class)
public abstract class ExperienceOrbEventsMixin {
    @Inject(method = "playerTouch(Lnet/minecraft/world/entity/player/Player;)V", at = @At("HEAD"), cancellable = true)
    private void weaver$pickup(Player player, CallbackInfo callback) {
        if (!GameEvents.hasListeners(ExperiencePickupEvent.class)) {
            return;
        }
        ExperienceOrb self = (ExperienceOrb) (Object) this;
        if (self.level().isClientSide()) {
            return;
        }
        if (GameEvents.canceled(GameEvents.post(new ExperiencePickupEvent(player, self), self.level()))) {
            callback.cancel();
        }
    }

    @WrapOperation(method = "followNearbyPlayer()V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getNearestPlayer(Lnet/minecraft/world/entity/Entity;D)Lnet/minecraft/world/entity/player/Player;"))
    private Player weaver$target(Level level, Entity orb, double range, Operation<Player> original) {
        Player nearest = original.call(level, orb, range);
        if (!GameEvents.hasListeners(XpOrbTargetingEvent.class)) {
            return nearest;
        }
        return GameEvents.post(new XpOrbTargetingEvent((ExperienceOrb) (Object) this, nearest), level).target();
    }
}
