package net.weaver.api.game.event.mixin;

import com.mojang.serialization.Dynamic;
import java.util.Map;
import net.minecraft.util.datafix.fixes.StructuresBecomeConfiguredFix;
import net.weaver.api.game.event.support.StructureConversions;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Mod entries in the pre-1.18.2 structure upgrade, added the first time it runs. */
@Mixin(StructuresBecomeConfiguredFix.class)
public abstract class StructureConversionsEventsMixin {
    @Shadow
    @Final
    @Mutable
    private static Map<String, ?> CONVERSION_MAP;

    @Unique
    private static volatile boolean weaver$extended;

    @Inject(method = "findUpdatedStructureType(Lcom/mojang/serialization/Dynamic;Lcom/mojang/serialization/Dynamic;)Lcom/mojang/serialization/Dynamic;",
            at = @At("HEAD"))
    private void weaver$extend(Dynamic<?> chunk, Dynamic<?> structure, CallbackInfoReturnable<Dynamic<?>> callback) {
        weaver$ensure();
    }

    @Unique
    private static synchronized void weaver$ensure() {
        if (!weaver$extended) {
            CONVERSION_MAP = StructureConversions.extend(CONVERSION_MAP);
            weaver$extended = true;
        }
    }

    /** For the proof: the table after mod entries were added. */
    @Unique
    private static Map<String, ?> weaver$table() {
        weaver$ensure();
        return CONVERSION_MAP;
    }
}
