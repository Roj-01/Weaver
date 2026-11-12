package net.weaver.api.game.event.mixin.extension;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.weaver.api.game.extension.ExtensionHooks;
import net.weaver.api.game.extension.ItemExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@link ItemExtension#onItemEntityTick}, {@link ItemExtension#getItemEntityLifespan},
 * and {@code BlockExtension.getFriction} for dropped items.
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityExtensionsMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void weaver$itemTick(CallbackInfo callback) {
        ItemEntity self = (ItemEntity) (Object) this;
        ItemStack stack = self.getItem();
        if (stack.getItem() instanceof ItemExtension extension && extension.onItemEntityTick(stack, self)) {
            callback.cancel();
        }
    }

    @ModifyConstant(method = "tick", constant = @Constant(intValue = 6000))
    private int weaver$lifespan(int vanilla) {
        ItemEntity self = (ItemEntity) (Object) this;
        ItemStack stack = self.getItem();
        return stack.getItem() instanceof ItemExtension extension
                ? extension.getItemEntityLifespan(stack, self.level()) : vanilla;
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/Block;getFriction()F"))
    private float weaver$friction(Block block, Operation<Float> original) {
        return ExtensionHooks.friction(block, (ItemEntity) (Object) this, original.call(block));
    }
}
