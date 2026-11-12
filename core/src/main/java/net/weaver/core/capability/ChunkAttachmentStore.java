package net.weaver.core.capability;

import java.lang.reflect.Method;
import net.weaver.api.capability.AttachmentRegistry;
import net.weaver.core.data.ReflectiveValueDataAccess;
import net.weaver.core.data.ValueDataAccess;

/**
 * {@link net.weaver.api.capability.AttachmentStore} for game chunks.
 *
 * <p>Values are held by weak identity. An {@code ImposterProtoChunk} shares
 * the values of the {@code LevelChunk} it wraps. With {@code api-game},
 * values are saved in the chunk's own save data automatically.</p>
 */
public final class ChunkAttachmentStore extends IdentityAttachmentStore
{
    static final String CHUNK_CLASS = "net.minecraft.world.level.chunk.ChunkAccess";
    static final String IMPOSTER_CLASS = "net.minecraft.world.level.chunk.ImposterProtoChunk";

    private volatile Method wrapped;

    /**
     * Creates a store backed by the reflective value bridge.
     *
     * @param registry the registry whose capabilities are persisted
     */
    public ChunkAttachmentStore(AttachmentRegistry registry)
    {
        this(registry, new ReflectiveValueDataAccess());
    }

    /**
     * Creates a store backed by the supplied value access.
     *
     * @param registry the registry whose capabilities are persisted
     * @param access   the non-null value access
     */
    public ChunkAttachmentStore(AttachmentRegistry registry, ValueDataAccess access)
    {
        super(registry, CHUNK_CLASS, access);
    }

    @Override
    Object canonical(Object holder)
    {
        if (!IMPOSTER_CLASS.equals(holder.getClass().getName()))
        {
            return holder;
        }
        try
        {
            Method method = wrapped;
            if (method == null)
            {
                method = holder.getClass().getMethod("getWrapped");
                wrapped = method;
            }
            Object inner = method.invoke(holder);
            return inner != null ? inner : holder;
        }
        catch (ReflectiveOperationException | RuntimeException failure)
        {
            return holder;
        }
    }

    @Override
    public int priority()
    {
        return 40;
    }
}
