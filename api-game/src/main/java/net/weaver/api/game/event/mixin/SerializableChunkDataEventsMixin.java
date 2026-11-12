package net.weaver.api.game.event.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.weaver.api.game.event.support.GameEvents;
import net.weaver.api.game.event.world.ChunkDataLoadEvent;
import net.weaver.api.game.event.world.ChunkDataSaveEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Chunk data read from and copied for disk. */
@Mixin(SerializableChunkData.class)
public abstract class SerializableChunkDataEventsMixin {
    @Inject(method = "read(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/ai/village/poi/PoiManager;Lnet/minecraft/world/level/chunk/storage/RegionStorageInfo;Lnet/minecraft/world/level/ChunkPos;)Lnet/minecraft/world/level/chunk/ProtoChunk;",
            at = @At("RETURN"))
    private void weaver$load(ServerLevel level, PoiManager poi, RegionStorageInfo info, ChunkPos pos,
                             CallbackInfoReturnable<ProtoChunk> callback) {
        if (GameEvents.hasListeners(ChunkDataLoadEvent.class)) {
            GameEvents.post(new ChunkDataLoadEvent(level, callback.getReturnValue(),
                    (SerializableChunkData) (Object) this), level);
        }
    }

    @Inject(method = "copyOf(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;)Lnet/minecraft/world/level/chunk/storage/SerializableChunkData;",
            at = @At("RETURN"))
    private static void weaver$save(ServerLevel level, ChunkAccess chunk, CallbackInfoReturnable<SerializableChunkData> callback) {
        if (GameEvents.hasListeners(ChunkDataSaveEvent.class)) {
            GameEvents.post(new ChunkDataSaveEvent(level, chunk, callback.getReturnValue()), level);
        }
    }
}
