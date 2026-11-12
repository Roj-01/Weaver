package net.weaver.api.game.client.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.Transparency;
import com.mojang.math.Transformation;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.renderer.block.dispatch.ModelState;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelDebugName;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.geometry.UnbakedGeometry;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.TextureSlots;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import org.joml.Vector3f;

/**
 * Geometry made of free-form quads, for shapes that are not cuboids (slopes,
 * wedges, pipes, panels at any angle). Positions and texture coordinates use
 * the vanilla model scale: 0 to 16 across a block.
 *
 * <p>From code, build one with {@link #builder()} and return it from a custom
 * {@link ModelLoaderRegistry.ModelLoader}. From JSON, use the built-in loader:
 *
 * <pre>{@code
 * {
 *   "loader": "weaver:quads",
 *   "parent": "minecraft:block/block",
 *   "textures": { "all": "mymod:block/slope", "particle": "#all" },
 *   "quads": [
 *     { "texture": "#all", "face": "up", "cull": "down", "tint": -1,
 *       "vertices": [[0,0,16, 0,16], [16,0,16, 16,16], [16,16,0, 16,0], [0,16,0, 0,0]] }
 *   ]
 * }
 * }</pre>
 *
 * Each quad has four corners {@code [x, y, z, u, v]}, counter-clockwise seen from
 * the front; {@code face} is the direction used for shading, {@code cull}
 * (optional) hides the quad when that side touches a full block, {@code tint}
 * (optional, default -1) is the tint index. Variant rotations from block states
 * are applied.
 */
public final class QuadGeometry implements UnbakedGeometry {
    /** The built-in JSON loader id, {@code weaver:quads}. */
    public static final Identifier LOADER = Identifier.fromNamespaceAndPath("weaver", "quads");

    private final List<Quad> quads;

    private QuadGeometry(List<Quad> quads) {
        this.quads = List.copyOf(quads);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** One quad: four corners of {@code [x, y, z, u, v]} in 0..16 units. */
    public record Quad(String texture, Direction face, Direction cull, int tint, float[][] corners) {
    }

    @Override
    public QuadCollection bake(TextureSlots slots, ModelBaker baker, ModelState state, ModelDebugName name) {
        QuadCollection.Builder out = new QuadCollection.Builder();
        Transformation transformation = state.transformation();
        for (Quad quad : quads) {
            Material.Baked material = baker.materials().resolveSlot(slots, quad.texture().startsWith("#")
                    ? quad.texture().substring(1) : quad.texture(), name);
            org.joml.Vector3fc[] positions = new org.joml.Vector3fc[4];
            long[] uvs = new long[4];
            for (int i = 0; i < 4; i++) {
                float[] corner = quad.corners()[i];
                Vector3f position = new Vector3f(corner[0] / 16.0F, corner[1] / 16.0F, corner[2] / 16.0F);
                if (!transformation.equals(Transformation.IDENTITY)) {
                    position.sub(0.5F, 0.5F, 0.5F);
                    transformation.getMatrix().transformPosition(position);
                    position.add(0.5F, 0.5F, 0.5F);
                }
                positions[i] = baker.interner().vector(position);
                uvs[i] = UVPair.pack(material.sprite().getU(corner[3] / 16.0F), material.sprite().getV(corner[4] / 16.0F));
            }
            Direction face = Direction.rotate(transformation.getMatrix(), quad.face());
            BakedQuad.MaterialInfo info = baker.interner().materialInfo(
                    BakedQuad.MaterialInfo.of(material, Transparency.NONE, quad.tint(), face, 0));
            BakedQuad baked = new BakedQuad(positions[0], positions[1], positions[2], positions[3],
                    uvs[0], uvs[1], uvs[2], uvs[3], face, info);
            if (quad.cull() == null) {
                out.addUnculledFace(baked);
            } else {
                out.addCulledFace(Direction.rotate(transformation.getMatrix(), quad.cull()), baked);
            }
        }
        return out.build();
    }

    /** The {@code weaver:quads} JSON loader. */
    static UnbakedGeometry read(JsonObject json, JsonDeserializationContext context) {
        Builder builder = builder();
        JsonArray quads = json.getAsJsonArray("quads");
        if (quads == null) {
            throw new IllegalArgumentException("weaver:quads needs a \"quads\" array");
        }
        for (JsonElement element : quads) {
            JsonObject quad = element.getAsJsonObject();
            JsonArray vertices = quad.getAsJsonArray("vertices");
            if (vertices == null || vertices.size() != 4) {
                throw new IllegalArgumentException("each quad needs four vertices");
            }
            float[][] corners = new float[4][5];
            for (int i = 0; i < 4; i++) {
                JsonArray vertex = vertices.get(i).getAsJsonArray();
                for (int j = 0; j < 5; j++) {
                    corners[i][j] = vertex.get(j).getAsFloat();
                }
            }
            Direction face = direction(quad.has("face") ? quad.get("face").getAsString() : "up");
            Direction cull = quad.has("cull") ? direction(quad.get("cull").getAsString()) : null;
            int tint = quad.has("tint") ? quad.get("tint").getAsInt() : -1;
            builder.quad(quad.has("texture") ? quad.get("texture").getAsString() : "#all", face, cull, tint, corners);
        }
        return builder.build();
    }

    private static Direction direction(String name) {
        Direction direction = Direction.byName(name.toLowerCase(Locale.ROOT));
        if (direction == null) {
            throw new IllegalArgumentException("unknown direction " + name);
        }
        return direction;
    }

    public static final class Builder {
        private final List<Quad> quads = new ArrayList<>();

        private Builder() {
        }

        /** Adds a quad; {@code texture} is a slot ({@code "#all"}) or a slot name. */
        public Builder quad(String texture, Direction face, Direction cull, int tint, float[][] corners) {
            if (corners.length != 4) {
                throw new IllegalArgumentException("a quad has four corners");
            }
            for (float[] corner : corners) {
                if (corner.length != 5) {
                    throw new IllegalArgumentException("a corner is [x, y, z, u, v]");
                }
            }
            quads.add(new Quad(texture, face, cull, tint, corners));
            return this;
        }

        public QuadGeometry build() {
            return new QuadGeometry(quads);
        }
    }
}
