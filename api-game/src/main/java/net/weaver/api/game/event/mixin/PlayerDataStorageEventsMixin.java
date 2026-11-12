package net.weaver.api.game.event.mixin;

import java.io.File;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.PlayerDataStorage;
import net.weaver.api.game.event.player.PlayerSaveToFileEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Player data saves. */
@Mixin(PlayerDataStorage.class)
public abstract class PlayerDataStorageEventsMixin {
    @Shadow
    @Final
    private File playerDir;

    @Inject(method = "save(Lnet/minecraft/world/entity/player/Player;)V", at = @At("RETURN"))
    private void weaver$saveToFile(Player player, CallbackInfo callback) {
        GameEvents.post(new PlayerSaveToFileEvent(player, playerDir), player.level());
    }
}
