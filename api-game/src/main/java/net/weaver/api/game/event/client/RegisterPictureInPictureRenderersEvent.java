package net.weaver.api.game.event.client;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.weaver.api.event.Event;

/**
 * Fired once on the client while the GUI renderer is created, to add renderers
 * that draw 3D content into a screen region (like the entity in the inventory
 * screen). A renderer handles one {@code PictureInPictureRenderState} class; a
 * screen submits such a state to the GUI render state to use it.
 */
public final class RegisterPictureInPictureRenderersEvent extends Event {
    private final Minecraft minecraft;
    private final List<PictureInPictureRenderer<?>> renderers;

    public RegisterPictureInPictureRenderersEvent(Minecraft minecraft, List<PictureInPictureRenderer<?>> renderers) {
        this.minecraft = minecraft;
        this.renderers = renderers;
    }

    public void register(PictureInPictureRenderer<?> renderer) {
        renderers.add(Objects.requireNonNull(renderer, "renderer"));
    }

    /** The client; its entity render dispatcher and model sets are ready. */
    public Minecraft minecraft() {
        return minecraft;
    }

    /** The renderers so far, vanilla included (read-only). */
    public List<PictureInPictureRenderer<?>> renderers() {
        return Collections.unmodifiableList(renderers);
    }
}
