package net.weaver.api.game.event.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.datafixers.util.Either;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.weaver.api.game.event.player.CanPlayerSleepEvent;
import net.weaver.api.game.event.player.PlayerSetSpawnEvent;
import net.weaver.api.game.event.player.PlayerTabListNameFormatEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tab-list names, respawn points, and the bed checks of a server player.
 *
 * <p>{@code startSleepInBed} checks the time of day and nearby monsters
 * before it calls {@code super}. Both checks are recorded instead of acted on,
 * so {@link CanPlayerSleepEvent} fires once, just before {@code super}, with
 * vanilla's problem, and a listener can clear it. The early returns for hard
 * problems (too far, obstructed, already asleep) post the event too, but a
 * listener can only replace those problems, not clear them.</p>
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerStateEventsMixin {
    @Unique
    private static final ThreadLocal<Player.BedSleepingProblem> weaver$deferredProblem = new ThreadLocal<>();

    @Unique
    private static final ThreadLocal<BlockPos> weaver$bedPos = new ThreadLocal<>();

    @Unique
    private ServerPlayer weaver$self() {
        return (ServerPlayer) (Object) this;
    }

    @Inject(method = "getTabListDisplayName()Lnet/minecraft/network/chat/Component;", at = @At("RETURN"), cancellable = true)
    private void weaver$tabListName(CallbackInfoReturnable<Component> callback) {
        if (!GameEvents.hasListeners(PlayerTabListNameFormatEvent.class)) {
            return;
        }
        ServerPlayer self = weaver$self();
        Component name = callback.getReturnValue();
        PlayerTabListNameFormatEvent event = GameEvents.post(new PlayerTabListNameFormatEvent(self, name), self.level());
        if (event.displayName() != name) {
            callback.setReturnValue(event.displayName());
        }
    }

    @Inject(method = "setRespawnPosition(Lnet/minecraft/server/level/ServerPlayer$RespawnConfig;Z)V", at = @At("HEAD"), cancellable = true)
    private void weaver$setSpawn(ServerPlayer.RespawnConfig config, boolean showMessage, CallbackInfo callback) {
        ServerPlayer self = weaver$self();
        if (GameEvents.canceled(GameEvents.post(new PlayerSetSpawnEvent(self, config), self.level()))) {
            callback.cancel();
        }
    }

    @Inject(method = "startSleepInBed(Lnet/minecraft/world/level/block/AbstractBedBlock;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/attribute/BedRule;Lnet/minecraft/core/BlockPos;)Lcom/mojang/datafixers/util/Either;",
            at = @At("HEAD"))
    private void weaver$sleepStart(AbstractBedBlock bed, BlockState state, BedRule rule, BlockPos pos,
                                   CallbackInfoReturnable<Either<Player.BedSleepingProblem, Unit>> callback) {
        weaver$deferredProblem.remove();
        weaver$bedPos.set(pos);
    }

    @WrapOperation(method = "startSleepInBed(Lnet/minecraft/world/level/block/AbstractBedBlock;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/attribute/BedRule;Lnet/minecraft/core/BlockPos;)Lcom/mojang/datafixers/util/Either;",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/attribute/BedRule;canSleep(Lnet/minecraft/world/level/Level;)Z"))
    private boolean weaver$sleepTime(BedRule rule, Level level, Operation<Boolean> original) {
        boolean canSleep = original.call(rule, level);
        if (canSleep || !GameEvents.hasListeners(CanPlayerSleepEvent.class)) {
            return canSleep;
        }
        weaver$deferredProblem.set(rule.asProblem());
        return true;
    }

    @ModifyExpressionValue(method = "startSleepInBed(Lnet/minecraft/world/level/block/AbstractBedBlock;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/attribute/BedRule;Lnet/minecraft/core/BlockPos;)Lcom/mojang/datafixers/util/Either;",
            at = @At(value = "INVOKE", target = "Ljava/util/List;isEmpty()Z"))
    private boolean weaver$sleepMonsters(boolean noMonsters) {
        if (noMonsters || !GameEvents.hasListeners(CanPlayerSleepEvent.class)) {
            return noMonsters;
        }
        if (weaver$deferredProblem.get() == null) {
            weaver$deferredProblem.set(Player.BedSleepingProblem.NOT_SAFE);
        }
        return true;
    }

    @Inject(method = "startSleepInBed(Lnet/minecraft/world/level/block/AbstractBedBlock;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/attribute/BedRule;Lnet/minecraft/core/BlockPos;)Lcom/mojang/datafixers/util/Either;",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;startSleepInBed(Lnet/minecraft/world/level/block/AbstractBedBlock;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/attribute/BedRule;Lnet/minecraft/core/BlockPos;)Lcom/mojang/datafixers/util/Either;"),
            cancellable = true)
    private void weaver$sleepDecide(AbstractBedBlock bed, BlockState state, BedRule rule, BlockPos pos,
                                    CallbackInfoReturnable<Either<Player.BedSleepingProblem, Unit>> callback) {
        Player.BedSleepingProblem deferred = weaver$deferredProblem.get();
        weaver$deferredProblem.remove();
        if (!GameEvents.hasListeners(CanPlayerSleepEvent.class)) {
            if (deferred != null) {
                callback.setReturnValue(Either.left(deferred));
            }
            return;
        }
        ServerPlayer self = weaver$self();
        CanPlayerSleepEvent event = GameEvents.post(new CanPlayerSleepEvent(self, pos, deferred), self.level());
        if (event.problem() != null) {
            callback.setReturnValue(Either.left(event.problem()));
        }
    }

    @WrapOperation(method = "startSleepInBed(Lnet/minecraft/world/level/block/AbstractBedBlock;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/attribute/BedRule;Lnet/minecraft/core/BlockPos;)Lcom/mojang/datafixers/util/Either;",
            at = @At(value = "INVOKE",
                    target = "Lcom/mojang/datafixers/util/Either;left(Ljava/lang/Object;)Lcom/mojang/datafixers/util/Either;"))
    private Either<?, ?> weaver$sleepRefused(Object problem, Operation<Either<?, ?>> original) {
        weaver$deferredProblem.remove();
        if (!(problem instanceof Player.BedSleepingProblem vanilla)
                || !GameEvents.hasListeners(CanPlayerSleepEvent.class)) {
            return original.call(problem);
        }
        ServerPlayer self = weaver$self();
        CanPlayerSleepEvent event = GameEvents.post(new CanPlayerSleepEvent(self, weaver$bedPos.get(), vanilla), self.level());
        Player.BedSleepingProblem chosen = event.problem() == null ? vanilla : event.problem();
        return original.call(chosen);
    }
}
