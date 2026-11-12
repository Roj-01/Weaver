package net.weaver.api.game.event.mixin;

import java.util.Set;
import net.minecraft.server.network.ServerPlayerConnection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The connections of the players that see a tracked entity. */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public interface TrackedEntityAccessor {
    @Accessor("seenBy")
    Set<ServerPlayerConnection> weaver$seenBy();
}
