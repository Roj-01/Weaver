package net.weaver.api.game.event.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.weaver.api.game.event.living.CanContinueSleepingEvent;
import net.weaver.api.game.event.player.PlayerBreakSpeedEvent;
import net.weaver.api.game.event.player.PlayerFlyableFallEvent;
import net.weaver.api.game.event.player.PlayerHarvestCheckEvent;
import net.weaver.api.game.event.player.PlayerNameFormatEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Player mining speed, harvest checks, display name, flyable falls, and bed rules. */
@Mixin(Player.class)
public abstract class PlayerStateEventsMixin {
    @Unique
    private Player weaver$self() {
        return (Player) (Object) this;
    }

    @Inject(method = "getDestroySpeed(Lnet/minecraft/world/level/block/state/BlockState;)F", at = @At("RETURN"), cancellable = true)
    private void weaver$breakSpeed(BlockState state, CallbackInfoReturnable<Float> callback) {
        if (!GameEvents.hasListeners(PlayerBreakSpeedEvent.class)) {
            return;
        }
        Player self = weaver$self();
        float speed = callback.getReturnValue();
        PlayerBreakSpeedEvent event = GameEvents.post(new PlayerBreakSpeedEvent(self, state, speed), self.level());
        if (event.speed() != speed) {
            callback.setReturnValue(event.speed());
        }
    }

    @Inject(method = "hasCorrectToolForDrops(Lnet/minecraft/world/level/block/state/BlockState;)Z", at = @At("RETURN"), cancellable = true)
    private void weaver$harvestCheck(BlockState state, CallbackInfoReturnable<Boolean> callback) {
        if (!GameEvents.hasListeners(PlayerHarvestCheckEvent.class)) {
            return;
        }
        Player self = weaver$self();
        boolean canHarvest = callback.getReturnValue();
        PlayerHarvestCheckEvent event = GameEvents.post(new PlayerHarvestCheckEvent(self, state, canHarvest), self.level());
        if (event.canHarvest() != canHarvest) {
            callback.setReturnValue(event.canHarvest());
        }
    }

    @ModifyArg(method = "getDisplayName()Lnet/minecraft/network/chat/Component;",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/scores/PlayerTeam;formatNameForTeam(Lnet/minecraft/world/scores/Team;Lnet/minecraft/network/chat/Component;)Lnet/minecraft/network/chat/MutableComponent;"),
            index = 1)
    private Component weaver$nameFormat(Component name) {
        if (!GameEvents.hasListeners(PlayerNameFormatEvent.class)) {
            return name;
        }
        Player self = weaver$self();
        return GameEvents.post(new PlayerNameFormatEvent(self, name), self.level()).displayName();
    }

    @Inject(method = "causeFallDamage(DFLnet/minecraft/world/damagesource/DamageSource;)Z", at = @At("HEAD"))
    private void weaver$flyableFall(double distance, float multiplier, DamageSource source,
                                    CallbackInfoReturnable<Boolean> callback) {
        Player self = weaver$self();
        if (self.getAbilities().mayfly) {
            GameEvents.post(new PlayerFlyableFallEvent(self, distance, multiplier), self.level());
        }
    }

    // Player#tick wakes a sleeping player when the bed's rule no longer allows sleep.
    @ModifyExpressionValue(method = "tick()V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/attribute/BedRule;canSleep(Lnet/minecraft/world/level/Level;)Z"))
    private boolean weaver$continueSleeping(boolean canSleep) {
        if (!GameEvents.hasListeners(CanContinueSleepingEvent.class)) {
            return canSleep;
        }
        Player self = weaver$self();
        return GameEvents.post(new CanContinueSleepingEvent(self,
                canSleep ? null : Player.BedSleepingProblem.OTHER_PROBLEM), self.level()).continueSleeping();
    }
}
