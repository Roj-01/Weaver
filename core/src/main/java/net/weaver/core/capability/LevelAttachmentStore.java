package net.weaver.core.capability;

import net.weaver.api.capability.AttachmentRegistry;
import net.weaver.core.data.ReflectiveValueDataAccess;
import net.weaver.core.data.ValueDataAccess;

/**
 * {@link net.weaver.api.capability.AttachmentStore} for game levels.
 *
 * <p>Values are held by weak identity. With {@code api-game}, server level
 * values are saved in a Weaver saved-data file per dimension
 * automatically.</p>
 */
public final class LevelAttachmentStore extends IdentityAttachmentStore
{
    static final String LEVEL_CLASS = "net.minecraft.world.level.Level";

    /**
     * Creates a store backed by the reflective value bridge.
     *
     * @param registry the registry whose capabilities are persisted
     */
    public LevelAttachmentStore(AttachmentRegistry registry)
    {
        this(registry, new ReflectiveValueDataAccess());
    }

    /**
     * Creates a store backed by the supplied value access.
     *
     * @param registry the registry whose capabilities are persisted
     * @param access   the non-null value access
     */
    public LevelAttachmentStore(AttachmentRegistry registry, ValueDataAccess access)
    {
        super(registry, LEVEL_CLASS, access);
    }

    @Override
    public int priority()
    {
        return 40;
    }
}
