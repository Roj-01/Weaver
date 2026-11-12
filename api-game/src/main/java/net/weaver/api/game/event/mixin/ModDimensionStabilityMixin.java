package net.weaver.api.game.event.mixin;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.weaver.api.WeaverMods;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla calls every dimension other than the overworld, the nether, and the end
 * experimental, so a world with a mod's dimension opened behind the "experimental
 * settings" warning every time. A dimension in the namespace of an installed
 * Weaver mod is the mod's content and is stable like the rest of it. Changed
 * vanilla dimensions keep vanilla's judgement.
 */
@Mixin(WorldDimensions.class)
public abstract class ModDimensionStabilityMixin {
    @Inject(method = "checkStability", at = @At("HEAD"), cancellable = true)
    private static void weaver$modDimension(ResourceKey<LevelStem> key, LevelStem stem,
            CallbackInfoReturnable<com.mojang.serialization.Lifecycle> callback) {
        String namespace = key.identifier().getNamespace();
        if (!"minecraft".equals(namespace) && WeaverMods.find(namespace).isPresent()) {
            callback.setReturnValue(com.mojang.serialization.Lifecycle.stable());
        }
    }
}
