package net.weaver.api.game.event.mixin.client;

import net.minecraft.client.multiplayer.RegistryDataCollector;
import net.minecraft.core.RegistryAccess;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.game.event.support.GameEvents;
import net.weaver.api.game.event.world.DefaultDataComponentsBoundEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Default components bound to the registries a client received. */
@Mixin(RegistryDataCollector.class)
public abstract class ClientComponentsEventsMixin {
    @Inject(method = "updateComponents(Lnet/minecraft/core/RegistryAccess$Frozen;Z)V", at = @At("RETURN"))
    private static void weaver$bound(RegistryAccess.Frozen registries, boolean local, CallbackInfo callback) {
        if (GameEvents.hasListeners(DefaultDataComponentsBoundEvent.class)) {
            GameEvents.post(new DefaultDataComponentsBoundEvent(registries, true), LogicalSide.CLIENT);
        }
    }
}
