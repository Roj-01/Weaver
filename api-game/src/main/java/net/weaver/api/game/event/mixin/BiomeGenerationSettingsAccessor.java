package net.weaver.api.game.event.mixin;

import java.util.List;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.levelgen.carver.WorldCarver;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Builds generation settings with changed features, used by biome feature modifiers. */
@Mixin(BiomeGenerationSettings.class)
public interface BiomeGenerationSettingsAccessor {
    @Accessor("carvers")
    HolderSet<WorldCarver> weaver$carvers();

    @Invoker("<init>")
    static BiomeGenerationSettings weaver$create(HolderSet<WorldCarver> carvers,
            List<HolderSet<PlacedFeature>> features) {
        throw new AssertionError();
    }
}
