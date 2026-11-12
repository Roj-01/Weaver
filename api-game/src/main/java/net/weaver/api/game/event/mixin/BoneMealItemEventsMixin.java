package net.weaver.api.game.event.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.weaver.api.game.event.block.BonemealEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Bone meal use. {@code growCrop} is static and has no player, so the player
 * from {@code useOn} is remembered for the duration of that call; dispensers
 * call {@code growCrop} directly and report no player.
 */
@Mixin(BoneMealItem.class)
public abstract class BoneMealItemEventsMixin {
    @Unique
    private static final ThreadLocal<Player> weaver$user = new ThreadLocal<>();

    @Inject(method = "useOn(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;",
            at = @At("HEAD"))
    private void weaver$rememberUser(UseOnContext context, CallbackInfoReturnable<InteractionResult> callback) {
        weaver$user.set(context.getPlayer());
    }

    @Inject(method = "useOn(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;",
            at = @At("RETURN"))
    private void weaver$forgetUser(UseOnContext context, CallbackInfoReturnable<InteractionResult> callback) {
        weaver$user.remove();
    }

    @Inject(method = "growCrop(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Z",
            at = @At("HEAD"), cancellable = true)
    private static void weaver$bonemeal(ItemStack stack, Level level, BlockPos pos,
                                        CallbackInfoReturnable<Boolean> callback) {
        BonemealEvent event = new BonemealEvent(level, pos, level.getBlockState(pos), stack, weaver$user.get());
        if (GameEvents.canceled(GameEvents.post(event, level))) {
            callback.setReturnValue(false);
        }
    }
}
