package net.weaver.api.game.event.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.clock.ClockTimeMarker;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.weaver.api.game.event.support.GameEvents;
import net.weaver.api.game.event.world.ModifyCustomSpawnersEvent;
import net.weaver.api.game.event.world.SleepFinishedEvent;
import net.weaver.api.game.event.world.VanillaGameEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Custom spawner list, the clock jump after a night's sleep, and vibration
 * game events. The custom spawner event is posted the first time the level
 * iterates its spawners; the resulting list replaces vanilla's from then on.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelWorldEventsMixin {
    @Unique
    private List<CustomSpawner> weaver$customSpawners;

    @Unique
    private ServerLevel weaver$self() {
        return (ServerLevel) (Object) this;
    }

    @WrapOperation(method = "tickCustomSpawners(Z)V",
            at = @At(value = "INVOKE", target = "Ljava/util/List;iterator()Ljava/util/Iterator;"))
    private Iterator<CustomSpawner> weaver$customSpawners(List<CustomSpawner> vanilla, Operation<Iterator<CustomSpawner>> original) {
        if (weaver$customSpawners == null) {
            ServerLevel self = weaver$self();
            List<CustomSpawner> spawners = new java.util.ArrayList<>(
                    GameEvents.post(new ModifyCustomSpawnersEvent(self, vanilla), self).spawners());
            spawners.removeIf(java.util.Objects::isNull);
            weaver$customSpawners = List.copyOf(spawners);
        }
        return original.call(weaver$customSpawners);
    }

    @WrapOperation(method = "tick(Ljava/util/function/BooleanSupplier;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/clock/ServerClockManager;moveToTimeMarker(Lnet/minecraft/core/Holder;Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/world/clock/ServerClockManager$MoveResult;"))
    private ServerClockManager.MoveResult weaver$sleepFinished(ServerClockManager clocks, Holder<WorldClock> clock,
                                                               ResourceKey<ClockTimeMarker> marker,
                                                               Operation<ServerClockManager.MoveResult> original) {
        ServerLevel self = weaver$self();
        SleepFinishedEvent event = GameEvents.post(new SleepFinishedEvent(self, marker), self);
        if (GameEvents.canceled(event)) {
            return null;
        }
        return original.call(clocks, clock, event.marker());
    }

    @Inject(method = "gameEvent(Lnet/minecraft/core/Holder;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/gameevent/GameEvent$Context;)V",
            at = @At("HEAD"), cancellable = true)
    private void weaver$gameEvent(Holder<GameEvent> gameEvent, Vec3 position, GameEvent.Context context, CallbackInfo callback) {
        if (!GameEvents.hasListeners(VanillaGameEvent.class)) {
            return;
        }
        ServerLevel self = weaver$self();
        if (GameEvents.canceled(GameEvents.post(new VanillaGameEvent(self, gameEvent, position, context), self))) {
            callback.cancel();
        }
    }
}
