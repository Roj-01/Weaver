package net.weaver.api.game.event.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.game.event.player.GrindstoneResultEvent;
import net.weaver.api.game.event.support.GameEvents;
import net.weaver.api.game.event.support.MenuLevels;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The grindstone's result, computed whenever its inputs change. A menu with
 * no level access is the client's copy, so the event is posted as client side.
 */
@Mixin(GrindstoneMenu.class)
public abstract class GrindstoneMenuEventsMixin {
    @Shadow
    @Final
    private ContainerLevelAccess access;

    @WrapOperation(method = "createResult()V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/inventory/GrindstoneMenu;computeResult(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack weaver$result(GrindstoneMenu self, ItemStack top, ItemStack bottom, Operation<ItemStack> original) {
        ItemStack result = original.call(self, top, bottom);
        if (!GameEvents.hasListeners(GrindstoneResultEvent.class)) {
            return result;
        }
        GrindstoneResultEvent event = new GrindstoneResultEvent(self, top, bottom, result);
        Level level = MenuLevels.levelOf(access);
        if (level == null) {
            GameEvents.post(event, LogicalSide.CLIENT);
        } else {
            GameEvents.post(event, level);
        }
        return GameEvents.canceled(event) ? ItemStack.EMPTY : event.result();
    }
}
