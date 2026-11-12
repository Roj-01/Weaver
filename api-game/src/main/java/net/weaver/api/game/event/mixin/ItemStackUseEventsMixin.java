package net.weaver.api.game.event.mixin;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.weaver.api.game.event.player.UseItemOnBlockEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The item phase of using an item on a block. */
@Mixin(ItemStack.class)
public abstract class ItemStackUseEventsMixin {
    @Inject(method = "useOn(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;",
            at = @At("HEAD"), cancellable = true)
    private void weaver$useOn(UseOnContext context, CallbackInfoReturnable<InteractionResult> callback) {
        if (!GameEvents.hasListeners(UseItemOnBlockEvent.class)) {
            return;
        }
        BlockHitResult hit = new BlockHitResult(context.getClickLocation(), context.getClickedFace(),
                context.getClickedPos(), context.isInside());
        UseItemOnBlockEvent event = GameEvents.post(new UseItemOnBlockEvent(context.getPlayer(), context.getLevel(),
                context.getHand(), (ItemStack) (Object) this, hit, UseItemOnBlockEvent.Phase.ITEM), context.getLevel());
        if (GameEvents.canceled(event)) {
            callback.setReturnValue(event.result());
        }
    }
}
