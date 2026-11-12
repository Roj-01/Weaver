package net.weaver.api.game.client.mixin;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.weaver.api.game.client.gui.MenuButtons;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A "Mods" button in the pause menu, in the row above "Disconnect" (which moves down one row). */
@Mixin(PauseScreen.class)
public abstract class PauseScreenModsMixin extends Screen {
    @Shadow
    @Final
    private boolean showPauseMenu;

    @Shadow
    private Button disconnectButton;

    protected PauseScreenModsMixin(Component title) {
        super(title);
    }

    @Inject(method = "init()V", at = @At("RETURN"))
    private void weaver$modsButton(CallbackInfo callback) {
        if (!showPauseMenu || disconnectButton == null) {
            return;
        }
        try {
            Button button = MenuButtons.mods(disconnectButton.getX(), disconnectButton.getY(), disconnectButton.getWidth());
            disconnectButton.setY(disconnectButton.getY() + 24);
            addRenderableWidget(button);
        } catch (RuntimeException failure) {
            System.out.println("[weaver] MODS BUTTON FAILED screen=pause reason=" + failure);
        }
    }
}
