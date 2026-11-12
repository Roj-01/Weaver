package net.weaver.api.game.event.mixin;

import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Replaces a structure's settings, for structure modifiers. */
@Mixin(Structure.class)
public interface StructureAccessor {
    @Accessor("settings")
    Structure.StructureSettings weaver$settings();

    @Accessor("settings")
    @Mutable
    void weaver$setSettings(Structure.StructureSettings settings);
}
