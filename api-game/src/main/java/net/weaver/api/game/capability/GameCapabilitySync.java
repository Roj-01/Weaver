package net.weaver.api.game.capability;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.weaver.api.capability.CapabilitySyncProvider;
import net.weaver.api.game.network.GameNetworkBridge;
import net.weaver.api.network.NetworkConnection;

import java.util.List;
import java.util.UUID;

/**
 * Game-typed holder identity provider for capability synchronization.
 *
 * <p>The provider names common 26.3 holders with stable keys so the loader can
 * install the core capability channel without knowing game types:</p>
 *
 * <ul>
 *   <li>entity: the entity UUID;</li>
 *   <li>block entity: {@code <dimension>|<block-pos-long>};</li>
 *   <li>chunk: {@code <dimension>|<chunk-pos-long>};</li>
 *   <li>level: {@code <dimension>}.</li>
 * </ul>
 *
 * <p>The reverse lookup is client-only. It reads {@code Minecraft.level}
 * reflectively so this class can load on a dedicated server without resolving
 * client classes. Unknown kinds and unavailable holders return {@code null};
 * the channel then logs and skips instead of applying a value to the wrong
 * object.</p>
 */
public final class GameCapabilitySync implements CapabilitySyncProvider
{
    private static final GameCapabilitySync INSTANCE = new GameCapabilitySync();

    private static final String ENTITY = "entity";
    private static final String BLOCK_ENTITY = "block_entity";
    private static final String CHUNK = "chunk";
    private static final String LEVEL = "level";
    private static final String SEPARATOR = "|";

    private GameCapabilitySync()
    {
    }

    /**
     * The shared provider instance installed by the loader.
     *
     * @return the provider, never {@code null}
     */
    public static CapabilitySyncProvider provider()
    {
        return INSTANCE;
    }

    /**
     * The current server-side bridge connections that can receive payloads.
     *
     * <p>Used as the default recipient supplier for runtime capability changes.
     * The list is empty on a client or before the bridge reaches play.</p>
     *
     * @return an immutable recipient snapshot, never {@code null}
     */
    public static List<NetworkConnection> recipients()
    {
        return GameNetworkBridge.connections();
    }

    @Override
    public boolean isRemoteCopy(Object holder)
    {
        Level level = levelOf(holder);
        return level != null && level.isClientSide();
    }

    /** Players far from (or in another level than) the holder do not track it and skip the change. */
    @Override
    public boolean shouldReceive(Object holder, NetworkConnection connection)
    {
        Level level = levelOf(holder);
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)
                || !(connection instanceof net.weaver.api.game.network.BridgeSession session)
                || session.player() == null)
        {
            return true;
        }
        net.minecraft.server.level.ServerPlayer player =
                serverLevel.getServer().getPlayerList().getPlayer(session.player().id());
        if (player == null)
        {
            return true;
        }
        if (player == holder || holder instanceof Level)
        {
            return player == holder || player.level() == level;
        }
        if (player.level() != level)
        {
            return false;
        }
        double range = (serverLevel.getServer().getPlayerList().getViewDistance() + 2) * 16.0;
        net.minecraft.world.phys.Vec3 position = positionOf(holder);
        return position == null || player.position().distanceToSqr(position) <= range * range;
    }

    private static Level levelOf(Object holder)
    {
        if (holder instanceof Entity entity)
        {
            return entity.level();
        }
        if (holder instanceof BlockEntity blockEntity)
        {
            return blockEntity.getLevel();
        }
        if (holder instanceof LevelChunk chunk)
        {
            return chunk.getLevel();
        }
        return holder instanceof Level level ? level : null;
    }

    private static net.minecraft.world.phys.Vec3 positionOf(Object holder)
    {
        if (holder instanceof Entity entity)
        {
            return entity.position();
        }
        if (holder instanceof BlockEntity blockEntity)
        {
            return net.minecraft.world.phys.Vec3.atCenterOf(blockEntity.getBlockPos());
        }
        if (holder instanceof LevelChunk chunk)
        {
            return new net.minecraft.world.phys.Vec3(chunk.getPos().getMiddleBlockX(), 64,
                    chunk.getPos().getMiddleBlockZ());
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String holderKind(Object holder)
    {
        if (holder instanceof Entity)
        {
            return ENTITY;
        }
        if (holder instanceof BlockEntity)
        {
            return BLOCK_ENTITY;
        }
        if (holder instanceof LevelChunk)
        {
            return CHUNK;
        }
        if (holder instanceof Level)
        {
            return LEVEL;
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String holderKey(Object holder)
    {
        if (holder instanceof Entity entity)
        {
            return entity.getUUID().toString();
        }
        if (holder instanceof BlockEntity blockEntity)
        {
            Level level = blockEntity.getLevel();
            if (level == null)
            {
                return null;
            }
            return dimensionKey(level) + SEPARATOR + blockEntity.getBlockPos().asLong();
        }
        if (holder instanceof LevelChunk chunk)
        {
            Level level = chunk.getLevel();
            if (level == null)
            {
                return null;
            }
            return dimensionKey(level) + SEPARATOR + chunk.getPos().pack();
        }
        if (holder instanceof Level level)
        {
            return dimensionKey(level);
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object resolve(String holderKind, String holderKey)
    {
        if (holderKind == null || holderKey == null)
        {
            return null;
        }
        try
        {
            Level level = clientLevel();
            if (level == null)
            {
                return null;
            }
            return switch (holderKind)
            {
                case ENTITY -> level.getEntity(UUID.fromString(holderKey));
                case BLOCK_ENTITY -> resolveBlockEntity(level, holderKey);
                case CHUNK -> resolveChunk(level, holderKey);
                case LEVEL -> dimensionKey(level).equals(holderKey) ? level : null;
                default -> null;
            };
        }
        catch (Throwable failure)
        {
            return null;
        }
    }

    private static Object resolveBlockEntity(Level level, String key)
    {
        int separator = key.lastIndexOf(SEPARATOR);
        if (separator <= 0)
        {
            return null;
        }
        String dimension = key.substring(0, separator);
        if (!dimensionKey(level).equals(dimension))
        {
            return null;
        }
        long packed = Long.parseLong(key.substring(separator + SEPARATOR.length()));
        return level.getBlockEntity(BlockPos.of(packed));
    }

    private static Object resolveChunk(Level level, String key)
    {
        int separator = key.lastIndexOf(SEPARATOR);
        if (separator <= 0)
        {
            return null;
        }
        String dimension = key.substring(0, separator);
        if (!dimensionKey(level).equals(dimension))
        {
            return null;
        }
        long packed = Long.parseLong(key.substring(separator + SEPARATOR.length()));
        ChunkPos pos = ChunkPos.unpack(packed);
        return level.getChunk(pos.x(), pos.z());
    }

    private static Level clientLevel() throws ReflectiveOperationException
    {
        Class<?> minecraftClass = Class.forName("net.minecraft.client.Minecraft");
        Object minecraft = minecraftClass.getMethod("getInstance").invoke(null);
        if (minecraft == null)
        {
            return null;
        }
        Object level = minecraftClass.getField("level").get(minecraft);
        return level instanceof Level gameLevel ? gameLevel : null;
    }

    private static String dimensionKey(Level level)
    {
        return String.valueOf(level.dimension().identifier());
    }
}
