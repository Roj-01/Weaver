package net.weaver.api.game.event.mixin.extension;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.weaver.api.game.extension.EntityExtension;
import net.weaver.api.game.extension.ItemExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** {@link EntityExtension#walksOnPowderSnow} and {@link ItemExtension#allowsWalkingOnPowderSnow}. */
@Mixin(PowderSnowBlock.class)
public abstract class PowderSnowExtensionsMixin {
    @Inject(method = "canEntityWalkOnPowderSnow", at = @At("HEAD"), cancellable = true)
    private static void weaver$entityWalks(Entity entity, CallbackInfoReturnable<Boolean> callback) {
        if (entity instanceof EntityExtension extension && extension.walksOnPowderSnow()) {
            callback.setReturnValue(true);
        }
    }

    @WrapOperation(method = "canEntityWalkOnPowderSnow", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;is(Ljava/lang/Object;)Z"))
    private static boolean weaver$boots(ItemStack boots, Object item, Operation<Boolean> original,
            @Local(argsOnly = true) Entity entity) {
        if (boots.getItem() instanceof ItemExtension extension && entity instanceof LivingEntity wearer
                && extension.allowsWalkingOnPowderSnow(boots, wearer)) {
            return true;
        }
        return original.call(boots, item);
    }
}
