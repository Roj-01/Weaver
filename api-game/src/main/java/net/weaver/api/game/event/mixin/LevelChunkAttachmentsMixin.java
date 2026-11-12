package net.weaver.api.game.event.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.weaver.api.game.capability.HolderAttachments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A proto chunk's capability values move to the full chunk built from it. */
@Mixin(LevelChunk.class)
public abstract class LevelChunkAttachmentsMixin {
    @Inject(method = "<init>(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ProtoChunk;Lnet/minecraft/world/level/chunk/LevelChunk$PostLoadProcessor;)V",
            at = @At("RETURN"))
    private void weaver$fromProto(ServerLevel level, ProtoChunk proto, LevelChunk.PostLoadProcessor postLoad,
                                  CallbackInfo callback) {
        HolderAttachments.transfer(level.registryAccess(), proto, this);
    }
}
