package net.weaver.api.game.event.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.weaver.api.game.capability.ChunkDataValues;
import net.weaver.api.game.capability.HolderAttachments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Chunk capability values in the chunk's save data, under
 * {@code weaver:capabilities}. They are encoded on the main thread when the
 * save copy is made and written with the rest of the chunk; on load they are
 * read with the chunk and applied to it before the load event.
 */
@Mixin(value = SerializableChunkData.class, priority = 900)
public abstract class SerializableChunkDataAttachmentsMixin implements ChunkDataValues {
    @Unique
    private volatile CompoundTag weaver$values;

    @Override
    public CompoundTag weaver$capabilityValues() {
        return weaver$values;
    }

    @Override
    public void weaver$setCapabilityValues(CompoundTag values) {
        weaver$values = values;
    }

    @Inject(method = "parse(Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/world/level/chunk/PalettedContainerFactory;Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/world/level/chunk/storage/SerializableChunkData;",
            at = @At("RETURN"))
    private static void weaver$parsed(LevelHeightAccessor height, PalettedContainerFactory containers, CompoundTag tag,
                                      CallbackInfoReturnable<SerializableChunkData> callback) {
        SerializableChunkData data = callback.getReturnValue();
        if (data != null && tag.contains(HolderAttachments.ROOT_KEY)) {
            ((ChunkDataValues) (Object) data).weaver$setCapabilityValues(
                    tag.getCompound(HolderAttachments.ROOT_KEY).orElse(null));
        }
    }

    @Inject(method = "read(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/ai/village/poi/PoiManager;Lnet/minecraft/world/level/chunk/storage/RegionStorageInfo;Lnet/minecraft/world/level/ChunkPos;)Lnet/minecraft/world/level/chunk/ProtoChunk;",
            at = @At("RETURN"))
    private void weaver$read(ServerLevel level, PoiManager poi, RegionStorageInfo info, ChunkPos pos,
                             CallbackInfoReturnable<ProtoChunk> callback) {
        if (weaver$values != null && callback.getReturnValue() != null) {
            HolderAttachments.load(level.registryAccess(), HolderAttachments.unwrap(callback.getReturnValue()),
                    weaver$values);
        }
    }

    @Inject(method = "copyOf(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;)Lnet/minecraft/world/level/chunk/storage/SerializableChunkData;",
            at = @At("RETURN"))
    private static void weaver$copied(ServerLevel level, ChunkAccess chunk,
                                      CallbackInfoReturnable<SerializableChunkData> callback) {
        SerializableChunkData data = callback.getReturnValue();
        if (data != null) {
            ((ChunkDataValues) (Object) data).weaver$setCapabilityValues(
                    HolderAttachments.save(level.registryAccess(), HolderAttachments.unwrap(chunk)));
        }
    }

    @Inject(method = "write()Lnet/minecraft/nbt/CompoundTag;", at = @At("RETURN"))
    private void weaver$write(CallbackInfoReturnable<CompoundTag> callback) {
        CompoundTag values = weaver$values;
        if (values != null) {
            callback.getReturnValue().put(HolderAttachments.ROOT_KEY, values);
        }
    }
}
