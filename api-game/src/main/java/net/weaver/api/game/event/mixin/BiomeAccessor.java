package net.weaver.api.game.event.mixin;

import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Replaces a biome's generation settings, used by biome feature modifiers. */
@Mixin(Biome.class)
public interface BiomeAccessor {
    @Accessor("generationSettings")
    @Mutable
    void weaver$setGenerationSettings(BiomeGenerationSettings settings);
}
