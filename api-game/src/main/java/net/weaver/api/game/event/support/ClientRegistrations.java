package net.weaver.api.game.event.support;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.object.skull.SkullModelBase;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.AtlasManager;
import net.minecraft.client.resources.model.sprite.MaterialBaker;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.material.Fluid;
import net.weaver.api.event.LogicalSide;
import net.weaver.api.game.event.client.CreateSkullModelsEvent;
import net.weaver.api.game.event.client.RegisterFluidModelsEvent;
import net.weaver.api.game.event.client.RegisterPictureInPictureRenderersEvent;
import net.weaver.api.game.event.client.RegisterTextureAtlasesEvent;

/** Client registration events posted from the render-setup mixins. Client only. */
public final class ClientRegistrations {
    private static final Map<SkullBlock.Type, Function<EntityModelSet, SkullModelBase>> SKULL_MODELS = new ConcurrentHashMap<>();

    private ClientRegistrations() {
    }

    /** Posts {@link CreateSkullModelsEvent}; skins go straight into the renderer skin map. */
    public static void postSkulls(Map<SkullBlock.Type, Identifier> skins) {
        GameEvents.post(new CreateSkullModelsEvent(SKULL_MODELS, skins), LogicalSide.CLIENT);
    }

    /** The mod model of a skull type, or null to use vanilla. */
    public static SkullModelBase skullModel(EntityModelSet models, SkullBlock.Type type) {
        if (SKULL_MODELS.isEmpty()) {
            return null;
        }
        Function<EntityModelSet, SkullModelBase> factory = SKULL_MODELS.get(type);
        return factory == null ? null : factory.apply(models);
    }

    public static Map<Fluid, FluidModel> fluidModels(MaterialBaker baker, Map<Fluid, FluidModel> vanilla) {
        if (!GameEvents.hasListeners(RegisterFluidModelsEvent.class)) {
            return vanilla;
        }
        Map<Fluid, FluidModel> models = new HashMap<>(vanilla);
        GameEvents.post(new RegisterFluidModelsEvent(baker, models), LogicalSide.CLIENT);
        return Map.copyOf(models);
    }

    public static List<PictureInPictureRenderer<?>> pictureInPicture(List<PictureInPictureRenderer<?>> vanilla) {
        List<PictureInPictureRenderer<?>> renderers = new ArrayList<>(vanilla);
        renderers.add(new net.weaver.api.game.client.render.GuiBlockScene.Renderer());
        if (!GameEvents.hasListeners(RegisterPictureInPictureRenderersEvent.class)) {
            return List.copyOf(renderers);
        }
        GameEvents.post(new RegisterPictureInPictureRenderersEvent(Minecraft.getInstance(), renderers), LogicalSide.CLIENT);
        return List.copyOf(renderers);
    }

    public static List<AtlasManager.AtlasConfig> atlases(List<AtlasManager.AtlasConfig> vanilla) {
        if (!GameEvents.hasListeners(RegisterTextureAtlasesEvent.class)) {
            return vanilla;
        }
        List<AtlasManager.AtlasConfig> atlases = new ArrayList<>(vanilla);
        GameEvents.post(new RegisterTextureAtlasesEvent(atlases), LogicalSide.CLIENT);
        return List.copyOf(atlases);
    }
}
