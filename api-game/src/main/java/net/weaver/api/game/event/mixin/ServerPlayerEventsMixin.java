package net.weaver.api.game.event.mixin;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.weaver.api.game.event.living.LivingDeathEvent;
import net.weaver.api.game.event.support.GameEvents;
import net.weaver.api.game.event.player.ItemTossEvent;
import net.weaver.api.game.event.player.PlayerChangeGameModeEvent;
import net.weaver.api.game.event.player.PlayerChangedDimensionEvent;
import net.weaver.api.game.event.player.PlayerCloneEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Server player events: death ({@code ServerPlayer#die} does not call
 * {@code LivingEntity#die}), clone, dimension change, game mode change, item toss.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerEventsMixin {
    @Unique
    private static final ThreadLocal<Object> weaver$reinvoking = new ThreadLocal<>();

    @Unique
    private ResourceKey<Level> weaver$dimensionBeforeTeleport;

    @Unique
    private ServerPlayer weaver$self() {
        return (ServerPlayer) (Object) this;
    }

    @Inject(method = "restoreFrom(Lnet/minecraft/server/level/ServerPlayer;Z)V", at = @At("TAIL"))
    private void weaver$clone(ServerPlayer original, boolean restoreAll, CallbackInfo callback) {
        ServerPlayer self = weaver$self();
        // Capability values first, so clone listeners see them on the new player.
        net.weaver.api.game.capability.HolderAttachments.copyPlayer(original, self, restoreAll);
        GameEvents.post(new PlayerCloneEvent(self, original, !restoreAll), self.level());
    }

    @Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;",
            at = @At("HEAD"), cancellable = true)
    private void weaver$beforeTeleport(TeleportTransition transition,
                                       CallbackInfoReturnable<ServerPlayer> callback) {
        ServerPlayer self = weaver$self();
        if (transition.newLevel() != self.level() && net.weaver.api.game.event.support.GameEvents.canceled(
                GameEvents.post(new net.weaver.api.game.event.world.EntityTravelToDimensionEvent(
                        self, transition.newLevel().dimension()), self.level()))) {
            callback.setReturnValue(null);
            return;
        }
        weaver$dimensionBeforeTeleport = self.level().dimension();
    }

    @Inject(method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;",
            at = @At("RETURN"))
    private void weaver$afterTeleport(TeleportTransition transition,
                                      CallbackInfoReturnable<ServerPlayer> callback) {
        ResourceKey<Level> from = weaver$dimensionBeforeTeleport;
        weaver$dimensionBeforeTeleport = null;
        ServerPlayer moved = callback.getReturnValue();
        if (from == null || moved == null) {
            return;
        }
        ResourceKey<Level> to = moved.level().dimension();
        if (!from.equals(to)) {
            GameEvents.post(new PlayerChangedDimensionEvent(moved, from, to), moved.level());
        }
    }

    @Inject(method = "setGameMode(Lnet/minecraft/world/level/GameType;)Z", at = @At("HEAD"), cancellable = true)
    private void weaver$gameMode(GameType mode, CallbackInfoReturnable<Boolean> callback) {
        if (weaver$reinvoking.get() == this) {
            return;
        }
        ServerPlayer self = weaver$self();
        PlayerChangeGameModeEvent event = GameEvents.post(
                new PlayerChangeGameModeEvent(self, self.gameMode.getGameModeForPlayer(), mode), self.level());
        if (GameEvents.canceled(event)) {
            callback.setReturnValue(false);
            return;
        }
        if (event.newMode() != mode) {
            Object previous = weaver$reinvoking.get();
            weaver$reinvoking.set(this);
            try {
                callback.setReturnValue(self.setGameMode(event.newMode()));
            } finally {
                weaver$reinvoking.set(previous);
            }
        }
    }

    @Inject(method = "die(Lnet/minecraft/world/damagesource/DamageSource;)V", at = @At("HEAD"), cancellable = true)
    private void weaver$death(DamageSource source, CallbackInfo callback) {
        ServerPlayer self = weaver$self();
        if (GameEvents.hasListeners(LivingDeathEvent.class)
                && GameEvents.canceled(GameEvents.post(new LivingDeathEvent(self, source), self.level()))) {
            callback.cancel();
        }
    }

    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZLnet/minecraft/util/Prediction;)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At("RETURN"))
    private void weaver$toss(ItemStack stack, boolean thrownFromHand, Prediction prediction,
                             CallbackInfoReturnable<ItemEntity> callback) {
        ItemEntity item = callback.getReturnValue();
        if (item != null) {
            ServerPlayer self = weaver$self();
            GameEvents.post(new ItemTossEvent(self, item), self.level());
        }
    }
}
