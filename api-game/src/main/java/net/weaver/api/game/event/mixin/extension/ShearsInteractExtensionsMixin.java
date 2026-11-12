package net.weaver.api.game.event.mixin.extension;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.LeashFenceKnotEntity;
import net.minecraft.world.item.ItemStack;
import net.weaver.api.game.extension.ExtensionHooks;
import net.weaver.api.game.extension.ItemAction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** {@link ItemAction#SHEAR}: cutting leads from a leashed entity or a fence knot. */
@Mixin({Entity.class, LeashFenceKnotEntity.class})
public abstract class ShearsInteractExtensionsMixin {
    @WrapOperation(method = "interact", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;is(Ljava/lang/Object;)Z"))
    private boolean weaver$shears(ItemStack stack, Object item, Operation<Boolean> original) {
        return ExtensionHooks.isOrShears(stack, item, original.call(stack, item));
    }
}
