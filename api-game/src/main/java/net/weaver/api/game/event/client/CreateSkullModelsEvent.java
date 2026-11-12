package net.weaver.api.game.event.client;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.object.skull.SkullModelBase;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.SkullBlock;
import net.weaver.api.event.Event;

/**
 * Fired once on the client, when the skull renderer is first loaded, to add
 * models and skins for a mod's own {@link SkullBlock.Type}. Models are used
 * everywhere vanilla draws skulls: placed skulls, skull items, and heads worn
 * by mobs and players. A factory registered for a vanilla type replaces its model.
 */
public final class CreateSkullModelsEvent extends Event {
    private final Map<SkullBlock.Type, Function<EntityModelSet, SkullModelBase>> models;
    private final Map<SkullBlock.Type, Identifier> skins;

    public CreateSkullModelsEvent(Map<SkullBlock.Type, Function<EntityModelSet, SkullModelBase>> models,
            Map<SkullBlock.Type, Identifier> skins) {
        this.models = models;
        this.skins = skins;
    }

    /** Registers the model factory of a skull type; it is called once per model set. */
    public void registerModel(SkullBlock.Type type, Function<EntityModelSet, SkullModelBase> factory) {
        models.put(Objects.requireNonNull(type, "type"), Objects.requireNonNull(factory, "factory"));
    }

    /** Registers the texture of a skull type, for example {@code mymod:textures/entity/my_mob.png}. */
    public void registerSkin(SkullBlock.Type type, Identifier texture) {
        skins.put(Objects.requireNonNull(type, "type"), Objects.requireNonNull(texture, "texture"));
    }
}
