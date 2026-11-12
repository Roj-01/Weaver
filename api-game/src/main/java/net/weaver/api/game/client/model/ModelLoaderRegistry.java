package net.weaver.api.game.client.model;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.mojang.serialization.MapCodec;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.resources.model.geometry.UnbakedGeometry;
import net.minecraft.resources.Identifier;
import net.weaver.api.game.client.mixin.ItemModelsAccessor;
import net.weaver.api.game.client.mixin.SpecialModelRenderersAccessor;

/**
 * Custom model loaders: a model JSON ({@code assets/<ns>/models/...}) with
 * {@code "loader": "mymod:my_loader"} is read by the loader registered under
 * that id, which turns the JSON into custom geometry (an OBJ file, connected
 * textures, shapes that are not cuboids, geometry composed in code). Vanilla
 * still handles everything else in the file: {@code parent}, {@code textures},
 * {@code display}, {@code ambientocclusion}, and {@code gui_light}, so the loader
 * receives resolved texture slots when it bakes.
 *
 * <p>Weaver registers {@code weaver:quads}: free-form quads written in the JSON
 * (see {@link QuadGeometry#LOADER}).
 *
 * <p>Models are read on resource-reload worker threads, several at once:
 * register loaders during client setup, and keep loaders thread-safe. An
 * unknown loader id or a loader that throws gives that model empty geometry
 * and one log line; the game keeps loading.
 *
 * <p>Item model types ({@code "type"} in {@code assets/<ns>/items/*.json}) and
 * special model renderers can be added with {@link #registerItemModelType} and
 * {@link #registerSpecialModelType}. A model with a custom loader also works
 * through the vanilla {@code minecraft:model} item type.
 */
public final class ModelLoaderRegistry {
    /** Turns one model JSON into geometry. */
    @FunctionalInterface
    public interface ModelLoader {
        UnbakedGeometry read(JsonObject json, JsonDeserializationContext context);
    }

    private static final Map<Identifier, ModelLoader> LOADERS = new ConcurrentHashMap<>();
    private static final Set<Identifier> REPORTED = ConcurrentHashMap.newKeySet();

    static {
        LOADERS.put(QuadGeometry.LOADER, QuadGeometry::read);
    }

    private ModelLoaderRegistry() {
    }

    /** Registers a loader; the id is what model files write in {@code "loader"}. */
    public static void register(Identifier id, ModelLoader loader) {
        if (LOADERS.putIfAbsent(Objects.requireNonNull(id, "id"), Objects.requireNonNull(loader, "loader")) != null) {
            throw new IllegalStateException("model loader already registered: " + id);
        }
    }

    public static boolean has(Identifier id) {
        return LOADERS.containsKey(id);
    }

    /** Reads a model with a {@code "loader"} field; never throws. Called by the loader's model parsing hook. */
    @net.weaver.api.annotation.Internal
    public static UnbakedGeometry read(JsonObject json, JsonDeserializationContext context) {
        Identifier id;
        try {
            id = Identifier.parse(json.get("loader").getAsString());
        } catch (RuntimeException invalid) {
            report(null, "invalid loader field: " + invalid);
            return UnbakedGeometry.EMPTY;
        }
        ModelLoader loader = LOADERS.get(id);
        if (loader == null) {
            report(id, "no loader registered");
            return UnbakedGeometry.EMPTY;
        }
        try {
            UnbakedGeometry geometry = loader.read(json, context);
            return geometry == null ? UnbakedGeometry.EMPTY : geometry;
        } catch (RuntimeException | LinkageError | StackOverflowError failure) {
            report(id, String.valueOf(failure));
            return UnbakedGeometry.EMPTY;
        }
    }

    private static void report(Identifier id, String reason) {
        if (id == null || REPORTED.add(id)) {
            System.out.println("[weaver] MODEL LOADER FAILED loader=" + id + " reason=" + reason + " action=empty-geometry");
        }
    }

    /** Adds an item model type, used as {@code "type": "<id>"} in item definitions. Before the first resource reload. */
    public static void registerItemModelType(Identifier id, MapCodec<? extends ItemModel.Unbaked> codec) {
        ItemModelsAccessor.weaver$idMapper().put(Objects.requireNonNull(id, "id"), Objects.requireNonNull(codec, "codec"));
    }

    /** Adds a special model renderer type. Before the first resource reload. */
    public static void registerSpecialModelType(Identifier id, MapCodec<? extends SpecialModelRenderer.Unbaked<?>> codec) {
        SpecialModelRenderersAccessor.weaver$idMapper().put(Objects.requireNonNull(id, "id"), Objects.requireNonNull(codec, "codec"));
    }

    /**
     * Adds an item tint source, used as {@code "type": "<id>"} in the {@code tints}
     * of an item model: the color of a tinted layer computed from the stack (an
     * energy level, a stored fluid's color, a team color). Before the first
     * resource reload.
     */
    public static void registerItemTintSource(Identifier id,
            MapCodec<? extends net.minecraft.client.color.item.ItemTintSource> codec) {
        net.weaver.api.game.client.mixin.ItemTintSourcesAccessor.weaver$idMapper()
                .put(Objects.requireNonNull(id, "id"), Objects.requireNonNull(codec, "codec"));
    }

    /**
     * Adds a boolean item model property for {@code minecraft:condition} item
     * models ({@code "property": "<id>"}). Before the first resource reload.
     */
    public static void registerConditionalProperty(Identifier id,
            MapCodec<? extends net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty> codec) {
        net.weaver.api.game.client.mixin.ConditionalPropertiesAccessor.weaver$idMapper()
                .put(Objects.requireNonNull(id, "id"), Objects.requireNonNull(codec, "codec"));
    }

    /**
     * Adds a numeric item model property for {@code minecraft:range_dispatch} item
     * models (a charge level, a fill amount). Before the first resource reload.
     */
    public static void registerRangeProperty(Identifier id,
            MapCodec<? extends net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty> codec) {
        net.weaver.api.game.client.mixin.RangeSelectPropertiesAccessor.weaver$idMapper()
                .put(Objects.requireNonNull(id, "id"), Objects.requireNonNull(codec, "codec"));
    }

    /**
     * Adds a select item model property for {@code minecraft:select} item models
     * (a mode name). Before the first resource reload.
     */
    public static void registerSelectProperty(Identifier id,
            net.minecraft.client.renderer.item.properties.select.SelectItemModelProperty.Type<?, ?> type) {
        net.weaver.api.game.client.mixin.SelectPropertiesAccessor.weaver$idMapper()
                .put(Objects.requireNonNull(id, "id"), Objects.requireNonNull(type, "type"));
    }
}
