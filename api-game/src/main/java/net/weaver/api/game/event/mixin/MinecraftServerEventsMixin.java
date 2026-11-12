package net.weaver.api.game.event.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.storage.ServerLevelData;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.game.event.support.GameEvents;
import net.weaver.api.game.event.world.CreateSpawnPositionEvent;
import net.weaver.api.game.event.world.GameRuleChangedEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Game rule changes and the initial spawn point of a new world. */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerEventsMixin {
    @Inject(method = "onGameRuleChanged(Lnet/minecraft/world/level/gamerules/GameRule;Ljava/lang/Object;)V", at = @At("RETURN"))
    private void weaver$gameRuleChanged(GameRule<?> rule, Object value, CallbackInfo callback) {
        GameEvents.post(new GameRuleChangedEvent((MinecraftServer) (Object) this, rule, value), LogicalSide.SERVER);
    }

    @Inject(method = "setInitialSpawn(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/storage/ServerLevelData;ZZLnet/minecraft/server/level/progress/LevelLoadListener;)V",
            at = @At("HEAD"), cancellable = true)
    private static void weaver$createSpawn(ServerLevel level, ServerLevelData levelData, boolean bonusChest, boolean debug,
                                           LevelLoadListener listener, CallbackInfo callback) {
        if (GameEvents.canceled(GameEvents.post(new CreateSpawnPositionEvent(level, levelData), level))) {
            callback.cancel();
        }
    }
}
