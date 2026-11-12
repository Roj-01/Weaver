package net.weaver.api.game.event.mixin;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.weaver.api.game.event.player.AdvancementProgressEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Advancement criteria granted to a player. */
@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsEventsMixin {
    @Shadow
    private ServerPlayer player;

    @Shadow
    public abstract AdvancementProgress getOrStartProgress(AdvancementHolder advancement);

    @Inject(method = "award(Lnet/minecraft/advancements/AdvancementHolder;Ljava/lang/String;)Z", at = @At("RETURN"))
    private void weaver$award(AdvancementHolder advancement, String criterion,
                              CallbackInfoReturnable<Boolean> callback) {
        if (Boolean.TRUE.equals(callback.getReturnValue()) && player != null) {
            boolean completed = getOrStartProgress(advancement).isDone();
            GameEvents.post(new AdvancementProgressEvent(player, advancement, criterion, completed),
                    player.level());
        }
    }
}
