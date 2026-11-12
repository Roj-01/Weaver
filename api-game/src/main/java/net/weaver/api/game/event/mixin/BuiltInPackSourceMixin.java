package net.weaver.api.game.event.mixin;

import java.util.function.Consumer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.BuiltInPackSource;
import net.minecraft.server.packs.repository.Pack;
import net.weaver.api.game.data.ModPacks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mod packs next to vanilla's built-in packs. {@code BuiltInPackSource} is the
 * source of vanilla's data packs (server) and resource packs (client), so
 * mod packs appear wherever the game lists its own.
 */
@Mixin(BuiltInPackSource.class)
public abstract class BuiltInPackSourceMixin {
    @Shadow
    @Final
    private PackType packType;

    @Inject(method = "loadPacks(Ljava/util/function/Consumer;)V", at = @At("RETURN"))
    private void weaver$modPacks(Consumer<Pack> output, CallbackInfo callback) {
        ModPacks.addPacks(packType, output);
    }
}
