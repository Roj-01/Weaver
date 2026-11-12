package net.weaver.api.game.event.mixin;

import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.weaver.api.game.event.player.ChunkWatchEvent;
import net.weaver.api.game.event.support.GameEvents;
import net.weaver.api.game.event.world.ChunkTicketLevelUpdatedEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Chunk ticket levels, and chunks entering and leaving a player's view. */
@Mixin(ChunkMap.class)
public abstract class ChunkMapEventsMixin {
    @Shadow
    @Final
    private ServerLevel level;

    @Inject(method = "updateChunkScheduling(JILnet/minecraft/server/level/ChunkHolder;I)Lnet/minecraft/server/level/ChunkHolder;",
            at = @At("RETURN"))
    private void weaver$ticketLevel(long node, int newLevel, ChunkHolder holder, int oldLevel,
                                    CallbackInfoReturnable<ChunkHolder> callback) {
        if (GameEvents.hasListeners(ChunkTicketLevelUpdatedEvent.class)) {
            GameEvents.post(new ChunkTicketLevelUpdatedEvent(level, node, oldLevel, newLevel, callback.getReturnValue()), level);
        }
    }

    @Inject(method = "markChunkPendingToSend(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/chunk/LevelChunk;)V",
            at = @At("RETURN"))
    private static void weaver$watch(ServerPlayer player, LevelChunk chunk, CallbackInfo callback) {
        if (GameEvents.hasListeners(ChunkWatchEvent.class)) {
            GameEvents.post(new ChunkWatchEvent(ChunkWatchEvent.Kind.WATCH, player, player.level(), chunk.getPos(), chunk),
                    player.level());
        }
    }

    @Inject(method = "dropChunk(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/ChunkPos;)V",
            at = @At("HEAD"))
    private static void weaver$unwatch(ServerPlayer player, ChunkPos pos, CallbackInfo callback) {
        if (GameEvents.hasListeners(ChunkWatchEvent.class)) {
            GameEvents.post(new ChunkWatchEvent(ChunkWatchEvent.Kind.UNWATCH, player, player.level(), pos, null),
                    player.level());
        }
    }
}
