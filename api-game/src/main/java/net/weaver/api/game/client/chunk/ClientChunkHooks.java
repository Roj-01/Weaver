package net.weaver.api.game.client.chunk;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.weaver.api.client.ClientBlockContext;
import net.weaver.api.client.ClientChunkContext;
import net.weaver.api.client.ClientRegistry;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Game-typed bridge from the built-in client chunk mixin to the game-free
 * {@link ClientRegistry} chunk listeners.
 *
 * <p>The bridge only translates game values to {@link ClientChunkContext} and
 * {@link ClientBlockContext}; it holds no state and never retains the level,
 * position, or chunk. Dispatch is a no-op until a client backend is installed
 * by the first listener registration. A failure is reported once per event
 * type and never escapes into the game loop.
 *
 * <p>The mixin targets are
 * {@code ClientLevel#onChunkLoaded(ChunkPos)},
 * {@code ClientLevel#unload(LevelChunk)}, and
 * {@code ClientLevel#setServerVerifiedBlockState(BlockPos, BlockState, int)}.
 */
public final class ClientChunkHooks
{
    private static final AtomicBoolean LOAD_FAILURE_REPORTED = new AtomicBoolean();
    private static final AtomicBoolean UNLOAD_FAILURE_REPORTED = new AtomicBoolean();
    private static final AtomicBoolean BLOCK_FAILURE_REPORTED = new AtomicBoolean();
    private static final AtomicBoolean LOAD_PROOF_REPORTED = new AtomicBoolean();
    private static final AtomicBoolean UNLOAD_PROOF_REPORTED = new AtomicBoolean();
    private static final AtomicBoolean BLOCK_PROOF_REPORTED = new AtomicBoolean();

    private ClientChunkHooks()
    {
    }

    /**
     * Forwards a chunk-load event.
     *
     * @param level the level that loaded the chunk, possibly {@code null}
     * @param pos   the loaded chunk position, possibly {@code null}
     */
    public static void chunkLoaded(ClientLevel level, ChunkPos pos)
    {
        if (level == null || pos == null)
        {
            return;
        }
        try
        {
            ClientRegistry.chunkLoaded(new ClientChunkContext(dimension(level), pos.x(), pos.z()));
            proof(LOAD_PROOF_REPORTED, "load");
        }
        catch (Throwable failure)
        {
            report(LOAD_FAILURE_REPORTED, "load", failure);
        }
    }

    /**
     * Forwards a chunk-unload event.
     *
     * @param level the level that unloaded the chunk, possibly {@code null}
     * @param pos   the unloaded chunk position, possibly {@code null}
     */
    public static void chunkUnloaded(ClientLevel level, ChunkPos pos)
    {
        if (level == null || pos == null)
        {
            return;
        }
        try
        {
            ClientRegistry.chunkUnloaded(new ClientChunkContext(dimension(level), pos.x(), pos.z()));
            proof(UNLOAD_PROOF_REPORTED, "unload");
        }
        catch (Throwable failure)
        {
            report(UNLOAD_FAILURE_REPORTED, "unload", failure);
        }
    }

    /**
     * Forwards a server-confirmed block-update event.
     *
     * @param level the level that changed, possibly {@code null}
     * @param pos   the changed block position, possibly {@code null}
     */
    public static void blockUpdated(ClientLevel level, BlockPos pos)
    {
        if (level == null || pos == null)
        {
            return;
        }
        try
        {
            ClientRegistry.blockUpdated(new ClientBlockContext(dimension(level), pos.getX(),
                    pos.getY(), pos.getZ()));
            proof(BLOCK_PROOF_REPORTED, "block");
        }
        catch (Throwable failure)
        {
            report(BLOCK_FAILURE_REPORTED, "block", failure);
        }
    }

    private static String dimension(ClientLevel level)
    {
        return level.dimension().identifier().toString();
    }

    private static void proof(AtomicBoolean reported, String type)
    {
        if (Boolean.getBoolean("weaver.proof.chunkEvents")
                && reported.compareAndSet(false, true))
        {
            System.out.println("[weaver] CHUNK EVENTS ok type=" + type);
        }
    }

    private static void report(AtomicBoolean reported, String type, Throwable failure)
    {
        if (reported.compareAndSet(false, true))
        {
            System.err.println("[weaver] CLIENT CHUNK HOOK FAILED type=" + type
                    + " reason=" + failure);
        }
    }
}
