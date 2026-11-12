package net.weaver.api.game.event.support;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** The client side of {@link CrashShield}: failing screens are closed. Client only. */
public final class ClientCrashShield {
    private ClientCrashShield() {
    }

    /** Reports a screen failure and closes the screen on the next client task. */
    public static void screenFailed(Screen screen, String phase, Throwable failure) {
        String name = screen == null ? "none" : screen.getClass().getName();
        CrashShield.report("screen-" + phase, name, screen == null ? "ignored" : "closed", failure);
        if (screen == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            if (minecraft.gui.screen() == screen) {
                minecraft.gui.setScreen(null);
            }
        });
    }

    /** Reports an input-handler failure and closes the open screen, the usual source. */
    public static void inputFailed(String handler, Throwable failure) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = minecraft.gui.screen();
        if (screen != null) {
            screenFailed(screen, handler, failure);
        } else {
            CrashShield.report("input-" + handler, "in-game", "ignored", failure);
        }
    }
}
