package net.weaver.api.game.event.mixin;

import java.util.Collection;
import java.util.List;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.weaver.api.game.data.ModPacks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps mod packs directly above vanilla in every selected-pack list. A world
 * that first sees a mod pack appends it at the top, above the world's own
 * data packs, which would let a mod override a world pack; vanilla's
 * {@code rebuildSelected} builds every selection, so it is reordered there.
 */
@Mixin(PackRepository.class)
public abstract class PackRepositoryMixin {
    @Inject(method = "rebuildSelected(Ljava/util/Collection;)Ljava/util/List;", at = @At("RETURN"), cancellable = true)
    private void weaver$modPacksAboveVanilla(Collection<String> ids, CallbackInfoReturnable<List<Pack>> callback) {
        List<Pack> ordered = ModPacks.placeAboveVanilla(callback.getReturnValue());
        if (ordered != null) {
            callback.setReturnValue(ordered);
        }
    }
}
