package net.weaver.api.game.event.mixin;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.weaver.api.game.event.support.GameEvents;
import net.weaver.api.game.event.player.ItemPickupEvent;
import net.weaver.api.game.event.player.ItemPickupPostEvent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Item pickup by players, before and after the inventory takes the stack. */
@Mixin(ItemEntity.class)
public abstract class PickupEventsMixin {
    @Inject(method = "playerTouch(Lnet/minecraft/world/entity/player/Player;)V", at = @At("HEAD"), cancellable = true)
    private void weaver$pickup(Player player, CallbackInfo callback) {
        if (!GameEvents.hasListeners(ItemPickupEvent.class)) {
            return;
        }
        ItemEntity self = (ItemEntity) (Object) this;
        if (self.level().isClientSide()) {
            return;
        }
        if (GameEvents.canceled(GameEvents.post(new ItemPickupEvent(player, self), self.level()))) {
            callback.cancel();
        }
    }

    @WrapOperation(method = "playerTouch(Lnet/minecraft/world/entity/player/Player;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;add(Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean weaver$pickupPost(Inventory inventory, ItemStack stack, Operation<Boolean> original) {
        if (!GameEvents.hasListeners(ItemPickupPostEvent.class)) {
            return original.call(inventory, stack);
        }
        ItemStack before = stack.copy();
        boolean added = original.call(inventory, stack);
        if (added) {
            ItemEntity self = (ItemEntity) (Object) this;
            GameEvents.post(new ItemPickupPostEvent(inventory.player, self, before), self.level());
        }
        return added;
    }
}
