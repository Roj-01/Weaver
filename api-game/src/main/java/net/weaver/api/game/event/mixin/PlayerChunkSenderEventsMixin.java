package net.weaver.api.game.event.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.chunk.LevelChunk;
import net.weaver.api.game.event.player.ChunkWatchEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A chunk packet sent to a player. */
@Mixin(PlayerChunkSender.class)
public abstract class PlayerChunkSenderEventsMixin {
    @Inject(method = "sendChunk(Lnet/minecraft/server/network/ServerGamePacketListenerImpl;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/LevelChunk;)V",
            at = @At("RETURN"))
    private static void weaver$sent(ServerGamePacketListenerImpl connection, ServerLevel level, LevelChunk chunk,
                                    CallbackInfo callback) {
        if (GameEvents.hasListeners(ChunkWatchEvent.class)) {
            GameEvents.post(new ChunkWatchEvent(ChunkWatchEvent.Kind.SENT, connection.player, level, chunk.getPos(), chunk),
                    level);
        }
    }
}
