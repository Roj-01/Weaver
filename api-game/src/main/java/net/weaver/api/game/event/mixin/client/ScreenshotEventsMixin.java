package net.weaver.api.game.event.mixin.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.File;
import java.util.function.Consumer;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.game.event.client.ScreenshotEvent;
import net.weaver.api.game.event.support.GameEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Screenshots about to be written. {@code lambda$grab$3} is the background
 * task that writes the image, reports the result, and closes the image; a
 * canceled screenshot does the reporting and closing itself.
 */
@Mixin(Screenshot.class)
public abstract class ScreenshotEventsMixin {
    @Inject(method = "lambda$grab$3(Lcom/mojang/blaze3d/platform/NativeImage;Ljava/io/File;Ljava/util/function/Consumer;)V",
            at = @At("HEAD"), cancellable = true)
    private static void weaver$screenshot(NativeImage image, File file, Consumer<Component> callback, CallbackInfo info) {
        if (!GameEvents.hasListeners(ScreenshotEvent.class)) {
            return;
        }
        ScreenshotEvent event = GameEvents.post(new ScreenshotEvent(image, file), LogicalSide.CLIENT);
        if (GameEvents.canceled(event)) {
            try {
                callback.accept(event.cancelMessage());
            } finally {
                image.close();
            }
            info.cancel();
        }
    }
}
