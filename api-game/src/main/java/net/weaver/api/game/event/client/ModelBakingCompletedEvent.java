package net.weaver.api.game.event.client;

import net.minecraft.client.resources.model.ModelManager;
import net.weaver.api.event.Event;

/**
 * Fired on the client after a resource reload baked every block, item, and
 * entity model and the model manager switched to them ({@code ModelManager#apply}).
 * Look up and cache baked models here.
 */
public final class ModelBakingCompletedEvent extends Event {
    private final ModelManager modelManager;

    public ModelBakingCompletedEvent(ModelManager modelManager) {
        this.modelManager = modelManager;
    }

    public ModelManager modelManager() {
        return modelManager;
    }
}
