package net.weaver.api.game.event.mixin;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.BlockHitResult;
import net.weaver.api.game.event.player.UseItemOnBlockEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The block phase of using an item on a block. */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class UseItemOnBlockEventsMixin {
    @Inject(method = "useItemOn(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;",
            at = @At("HEAD"), cancellable = true)
    private void weaver$useOnBlock(ItemStack stack, Level level, Player player, InteractionHand hand, BlockHitResult hit,
                                   CallbackInfoReturnable<InteractionResult> callback) {
        if (!GameEvents.hasListeners(UseItemOnBlockEvent.class)) {
            return;
        }
        UseItemOnBlockEvent event = GameEvents.post(
                new UseItemOnBlockEvent(player, level, hand, stack, hit, UseItemOnBlockEvent.Phase.BLOCK), level);
        if (GameEvents.canceled(event)) {
            callback.setReturnValue(event.result());
        }
    }
}
