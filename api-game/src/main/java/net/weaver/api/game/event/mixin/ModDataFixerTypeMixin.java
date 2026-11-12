package net.weaver.api.game.event.mixin;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.types.Type;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mod block entity and entity types have no data fixer schema: when the game
 * checks the schemas (development runs), {@code Util.fetchChoiceType} would log an
 * error, or throw, for every one of them. Ids outside the {@code minecraft}
 * namespace get no choice type, as in a normal run.
 */
@Mixin(Util.class)
public abstract class ModDataFixerTypeMixin {
    @Inject(method = "fetchChoiceType", at = @At("HEAD"), cancellable = true)
    private static void weaver$modTypes(DSL.TypeReference reference, String id, CallbackInfoReturnable<Type<?>> callback) {
        if (id != null && id.indexOf(':') > 0 && !id.startsWith("minecraft:")) {
            callback.setReturnValue(null);
        }
    }
}
