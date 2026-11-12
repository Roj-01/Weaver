package net.weaver.nf;

import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.weaver.api.game.client.blockentity.BlockEntityRendererRegistry;
import net.weaver.api.game.client.entity.EntityRendererRegistry;
import net.weaver.api.game.client.model.ModelLayerRegistry;

/**
 * Entity rendering registrations, as NeoForge's {@code EntityRenderersEvent}:
 * {@code EntityRenderersEvent.RENDERERS.registerEntityRenderer(type, Provider::new)}
 * and {@code LAYERS.registerLayerDefinition(location, Model::createLayer)}, backed
 * by Weaver's renderer and model layer registries. Call them from
 * {@code onClientSetup}.
 */
public final class EntityRenderersEvent {
    public static final RegisterLayerDefinitions LAYERS = new RegisterLayerDefinitions();
    public static final RegisterRenderers RENDERERS = new RegisterRenderers();

    private EntityRenderersEvent() {
    }

    /** Model layer definitions. */
    public static final class RegisterLayerDefinitions {
        public RegisterLayerDefinitions() {
        }

        public void registerLayerDefinition(ModelLayerLocation location, Supplier<LayerDefinition> definition) {
            ModelLayerRegistry.register(location, definition.get());
        }
    }

    /** Entity and block entity renderers. */
    public static final class RegisterRenderers {
        public RegisterRenderers() {
        }

        public <T extends Entity> void registerEntityRenderer(EntityType<? extends T> type, EntityRendererProvider<T> provider) {
            registerUnchecked(type, provider);
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private static void registerUnchecked(EntityType type, EntityRendererProvider provider) {
            EntityRendererRegistry.register(type, provider);
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        public <T extends BlockEntity, S extends BlockEntityRenderState> void registerBlockEntityRenderer(
                BlockEntityType<? extends T> type, BlockEntityRendererProvider<T, S> provider) {
            BlockEntityRendererRegistry.register((BlockEntityType) type, (BlockEntityRendererProvider) provider);
        }
    }

    /**
     * The renderers after they were built, to add layers to, as NeoForge's
     * {@code EntityRenderersEvent.AddLayers}. With Weaver, add layers through
     * {@code EntityLayerRegistry}; this view is offered for code that reads the
     * renderers.
     */
    public static final class AddLayers {
        private final Map<EntityType<?>, EntityRenderer<?, ?>> renderers;
        private final Map<PlayerModelType, ? extends AvatarRenderer<?>> players;
        private final Map<PlayerModelType, ? extends AvatarRenderer<?>> mannequins;
        private final EntityModelSet models;

        public AddLayers(Map<EntityType<?>, EntityRenderer<?, ?>> renderers,
                Map<PlayerModelType, ? extends AvatarRenderer<?>> players,
                Map<PlayerModelType, ? extends AvatarRenderer<?>> mannequins, EntityModelSet models) {
            this.renderers = renderers;
            this.players = players;
            this.mannequins = mannequins;
            this.models = models;
        }

        public EntityModelSet getEntityModels() {
            return models;
        }

        public Set<PlayerModelType> getSkins() {
            return players.keySet();
        }

        public AvatarRenderer<?> getPlayerRenderer(PlayerModelType type) {
            return players.get(type);
        }

        public AvatarRenderer<?> getMannequinRenderer(PlayerModelType type) {
            return mannequins.get(type);
        }

        public Set<EntityType<?>> getEntityTypes() {
            return renderers.keySet();
        }

        public EntityRenderer<?, ?> getRenderer(EntityType<?> type) {
            return renderers.get(type);
        }
    }
}
