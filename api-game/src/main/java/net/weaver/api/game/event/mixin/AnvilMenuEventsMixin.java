package net.weaver.api.game.event.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.weaver.api.game.event.player.AnvilCraftPostEvent;
import net.weaver.api.game.event.player.AnvilCraftPreEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Anvil crafting. Slots 0 and 1 are the inputs. */
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuEventsMixin {
    @Unique
    private static final ThreadLocal<ItemStack[]> weaver$inputs = new ThreadLocal<>();

    // Canceling must happen before the result leaves the slot: in onTake it is already on the
    // cursor, so skipping the cost there would duplicate items.
    @Inject(method = "mayPickup(Lnet/minecraft/world/entity/player/Player;Z)Z", at = @At("RETURN"), cancellable = true)
    private void weaver$craftPre(Player player, boolean hasItem, CallbackInfoReturnable<Boolean> callback) {
        if (!callback.getReturnValueZ() || !GameEvents.hasListeners(AnvilCraftPreEvent.class)) {
            return;
        }
        AnvilMenu self = (AnvilMenu) (Object) this;
        ItemStack output = self.getSlot(self.getResultSlot()).getItem();
        if (GameEvents.canceled(GameEvents.post(new AnvilCraftPreEvent(player, self, output,
                self.getSlot(0).getItem(), self.getSlot(1).getItem()), player.level()))) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "onTake(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"))
    private void weaver$craftInputs(Player player, ItemStack carried, CallbackInfo callback) {
        AnvilMenu self = (AnvilMenu) (Object) this;
        weaver$inputs.set(new ItemStack[] {self.getSlot(0).getItem().copy(), self.getSlot(1).getItem().copy()});
    }

    @Inject(method = "onTake(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)V", at = @At("RETURN"))
    private void weaver$craftPost(Player player, ItemStack carried, CallbackInfo callback) {
        ItemStack[] inputs = weaver$inputs.get();
        weaver$inputs.remove();
        if (inputs != null) {
            AnvilMenu self = (AnvilMenu) (Object) this;
            GameEvents.post(new AnvilCraftPostEvent(player, self, carried, inputs[0], inputs[1]), player.level());
        }
    }
}
