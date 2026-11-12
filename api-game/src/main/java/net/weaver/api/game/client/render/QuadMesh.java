package net.weaver.api.game.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/**
 * A model built in code: quads with positions, texture coordinates, colors, and
 * normals, built once and drawn every frame at any transform (gears, shafts,
 * belts, animated parts). Animate it by changing the pose stack before
 * {@link #submit}: rotate a gear, move a piston arm. The mesh itself is
 * immutable and can be shared by every instance.
 *
 * <p>Use an entity render type for models with a texture, for example
 * {@code RenderTypes.entityCutout(texture)}, which expects position, color,
 * texture, overlay, light, and normal; a texture coordinate is in 0..1 of that
 * texture, or of an atlas sprite with {@link Builder#sprite}.
 *
 * <p>Each quad is written every frame on the render thread; for thousands of
 * instances, draw fewer, larger meshes.
 */
public final class QuadMesh {
    private final float[] data;
    private final int quads;

    private QuadMesh(float[] data, int quads) {
        this.data = data;
        this.quads = quads;
    }

    public static Builder builder() {
        return new Builder();
    }

    public int quadCount() {
        return quads;
    }

    /** Draws the mesh at the current pose with {@code type}, light, and a tint (ARGB, -1 for none). */
    public void submit(SubmitNodeCollector collector, PoseStack poses, RenderType type, int light, int tint) {
        float a = ((tint >>> 24) & 0xFF) / 255.0F;
        float r = ((tint >>> 16) & 0xFF) / 255.0F;
        float g = ((tint >>> 8) & 0xFF) / 255.0F;
        float b = (tint & 0xFF) / 255.0F;
        collector.submitCustomGeometry(poses, type, (pose, consumer) -> write(pose, consumer, light, a, r, g, b));
    }

    private void write(PoseStack.Pose pose, VertexConsumer consumer, int light, float a, float r, float g, float b) {
        for (int vertex = 0; vertex < quads * 4; vertex++) {
            int i = vertex * Builder.STRIDE;
            consumer.addVertex(pose, data[i], data[i + 1], data[i + 2])
                    .setColor(data[i + 5] * r, data[i + 6] * g, data[i + 7] * b, data[i + 8] * a)
                    .setUv(data[i + 3], data[i + 4])
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(pose, data[i + 9], data[i + 10], data[i + 11]);
        }
    }

    /** Builds a mesh one quad at a time (four corners, counter-clockwise seen from the front). */
    public static final class Builder {
        static final int STRIDE = 12;
        private final List<float[]> vertices = new ArrayList<>();
        private TextureAtlasSprite sprite;
        private float red = 1, green = 1, blue = 1, alpha = 1;

        private Builder() {
        }

        /** Maps the following texture coordinates (0..1) into an atlas sprite; null for the whole texture. */
        public Builder sprite(TextureAtlasSprite sprite) {
            this.sprite = sprite;
            return this;
        }

        /** The color of the following quads (0..1). */
        public Builder color(float red, float green, float blue, float alpha) {
            this.red = red;
            this.green = green;
            this.blue = blue;
            this.alpha = alpha;
            return this;
        }

        /** One corner: position, texture coordinate, and normal. Call four times per quad. */
        public Builder vertex(float x, float y, float z, float u, float v, float nx, float ny, float nz) {
            float su = sprite == null ? u : sprite.getU(u);
            float sv = sprite == null ? v : sprite.getV(v);
            vertices.add(new float[] {x, y, z, su, sv, red, green, blue, alpha, nx, ny, nz});
            return this;
        }

        /** An axis-aligned box from {@code (x0, y0, z0)} to {@code (x1, y1, z1)} with the whole texture on each face. */
        public Builder box(float x0, float y0, float z0, float x1, float y1, float z1) {
            // down, up, north, south, west, east
            vertex(x0, y0, z1, 0, 1, 0, -1, 0).vertex(x0, y0, z0, 0, 0, 0, -1, 0).vertex(x1, y0, z0, 1, 0, 0, -1, 0).vertex(x1, y0, z1, 1, 1, 0, -1, 0);
            vertex(x0, y1, z0, 0, 0, 0, 1, 0).vertex(x0, y1, z1, 0, 1, 0, 1, 0).vertex(x1, y1, z1, 1, 1, 0, 1, 0).vertex(x1, y1, z0, 1, 0, 0, 1, 0);
            vertex(x1, y1, z0, 0, 0, 0, 0, -1).vertex(x1, y0, z0, 0, 1, 0, 0, -1).vertex(x0, y0, z0, 1, 1, 0, 0, -1).vertex(x0, y1, z0, 1, 0, 0, 0, -1);
            vertex(x0, y1, z1, 0, 0, 0, 0, 1).vertex(x0, y0, z1, 0, 1, 0, 0, 1).vertex(x1, y0, z1, 1, 1, 0, 0, 1).vertex(x1, y1, z1, 1, 0, 0, 0, 1);
            vertex(x0, y1, z0, 0, 0, -1, 0, 0).vertex(x0, y0, z0, 0, 1, -1, 0, 0).vertex(x0, y0, z1, 1, 1, -1, 0, 0).vertex(x0, y1, z1, 1, 0, -1, 0, 0);
            vertex(x1, y1, z1, 0, 0, 1, 0, 0).vertex(x1, y0, z1, 0, 1, 1, 0, 0).vertex(x1, y0, z0, 1, 1, 1, 0, 0).vertex(x1, y1, z0, 1, 0, 1, 0, 0);
            return this;
        }

        /**
         * @throws IllegalStateException when the corner count is not a multiple of four
         */
        public QuadMesh build() {
            if (vertices.size() % 4 != 0) {
                throw new IllegalStateException("a quad mesh needs four corners per quad, got " + vertices.size());
            }
            float[] data = new float[vertices.size() * STRIDE];
            for (int i = 0; i < vertices.size(); i++) {
                System.arraycopy(vertices.get(i), 0, data, i * STRIDE, STRIDE);
            }
            return new QuadMesh(data, vertices.size() / 4);
        }
    }
}
