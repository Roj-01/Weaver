package net.weaver.api.game.event.mixin;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The tracked entities of a level, for sending to the players that see an entity. */
@Mixin(ChunkMap.class)
public interface ChunkMapAccessor {
    @Accessor("entityMap")
    Int2ObjectMap<?> weaver$entityMap();
}
