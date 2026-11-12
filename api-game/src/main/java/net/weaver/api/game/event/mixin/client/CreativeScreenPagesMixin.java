package net.weaver.api.game.event.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import net.weaver.api.game.creative.CreativeScreenPages;
import net.weaver.api.game.creative.CreativeTabPages;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Creative tab pages: the screen lists, draws and clicks only the current
 * page's tabs, gets page buttons and a page label, and turns to the page of
 * whatever tab is selected. The item tooltip still sees every tab.
 */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeScreenPagesMixin {
    @Shadow
    private static CreativeModeTab selectedTab;

    @WrapOperation(
            method = {"mouseClicked", "mouseReleased", "extractRenderState", "extractBackground"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/CreativeModeTabs;tabs()Ljava/util/List;"),
            require = 4)
    private List<CreativeModeTab> weaver$pageTabs(Operation<List<CreativeModeTab>> original) {
        return CreativeTabPages.visible(original.call());
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void weaver$pageButtons(CallbackInfo callback) {
        CreativeScreenPages.addButtons((CreativeModeInventoryScreen) (Object) this);
    }

    @Inject(method = "selectTab", at = @At("RETURN"))
    private void weaver$followTab(CallbackInfo callback) {
        CreativeScreenPages.follow(selectedTab);
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void weaver$pageLabel(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick,
            CallbackInfo callback) {
        CreativeScreenPages.drawLabel((CreativeModeInventoryScreen) (Object) this, graphics);
    }
}
