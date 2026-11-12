package net.weaver.api.game.event.mixin;

import net.minecraft.stats.Stat;
import net.minecraft.stats.StatsCounter;
import net.minecraft.world.entity.player.Player;
import net.weaver.api.game.event.player.StatAwardEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Statistic changes. {@code ServerStatsCounter#setValue} calls {@code super}
 * first, so hooking the base class covers both counters.
 */
@Mixin(StatsCounter.class)
public abstract class StatsCounterEventsMixin {
    @Unique
    private static final ThreadLocal<Object> weaver$reinvoking = new ThreadLocal<>();

    @Inject(method = "setValue(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/stats/Stat;I)V", at = @At("HEAD"), cancellable = true)
    private void weaver$statAward(Player player, Stat<?> stat, int value, CallbackInfo callback) {
        if (player == null || weaver$reinvoking.get() == this || !GameEvents.hasListeners(StatAwardEvent.class)) {
            return;
        }
        StatAwardEvent event = GameEvents.post(new StatAwardEvent(player, stat, value), player.level());
        if (GameEvents.canceled(event)) {
            callback.cancel();
            return;
        }
        if (event.value() != value) {
            callback.cancel();
            Object previous = weaver$reinvoking.get();
            weaver$reinvoking.set(this);
            try {
                ((StatsCounter) (Object) this).setValue(player, stat, event.value());
            } finally {
                weaver$reinvoking.set(previous);
            }
        }
    }
}
