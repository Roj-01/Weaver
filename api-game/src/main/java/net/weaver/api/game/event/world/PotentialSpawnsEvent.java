package net.weaver.api.game.event.world;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.weaver.api.event.Event;

/**
 * Fired when natural spawning looks up which mobs may spawn at a position
 * ({@code NaturalSpawner#mobsAt}): biome spawns, or structure spawns such as
 * nether fortress mobs. {@link #spawns()} is a mutable copy; add, remove, or
 * reweight entries. Fires very often during spawning; keep listeners cheap.
 */
public final class PotentialSpawnsEvent extends Event {
    private final ServerLevel level;
    private final MobCategory category;
    private final BlockPos pos;
    private final List<Weighted<MobSpawnSettings.SpawnerData>> spawns;

    public PotentialSpawnsEvent(ServerLevel level, MobCategory category, BlockPos pos,
                                List<Weighted<MobSpawnSettings.SpawnerData>> spawns) {
        this.level = level;
        this.category = category;
        this.pos = pos;
        this.spawns = new ArrayList<>(spawns);
    }

    public ServerLevel level() {
        return level;
    }

    public MobCategory category() {
        return category;
    }

    public BlockPos pos() {
        return pos;
    }

    /** The candidates, mutable. */
    public List<Weighted<MobSpawnSettings.SpawnerData>> spawns() {
        return spawns;
    }
}
