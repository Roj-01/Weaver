package net.weaver.api.game.event.mixin.client;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Invoker for the private {@code CreativeModeInventoryScreen#selectTab}, used by the tab page buttons. */
@Mixin(CreativeModeInventoryScreen.class)
public interface CreativeScreenInvoker {
    @Invoker("selectTab")
    void weaverSelectTab(CreativeModeTab tab);
}
