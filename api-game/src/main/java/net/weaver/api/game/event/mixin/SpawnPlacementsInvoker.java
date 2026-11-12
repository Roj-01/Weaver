package net.weaver.api.game.event.mixin;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacementType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The private spawn placement registration of vanilla, for mod types. */
@Mixin(SpawnPlacements.class)
public interface SpawnPlacementsInvoker {
    @Invoker("register")
    static <T extends Mob> void weaver$register(EntityType<T> type, SpawnPlacementType placement, Heightmap.Types heightmap,
            SpawnPlacements.SpawnPredicate<T> predicate) {
        throw new AssertionError("mixin not applied");
    }
}
