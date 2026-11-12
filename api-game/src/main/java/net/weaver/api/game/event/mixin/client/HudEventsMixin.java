package net.weaver.api.game.event.mixin.client;

import net.minecraft.world.entity.player.Player;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.game.event.client.HeartTypeEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The heart type the HUD draws. {@code Hud.HeartType} is package-private, so
 * the value is handled as a plain enum and exchanged by name.
 */
@Mixin(targets = "net.minecraft.client.gui.Hud$HeartType")
public abstract class HudEventsMixin {
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Inject(method = "forPlayer(Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/client/gui/Hud$HeartType;",
            at = @At("RETURN"), cancellable = true)
    private static void weaver$heartType(Player player, CallbackInfoReturnable callback) {
        if (!GameEvents.hasListeners(HeartTypeEvent.class)) {
            return;
        }
        Enum vanilla = (Enum) callback.getReturnValue();
        String chosen = GameEvents.post(new HeartTypeEvent(player, vanilla.name()), LogicalSide.CLIENT).type();
        if (!chosen.equals(vanilla.name())) {
            try {
                callback.setReturnValue(Enum.valueOf(vanilla.getDeclaringClass(), chosen));
            } catch (IllegalArgumentException unknown) {
                // keep vanilla's type
            }
        }
    }
}
