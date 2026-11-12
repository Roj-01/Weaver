package net.weaver.api.game.event.client;

import java.util.Map;
import java.util.Objects;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.MaterialBaker;
import net.minecraft.world.level.material.Fluid;
import net.weaver.api.event.Event;

/**
 * Fired on each resource reload while fluid models are baked
 * ({@code FluidStateModelSet#bake}), so mod fluids can be drawn with their own
 * still, flowing, and overlay textures. Register both the source and the
 * flowing fluid. Runs on a reload worker thread.
 */
public final class RegisterFluidModelsEvent extends Event {
    private final MaterialBaker baker;
    private final Map<Fluid, FluidModel> models;

    public RegisterFluidModelsEvent(MaterialBaker baker, Map<Fluid, FluidModel> models) {
        this.baker = baker;
        this.models = models;
    }

    /** Bakes and registers the model of a fluid. */
    public void register(Fluid fluid, FluidModel.Unbaked model) {
        Objects.requireNonNull(fluid, "fluid");
        String name = fluid.builtInRegistryHolder().getRegisteredName();
        models.put(fluid, Objects.requireNonNull(model, "model").bake(baker, () -> name));
    }

    /** Registers an already baked fluid model. */
    public void register(Fluid fluid, FluidModel model) {
        models.put(Objects.requireNonNull(fluid, "fluid"), Objects.requireNonNull(model, "model"));
    }

    public MaterialBaker baker() {
        return baker;
    }

    /** The models so far, vanilla water and lava included (read-write). */
    public Map<Fluid, FluidModel> models() {
        return models;
    }
}
