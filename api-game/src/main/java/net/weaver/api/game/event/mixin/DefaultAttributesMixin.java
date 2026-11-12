package net.weaver.api.game.event.mixin;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.weaver.api.game.entity.EntityAttributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Mod-registered and extended attribute suppliers. */
@Mixin(DefaultAttributes.class)
public abstract class DefaultAttributesMixin {
    @Inject(method = "getSupplier", at = @At("HEAD"), cancellable = true)
    private static void weaver$ownSupplier(EntityType<? extends LivingEntity> type,
            CallbackInfoReturnable<AttributeSupplier> callback) {
        // Mod types have no vanilla supplier (vanilla's lookup would throw): answer first.
        if (EntityAttributes.isRegistered(type)) {
            callback.setReturnValue(EntityAttributes.adjust(type, null));
        }
    }

    @Inject(method = "getSupplier", at = @At("RETURN"), cancellable = true)
    private static void weaver$supplier(EntityType<? extends LivingEntity> type,
            CallbackInfoReturnable<AttributeSupplier> callback) {
        AttributeSupplier adjusted = EntityAttributes.adjust(type, callback.getReturnValue());
        if (adjusted != null) {
            callback.setReturnValue(adjusted);
        }
    }

    @Inject(method = "hasSupplier", at = @At("HEAD"), cancellable = true)
    private static void weaver$hasSupplier(EntityType<?> type, CallbackInfoReturnable<Boolean> callback) {
        if (EntityAttributes.isRegistered(type)) {
            callback.setReturnValue(true);
        }
    }
}
