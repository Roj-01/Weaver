package net.weaver.api.game.event.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.game.event.client.AddClientReloadListenersEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client reload listeners: the event fires in {@code Minecraft}'s constructor
 * just before the first resource load starts, after vanilla registered its
 * own listeners.
 */
@Mixin(Minecraft.class)
public abstract class ClientResourceEventsMixin {
    @Shadow
    @Final
    private ReloadableResourceManager resourceManager;

    @Inject(method = "<init>(Lnet/minecraft/client/main/GameConfig;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/packs/resources/ReloadableResourceManager;createReload(Ljava/util/concurrent/Executor;Ljava/util/concurrent/Executor;Ljava/util/concurrent/CompletableFuture;Ljava/util/List;)Lnet/minecraft/server/packs/resources/ReloadInstance;"))
    private void weaver$addReloadListeners(GameConfig config, CallbackInfo callback) {
        GameEvents.post(new AddClientReloadListenersEvent(resourceManager), LogicalSide.CLIENT);
    }
}
