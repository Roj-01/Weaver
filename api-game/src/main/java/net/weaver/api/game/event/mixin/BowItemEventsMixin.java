package net.weaver.api.game.event.mixin;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.weaver.api.game.event.player.ArrowLooseEvent;
import net.weaver.api.game.event.player.ArrowNockEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Drawing and releasing bows. */
@Mixin(BowItem.class)
public abstract class BowItemEventsMixin {
    @Unique
    private static final ThreadLocal<Object> weaver$reinvoking = new ThreadLocal<>();

    @Inject(method = "use(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;",
            at = @At("HEAD"), cancellable = true)
    private void weaver$nock(Level level, Player player, InteractionHand hand,
                             CallbackInfoReturnable<InteractionResult> callback) {
        ItemStack bow = player.getItemInHand(hand);
        if (GameEvents.canceled(GameEvents.post(new ArrowNockEvent(player, bow, hand), level))) {
            callback.setReturnValue(InteractionResult.FAIL);
        }
    }

    @Inject(method = "releaseUsing(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;I)Z",
            at = @At("HEAD"), cancellable = true)
    private void weaver$loose(ItemStack bow, Level level, LivingEntity shooter, int timeLeft,
                              CallbackInfoReturnable<Boolean> callback) {
        if (weaver$reinvoking.get() == this) {
            return;
        }
        BowItem self = (BowItem) (Object) this;
        int useDuration = self.getUseDuration(bow, shooter);
        int charge = useDuration - timeLeft;
        ArrowLooseEvent event = GameEvents.post(new ArrowLooseEvent(shooter, bow, charge), level);
        if (GameEvents.canceled(event)) {
            callback.setReturnValue(false);
            return;
        }
        if (event.charge() != charge) {
            Object previous = weaver$reinvoking.get();
            weaver$reinvoking.set(this);
            try {
                callback.setReturnValue(self.releaseUsing(bow, level, shooter, useDuration - event.charge()));
            } finally {
                weaver$reinvoking.set(previous);
            }
        }
    }
}
