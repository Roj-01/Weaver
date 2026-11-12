package net.weaver.api.game.event.mixin;

import net.minecraft.server.dedicated.DedicatedServer;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.game.event.support.GameEvents;
import net.weaver.api.game.event.world.GameShuttingDownEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A dedicated server process shutting down. */
@Mixin(DedicatedServer.class)
public abstract class DedicatedServerEventsMixin {
    @Inject(method = "stopServer()V", at = @At("HEAD"))
    private void weaver$shutdown(CallbackInfo callback) {
        GameEvents.post(new GameShuttingDownEvent(), LogicalSide.SERVER);
    }
}
