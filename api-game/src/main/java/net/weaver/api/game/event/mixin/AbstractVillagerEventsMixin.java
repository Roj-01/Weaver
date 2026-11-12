package net.weaver.api.game.event.mixin;

import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.trading.MerchantOffer;
import net.weaver.api.game.event.player.TradeWithVillagerEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Completed villager and wandering trader trades. */
@Mixin(AbstractVillager.class)
public abstract class AbstractVillagerEventsMixin {
    @Inject(method = "notifyTrade(Lnet/minecraft/world/item/trading/MerchantOffer;)V", at = @At("RETURN"))
    private void weaver$trade(MerchantOffer offer, CallbackInfo callback) {
        AbstractVillager self = (AbstractVillager) (Object) this;
        GameEvents.post(new TradeWithVillagerEvent(self.getTradingPlayer(), offer, self), self.level());
    }
}
