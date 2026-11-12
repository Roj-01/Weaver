package net.weaver.api.game.client.mixin;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.weaver.api.game.client.gui.MenuButtons;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A "Mods" button on the title screen: the Realms button is halved and the
 * button takes the left half; without a Realms button it goes below the menu.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenModsMixin extends Screen {
    protected TitleScreenModsMixin(Component title) {
        super(title);
    }

    @Inject(method = "init()V", at = @At("RETURN"))
    private void weaver$modsButton(CallbackInfo callback) {
        try {
            Button realms = MenuButtons.find(children(), "menu.online");
            Button button;
            if (realms != null) {
                int half = (realms.getWidth() - 4) / 2;
                // Mods on the left half, Realms on the right: Realms draws its notification
                // badges at the right end of its row, which must stay on the Realms button.
                button = MenuButtons.mods(realms.getX(), realms.getY(), half);
                realms.setWidth(half);
                realms.setX(realms.getX() + half + 4);
            } else {
                button = MenuButtons.mods(width / 2 - 100, height / 4 + 48 + 72 + 36, 200);
            }
            addRenderableWidget(button);
        } catch (RuntimeException failure) {
            System.out.println("[weaver] MODS BUTTON FAILED screen=title reason=" + failure);
        }
    }
}
