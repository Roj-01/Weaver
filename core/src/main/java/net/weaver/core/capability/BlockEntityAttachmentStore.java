package net.weaver.core.capability;

import net.weaver.api.capability.AttachmentRegistry;
import net.weaver.core.data.ReflectiveValueDataAccess;
import net.weaver.core.data.ValueDataAccess;

/**
 * {@link net.weaver.api.capability.AttachmentStore} for game block entities.
 *
 * <p>Values are held by weak identity and persisted through the game
 * {@code ValueOutput}/{@code ValueInput} carriers. The store accepts every
 * class in the {@code net.minecraft.world.level.block.entity.BlockEntity}
 * hierarchy and every non-null capability; persistence writes only registered
 * codec-backed capabilities.</p>
 */
public final class BlockEntityAttachmentStore extends IdentityAttachmentStore
{
    static final String BLOCK_ENTITY_CLASS =
            "net.minecraft.world.level.block.entity.BlockEntity";

    /**
     * Creates a store backed by the reflective value bridge.
     *
     * @param registry the registry whose capabilities are persisted
     */
    public BlockEntityAttachmentStore(AttachmentRegistry registry)
    {
        this(registry, new ReflectiveValueDataAccess());
    }

    /**
     * Creates a store backed by the supplied value access.
     *
     * @param registry the registry whose capabilities are persisted
     * @param access   the non-null value access
     */
    public BlockEntityAttachmentStore(AttachmentRegistry registry, ValueDataAccess access)
    {
        super(registry, BLOCK_ENTITY_CLASS, access);
    }

    @Override
    public int priority()
    {
        return 50;
    }
}
