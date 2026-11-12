package net.weaver.api.game.event.mixin;

import java.util.Set;
import net.minecraft.world.level.storage.loot.LootContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The tables, conditions, and functions a loot roll is inside; empty for a top-level roll. */
@Mixin(LootContext.class)
public interface LootContextAccessor {
    @Accessor("visitedElements")
    Set<?> weaver$visitedElements();
}
