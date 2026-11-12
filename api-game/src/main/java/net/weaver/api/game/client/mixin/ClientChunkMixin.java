package net.weaver.api.game.client.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.weaver.api.game.client.chunk.ClientChunkHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forwards client chunk load, chunk unload, and server-confirmed block update
 * events to the Weaver chunk listeners.
 *
 * <p>Targets verified against Minecraft 26.3:
 * {@code ClientLevel#onChunkLoaded(ChunkPos)},
 * {@code ClientLevel#unload(LevelChunk)}, and
 * {@code ClientLevel#setServerVerifiedBlockState(BlockPos, BlockState, int)}.
 * The block target is the shared path for single-block and section
 * block-update packets.
 */
@Mixin(ClientLevel.class)
public abstract class ClientChunkMixin
{
    @Inject(method = "onChunkLoaded", at = @At("TAIL"))
    private void weaverChunkLoaded(ChunkPos pos, CallbackInfo callback)
    {
        ClientChunkHooks.chunkLoaded((ClientLevel) (Object) this, pos);
    }

    @Inject(method = "unload", at = @At("HEAD"))
    private void weaverChunkUnloaded(LevelChunk chunk, CallbackInfo callback)
    {
        if (chunk != null)
        {
            ClientChunkHooks.chunkUnloaded((ClientLevel) (Object) this, chunk.getPos());
        }
    }

    @Inject(method = "setServerVerifiedBlockState", at = @At("HEAD"))
    private void weaverBlockUpdated(BlockPos pos, BlockState state, int flags,
                                    CallbackInfo callback)
    {
        ClientChunkHooks.blockUpdated((ClientLevel) (Object) this, pos);
    }
}
