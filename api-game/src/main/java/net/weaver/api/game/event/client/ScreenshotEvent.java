package net.weaver.api.game.event.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.File;
import java.util.Objects;
import net.minecraft.network.chat.Component;
import net.weaver.api.event.Event;

/**
 * Fired when a screenshot is about to be written to disk
 * ({@code Screenshot#grab}, in the background task that saves it; this runs
 * on an I/O thread, not the render thread). Cancelable: a canceled screenshot
 * is not saved, and {@link #cancelMessage()} is shown in chat instead of the
 * "saved as" message.
 */
public final class ScreenshotEvent extends Event {
    private final NativeImage image;
    private final File file;
    private Component cancelMessage = Component.translatable("screenshot.failure", "canceled");

    public ScreenshotEvent(NativeImage image, File file) {
        super(true);
        this.image = image;
        this.file = file;
    }

    /** The captured image; do not close it. */
    public NativeImage image() {
        return image;
    }

    /** Where the screenshot will be written. */
    public File file() {
        return file;
    }

    public Component cancelMessage() {
        return cancelMessage;
    }

    public void setCancelMessage(Component cancelMessage) {
        this.cancelMessage = Objects.requireNonNull(cancelMessage, "cancelMessage");
    }
}
