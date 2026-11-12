package net.weaver.api.game.event.mixin;

import java.util.Collection;
import java.util.Set;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.game.event.support.GameEvents;
import net.weaver.api.game.event.world.BuildCreativeTabContentsEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Creative tab contents. At the end of {@code buildContents} the tab holds
 * its new item sets (stack sets keyed on item and components), so the event
 * adds to them directly.
 */
@Mixin(CreativeModeTab.class)
public abstract class CreativeModeTabEventsMixin {
    @Shadow
    private Collection<ItemStack> displayItems;

    @Shadow
    private Set<ItemStack> displayItemsSearchTab;

    @Inject(method = "buildContents(Lnet/minecraft/world/item/CreativeModeTab$ItemDisplayParameters;)V", at = @At("RETURN"))
    private void weaver$contents(CreativeModeTab.ItemDisplayParameters parameters, CallbackInfo callback) {
        if (GameEvents.hasListeners(BuildCreativeTabContentsEvent.class)) {
            GameEvents.post(new BuildCreativeTabContentsEvent((CreativeModeTab) (Object) this, parameters,
                    displayItems, displayItemsSearchTab), LogicalSide.CLIENT);
        }
    }
}
