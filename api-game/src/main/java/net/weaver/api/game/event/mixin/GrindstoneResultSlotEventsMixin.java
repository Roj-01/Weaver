package net.weaver.api.game.event.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import net.weaver.api.game.event.player.GrindstoneTakeEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Taking the grindstone's result. The result slot is the fourth anonymous
 * class in {@code GrindstoneMenu} (the only one that overrides
 * {@code onTake}); the proof's mixin-applied check fails if a game update
 * renumbers it.
 */
@Mixin(targets = "net.minecraft.world.inventory.GrindstoneMenu$4")
public abstract class GrindstoneResultSlotEventsMixin {
    @Shadow
    @Final
    GrindstoneMenu this$0;

    /**
     * Added to the result slot (vanilla inherits {@code Slot#mayPickup}, which allows it): a
     * canceled event refuses the take before the result leaves the slot, so nothing is duplicated.
     */
    public boolean mayPickup(Player player) {
        return weaver$allowTake(player);
    }

    @org.spongepowered.asm.mixin.Unique
    private boolean weaver$allowTake(Player player) {
        if (!GameEvents.hasListeners(GrindstoneTakeEvent.class)) {
            return true;
        }
        ItemStack output = ((net.minecraft.world.inventory.Slot) (Object) this).getItem();
        return !GameEvents.canceled(GameEvents.post(new GrindstoneTakeEvent(player, this$0, output), player.level()));
    }
}
