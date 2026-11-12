package net.weaver.api.game.event.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.PhantomSpawner;
import net.weaver.api.game.event.player.PlayerSpawnPhantomsEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Phantom spawning per player. The event is posted at the spectator check,
 * the first thing the loop does for each player, and kept for the rest of
 * that iteration: {@code ALLOW} passes the difficulty roll and the
 * time-since-rest roll (the second {@code nextInt}), and the group size is the
 * sixth {@code nextInt} plus one.
 */
@Mixin(PhantomSpawner.class)
public abstract class PhantomSpawnerEventsMixin {
    @Unique
    private static final ThreadLocal<PlayerSpawnPhantomsEvent> weaver$current = new ThreadLocal<>();

    @WrapOperation(method = "tick(Lnet/minecraft/server/level/ServerLevel;Z)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;isSpectator()Z"))
    private boolean weaver$consider(ServerPlayer player, Operation<Boolean> original) {
        weaver$current.remove();
        boolean spectator = original.call(player);
        if (spectator || !GameEvents.hasListeners(PlayerSpawnPhantomsEvent.class)) {
            return spectator;
        }
        PlayerSpawnPhantomsEvent event = GameEvents.post(new PlayerSpawnPhantomsEvent(player), player.level());
        if (event.decision() == PlayerSpawnPhantomsEvent.Decision.DENY) {
            return true;
        }
        weaver$current.set(event);
        return false;
    }

    @ModifyExpressionValue(method = "tick(Lnet/minecraft/server/level/ServerLevel;Z)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/DifficultyInstance;isHarderThan(F)Z"))
    private boolean weaver$difficultyRoll(boolean passed) {
        PlayerSpawnPhantomsEvent event = weaver$current.get();
        return passed || (event != null && event.decision() == PlayerSpawnPhantomsEvent.Decision.ALLOW);
    }

    @ModifyExpressionValue(method = "tick(Lnet/minecraft/server/level/ServerLevel;Z)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;nextInt(I)I", ordinal = 1))
    private int weaver$restRoll(int roll) {
        PlayerSpawnPhantomsEvent event = weaver$current.get();
        return event != null && event.decision() == PlayerSpawnPhantomsEvent.Decision.ALLOW ? Math.max(roll, 72000) : roll;
    }

    @ModifyExpressionValue(method = "tick(Lnet/minecraft/server/level/ServerLevel;Z)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;nextInt(I)I", ordinal = 5))
    private int weaver$groupSize(int roll) {
        PlayerSpawnPhantomsEvent event = weaver$current.get();
        return event != null && event.phantomCount() >= 0 ? event.phantomCount() - 1 : roll;
    }
}
