package net.weaver.api.game.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.weaver.api.game.client.level.LevelDrawRegistry;
import net.weaver.api.game.client.level.LevelDrawStage;
import org.joml.Matrix4f;
import org.joml.Quaternionfc;
import org.joml.Vector4f;

/**
 * A mesh uploaded to the GPU once and drawn many times in one draw call, each
 * copy (instance) with its own transform, color, and light: the way to draw
 * thousands of moving parts (gears, shafts, belts, pistons) without writing their
 * quads every frame. Only the per-instance data, 80 bytes each, is sent per frame.
 *
 * <pre>{@code
 * InstancedMesh gear = InstancedMesh.builder().box(2, 0, 2, 14, 4, 14, sprite).build();
 * LevelDrawRegistry.register("examplemod:gears", LevelDrawStage.AFTER_SOLID, context -> {
 *     InstancedMesh.Instances batch = gear.instances(context);
 *     for (Machine machine : visibleMachines) {
 *         batch.add(machine.x, machine.y, machine.z, machine.rotation(), 1.0F, 0xFFFFFFFF, machine.light());
 *     }
 *     batch.draw();
 * });
 * }</pre>
 *
 * <p>How a mesh is drawn is its {@link Material}: the texture (the block atlas by
 * default, or any texture), the blending ({@link Blend}: opaque, cut-out,
 * translucent, additive), whether it glows (emissive: no lightmap or shading),
 * face culling, and the shaders (Weaver's, or a mod's own that read the same
 * attributes). Each material gets its own pipeline, built once.</p>
 *
 * <p>Coordinates in the builder are model units (0 to 16 across a block).
 * Instances are placed at world positions. Light is a packed light value as
 * vanilla uses (block light in the low half). Draw opaque and cut-out meshes at
 * {@code AFTER_SOLID}, translucent and additive ones at {@code AFTER_TRANSLUCENT}.
 * In a stage with an open main pass the mesh draws into it; in a stage without
 * one it opens its own pass on the main target (color and depth). Inside an
 * improved-transparency layer pass it does not draw (that pass has other
 * attachments). Each batch is drawn once ({@code instances(context)} starts the
 * next); a mesh reuses its instance memory from frame to frame. Instancing needs the GPU's base-instance support, which every
 * OpenGL 4.2 driver has.</p>
 */
public final class InstancedMesh implements AutoCloseable {
    /** Per-vertex data: position, texture coordinates, normal. */
    public static final VertexFormat MESH_FORMAT = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .addAttribute("UV0", GpuFormat.RG32_FLOAT)
            .addAttribute("Normal", GpuFormat.RGB32_FLOAT)
            .build();
    /** Per-instance data: the model matrix in four columns, the color, and the light (block, sky). */
    public static final VertexFormat INSTANCE_FORMAT = VertexFormat.builder(1)
            .addAttribute("InstanceModel0", GpuFormat.RGBA32_FLOAT)
            .addAttribute("InstanceModel1", GpuFormat.RGBA32_FLOAT)
            .addAttribute("InstanceModel2", GpuFormat.RGBA32_FLOAT)
            .addAttribute("InstanceModel3", GpuFormat.RGBA32_FLOAT)
            .addAttribute("InstanceColor", GpuFormat.RGBA32_FLOAT)
            .addAttribute("InstanceLight", GpuFormat.RG32_FLOAT)
            .build();
    private static final int INSTANCE_BYTES = INSTANCE_FORMAT.getVertexSize();
    private static final Identifier SHADER = Identifier.fromNamespaceAndPath("weaver", "core/instanced");

    /** How fragments combine with what is already drawn. */
    public enum Blend {
        /** Replaces; every pixel drawn. */
        OPAQUE,
        /** Replaces; pixels with alpha under 0.1 are dropped (leaves, grates). Depth written. */
        CUTOUT,
        /** Alpha-blended over what is behind (glass, liquids). Depth tested, not written. */
        TRANSLUCENT,
        /** Added to what is behind (glows, beams, sparks). Depth tested, not written. */
        ADDITIVE
    }

    /**
     * How a mesh is drawn.
     *
     * @param texture        the texture to sample: the block atlas, or any texture id
     *                       ({@code examplemod:textures/entity/gear.png})
     * @param atlas          whether builder sprites map texture coordinates into an atlas;
     *                       false for a plain texture, where quad coordinates 0..16 cover it
     * @param blend          the blending
     * @param emissive       full bright, without the lightmap or directional shading
     * @param cull           drop faces seen from behind
     * @param vertexShader   the vertex shader ({@code weaver:core/instanced} by default)
     * @param fragmentShader the fragment shader
     */
    public record Material(Identifier texture, boolean atlas, Blend blend, boolean emissive, boolean cull,
                           Identifier vertexShader, Identifier fragmentShader) {
        public Material {
            Objects.requireNonNull(texture, "texture");
            Objects.requireNonNull(blend, "blend");
            Objects.requireNonNull(vertexShader, "vertexShader");
            Objects.requireNonNull(fragmentShader, "fragmentShader");
        }

        /** Block atlas, cut-out, lit, culled: like blocks. */
        public static Material blocks() {
            return new Material(TextureAtlas.LOCATION_BLOCKS, true, Blend.CUTOUT, false, true, SHADER, SHADER);
        }

        /** A plain texture (not an atlas), cut-out, lit, culled. */
        public static Material texture(Identifier texture) {
            return new Material(texture, false, Blend.CUTOUT, false, true, SHADER, SHADER);
        }

        public Material withBlend(Blend blend) {
            return new Material(texture, atlas, blend, emissive, cull, vertexShader, fragmentShader);
        }

        public Material withEmissive(boolean emissive) {
            return new Material(texture, atlas, blend, emissive, cull, vertexShader, fragmentShader);
        }

        public Material withCull(boolean cull) {
            return new Material(texture, atlas, blend, emissive, cull, vertexShader, fragmentShader);
        }

        /**
         * Uses a mod's own shaders. They read the same inputs as Weaver's
         * ({@code Position}, {@code UV0}, {@code Normal}, {@code InstanceModel0..3},
         * {@code InstanceColor}, {@code InstanceLight}) and the same uniforms
         * ({@code Sampler0}, and {@code Sampler2} unless emissive); defines
         * {@code ALPHA_CUTOUT} and {@code EMISSIVE} are set as for Weaver's.
         */
        public Material withShaders(Identifier vertexShader, Identifier fragmentShader) {
            return new Material(texture, atlas, blend, emissive, cull, vertexShader, fragmentShader);
        }

        /** The pipeline of this material, built once and compiled during resource loading. */
        public RenderPipeline pipeline() {
            return PIPELINES.computeIfAbsent(pipelineKey(), key -> CustomRendering.precompile(build()));
        }

        private Material pipelineKey() {
            // The texture is bound per draw; pipelines differ only in their states and shaders.
            return new Material(TextureAtlas.LOCATION_BLOCKS, true, blend, emissive, cull, vertexShader, fragmentShader);
        }

        private RenderPipeline build() {
            boolean blended = blend == Blend.TRANSLUCENT || blend == Blend.ADDITIVE;
            RenderPipeline.Builder builder = RenderPipeline.builder()
                    .withBindGroupLayout(BindGroupLayouts.GLOBALS)
                    .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                    .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                    .withBindGroupLayout(BindGroupLayouts.FOG);
            if (!emissive) {
                builder.withBindGroupLayout(BindGroupLayouts.LIGHTING)
                        .withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER2);
            } else {
                builder.withBindGroupLayout(BindGroupLayouts.SAMPLER0).withShaderDefine("EMISSIVE");
            }
            if (blend == Blend.CUTOUT) {
                builder.withShaderDefine("ALPHA_CUTOUT");
            }
            String variant = blend.name().toLowerCase(java.util.Locale.ROOT) + (emissive ? "_emissive" : "")
                    + (cull ? "" : "_no_cull") + "_" + Integer.toHexString((vertexShader + "|" + fragmentShader).hashCode());
            return builder
                    .withLocation(Identifier.fromNamespaceAndPath("weaver", "pipeline/instanced/" + variant))
                    .withVertexShader(vertexShader)
                    .withFragmentShader(fragmentShader)
                    .withColorTargetState(switch (blend) {
                        case TRANSLUCENT -> new ColorTargetState(BlendFunction.TRANSLUCENT);
                        case ADDITIVE -> new ColorTargetState(BlendFunction.ADDITIVE);
                        default -> ColorTargetState.DEFAULT;
                    })
                    .withDepthStencilState(blended ? new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false)
                            : DepthStencilState.DEFAULT)
                    .withCull(cull)
                    .withVertexBinding(0, MESH_FORMAT)
                    .withVertexBinding(1, INSTANCE_FORMAT)
                    .withPrimitiveTopology(PrimitiveTopology.QUADS)
                    .build();
        }
    }

    private static final Map<Material, RenderPipeline> PIPELINES = new ConcurrentHashMap<>();

    /** The pipeline of {@link Material#blocks()}. */
    public static final RenderPipeline PIPELINE = Material.blocks().pipeline();

    private static final java.util.Set<String> REPORTED = ConcurrentHashMap.newKeySet();
    /** Textures to load at the end of the frame: loading uploads, which an open pass does not allow. */
    private static final java.util.Set<Identifier> PENDING_TEXTURES = ConcurrentHashMap.newKeySet();

    static {
        LevelDrawRegistry.register("weaver:instanced_mesh_textures", LevelDrawStage.AFTER_LEVEL, context -> {
            if (!PENDING_TEXTURES.isEmpty()) {
                for (Identifier id : List.copyOf(PENDING_TEXTURES)) {
                    PENDING_TEXTURES.remove(id);
                    try {
                        Minecraft.getInstance().getTextureManager().getTexture(id);
                    } catch (RuntimeException failure) {
                        report("texture " + id, "TEXTURE FAILED texture=" + id + " reason=" + failure);
                    }
                }
            }
        });
    }

    /** The loaded texture, or null after scheduling its load when a pass is open (loading uploads). */
    private static AbstractTexture texture(Identifier id, boolean passOpen) {
        net.minecraft.client.renderer.texture.TextureManager textures = Minecraft.getInstance().getTextureManager();
        AbstractTexture loaded = ((net.weaver.api.game.client.mixin.TextureManagerAccessor) textures).weaver$byPath().get(id);
        if (loaded != null || !passOpen) {
            return loaded != null ? loaded : textures.getTexture(id);
        }
        PENDING_TEXTURES.add(id);
        return null;
    }

    private final float[] vertices;
    private final int quads;
    private final Material material;
    private GpuBuffer meshBuffer;
    /** Instance buffers with the frame they were drawn in; freed two frames later. */
    private final Deque<Object[]> usedInstanceBuffers = new ArrayDeque<>();
    private long instancesDrawn;
    /** The instance data of the batch being built, reused across frames (grown as needed). */
    private ByteBuffer instanceData = ByteBuffer.allocateDirect(INSTANCE_BYTES * 64).order(ByteOrder.nativeOrder());

    private InstancedMesh(float[] vertices, int quads, Material material) {
        this.vertices = vertices;
        this.quads = quads;
        this.material = material;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** The number of quads in the mesh. */
    public int quadCount() {
        return quads;
    }

    public Material material() {
        return material;
    }

    /** How many instances have been drawn since the mesh was made (diagnostics). */
    public long instancesDrawn() {
        return instancesDrawn;
    }

    /** Starts this frame's instances for the context's stage. */
    public Instances instances(LevelDrawRegistry.LevelDrawContext context) {
        Objects.requireNonNull(context, "context");
        Vec3 camera = context.camera() == null || context.camera().pos == null ? Vec3.ZERO : context.camera().pos;
        return new Instances(context, camera);
    }

    /** Frees the GPU buffers. Render thread. */
    @Override
    public void close() {
        if (meshBuffer != null) {
            meshBuffer.close();
            meshBuffer = null;
        }
        while (!usedInstanceBuffers.isEmpty()) {
            ((GpuBuffer) usedInstanceBuffers.poll()[0]).close();
        }
    }

    private GpuBuffer mesh() {
        if (meshBuffer == null || meshBuffer.isClosed()) {
            ByteBuffer data = ByteBuffer.allocateDirect(vertices.length * 4).order(ByteOrder.nativeOrder());
            for (float value : vertices) {
                data.putFloat(value);
            }
            data.flip();
            meshBuffer = RenderSystem.getDevice().createBuffer(() -> "weaver instanced mesh", GpuBuffer.USAGE_VERTEX, data);
        }
        return meshBuffer;
    }

    private void retire(GpuBuffer buffer) {
        long frame = LevelDrawRegistry.dispatchCount(LevelDrawStage.AFTER_LEVEL);
        while (!usedInstanceBuffers.isEmpty() && (long) usedInstanceBuffers.peek()[1] < frame - 1) {
            ((GpuBuffer) usedInstanceBuffers.poll()[0]).close();
        }
        usedInstanceBuffers.add(new Object[] {buffer, frame});
    }

    private static void report(String key, String message) {
        if (REPORTED.add(key)) {
            System.out.println("[weaver] INSTANCED MESH " + message);
        }
    }

    /** One frame's instances of a mesh. */
    public final class Instances {
        private final LevelDrawRegistry.LevelDrawContext context;
        private final Vec3 camera;
        private int count;
        private boolean drawn;
        private final Matrix4f scratch = new Matrix4f();

        private Instances(LevelDrawRegistry.LevelDrawContext context, Vec3 camera) {
            this.context = context;
            this.camera = camera;
            instanceData.clear();
        }

        /**
         * Adds an instance at a world position, turned by {@code rotation} around the
         * mesh's centre (8, 8, 8) and scaled, with a color (ARGB) and packed light.
         */
        public Instances add(double x, double y, double z, Quaternionfc rotation, float scale, int argb, int packedLight) {
            scratch.identity()
                    .translate((float) (x - camera.x), (float) (y - camera.y), (float) (z - camera.z))
                    .translate(0.5F, 0.5F, 0.5F)
                    .rotate(rotation)
                    .scale(scale / 16.0F)
                    .translate(-8.0F, -8.0F, -8.0F);
            return add(scratch, argb, packedLight);
        }

        /**
         * Adds an instance with a full model matrix from model units to
         * camera-relative world space (subtract the camera position yourself).
         */
        public Instances add(Matrix4f model, int argb, int packedLight) {
            if (drawn) {
                throw new IllegalStateException("this batch was already drawn; start a new one with instances(context)");
            }
            ByteBuffer data = instanceData;
            if (data.remaining() < INSTANCE_BYTES) {
                ByteBuffer bigger = ByteBuffer.allocateDirect(data.capacity() * 2).order(ByteOrder.nativeOrder());
                data.flip();
                bigger.put(data);
                data = bigger;
                instanceData = bigger;
            }
            model.get(data.position(), data);
            data.position(data.position() + 64);
            data.putFloat(((argb >> 16) & 0xFF) / 255.0F).putFloat(((argb >> 8) & 0xFF) / 255.0F)
                    .putFloat((argb & 0xFF) / 255.0F).putFloat(((argb >>> 24) & 0xFF) / 255.0F);
            data.putFloat(packedLight & 0xFFFF).putFloat((packedLight >>> 16) & 0xFFFF);
            count++;
            return this;
        }

        /** The number of instances added. */
        public int count() {
            return count;
        }

        /**
         * Draws every instance in one call: into the stage's open pass, or into a pass
         * of its own on the main target when the stage has none. Returns false (and
         * logs once) when nothing could be drawn.
         */
        public boolean draw() {
            if (count == 0 || drawn) {
                return false;
            }
            drawn = true;
            if (context.oitLayer() != null && context.pass() != null) {
                report("oit", "SKIPPED reason=an improved-transparency layer pass has other attachments;"
                        + " draw at AFTER_TRANSLUCENT instead");
                return false;
            }
            RenderPass own = null;
            try {
                RenderPass pass = context.pass();
                AbstractTexture texture = texture(material.texture(), pass != null);
                if (texture == null) {
                    return false;
                }
                if (pass == null) {
                    RenderTarget target = context.target();
                    if (target == null) {
                        return false;
                    }
                    own = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "weaver instanced mesh",
                            target.getColorTextureView(), Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty());
                    pass = own;
                }
                ByteBuffer data = instanceData;
                data.flip();
                GpuBuffer instances = RenderSystem.getDevice().createBuffer(() -> "weaver mesh instances",
                        GpuBuffer.USAGE_VERTEX, data);
                retire(instances);
                Minecraft minecraft = Minecraft.getInstance();
                RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
                int indexCount = quads * 6;
                pass.setPipeline(RenderSystem.getCompiledPipeline(material.pipeline()));
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms", RenderSystem.getDynamicUniforms()
                        .writeTransform(context.modelView() != null ? new Matrix4f(context.modelView())
                                : RenderSystem.getModelViewMatrixCopy(), new Vector4f(1.0F, 1.0F, 1.0F, 1.0F)));
                pass.setUniform("Sampler0", texture.getTextureView(), texture.getSampler());
                if (!material.emissive()) {
                    pass.setUniform("Sampler2", minecraft.gameRenderer.lightmap(),
                            RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
                }
                pass.setVertexBuffer(0, mesh().slice());
                pass.setVertexBuffer(1, instances.slice());
                pass.setIndexBuffer(indices.getBuffer(indexCount), indices.type());
                pass.drawIndexed(indexCount, count, 0, 0, 0);
                instancesDrawn += count;
                return true;
            } catch (RuntimeException | LinkageError failure) {
                report("draw", "DRAW FAILED reason=" + failure);
                return false;
            } finally {
                if (own != null) {
                    own.close();
                }
            }
        }
    }

    /** Builds an {@link InstancedMesh} from quads in model units. */
    public static final class Builder {
        private final List<float[]> quads = new ArrayList<>();
        private Material material = Material.blocks();

        private Builder() {
        }

        /** How the mesh is drawn; {@link Material#blocks()} by default. Set before adding sprite quads. */
        public Builder material(Material material) {
            this.material = Objects.requireNonNull(material, "material");
            return this;
        }

        /**
         * Adds a quad: four corners {@code {x, y, z, u, v}} counter-clockwise seen from
         * the front, in model units, with texture coordinates in 0..16 of {@code sprite}
         * (or of the whole texture when {@code sprite} is null).
         */
        public Builder quad(TextureAtlasSprite sprite, Direction face, float[][] corners) {
            if (corners.length != 4) {
                throw new IllegalArgumentException("a quad has 4 corners");
            }
            float[] quad = new float[32];
            for (int i = 0; i < 4; i++) {
                float[] corner = corners[i];
                if (corner.length < 5) {
                    throw new IllegalArgumentException("a corner is {x, y, z, u, v}");
                }
                int at = i * 8;
                quad[at] = corner[0];
                quad[at + 1] = corner[1];
                quad[at + 2] = corner[2];
                quad[at + 3] = sprite == null ? corner[3] / 16.0F : sprite.getU(corner[3] / 16.0F);
                quad[at + 4] = sprite == null ? corner[4] / 16.0F : sprite.getV(corner[4] / 16.0F);
                quad[at + 5] = face.getStepX();
                quad[at + 6] = face.getStepY();
                quad[at + 7] = face.getStepZ();
            }
            quads.add(quad);
            return this;
        }

        /** Adds a quad covering the whole texture coordinates given (no sprite: a plain texture). */
        public Builder quad(Direction face, float[][] corners) {
            return quad(null, face, corners);
        }

        /** Adds a box with all six faces from one sprite (null: the whole plain texture), in model units. */
        public Builder box(float x0, float y0, float z0, float x1, float y1, float z1, TextureAtlasSprite sprite) {
            quad(sprite, Direction.UP, new float[][] {{x0, y1, z1, x0, z1}, {x1, y1, z1, x1, z1}, {x1, y1, z0, x1, z0}, {x0, y1, z0, x0, z0}});
            quad(sprite, Direction.DOWN, new float[][] {{x0, y0, z0, x0, z0}, {x1, y0, z0, x1, z0}, {x1, y0, z1, x1, z1}, {x0, y0, z1, x0, z1}});
            quad(sprite, Direction.NORTH, new float[][] {{x1, y0, z0, 16 - x1, 16 - y0}, {x0, y0, z0, 16 - x0, 16 - y0}, {x0, y1, z0, 16 - x0, 16 - y1}, {x1, y1, z0, 16 - x1, 16 - y1}});
            quad(sprite, Direction.SOUTH, new float[][] {{x0, y0, z1, x0, 16 - y0}, {x1, y0, z1, x1, 16 - y0}, {x1, y1, z1, x1, 16 - y1}, {x0, y1, z1, x0, 16 - y1}});
            quad(sprite, Direction.WEST, new float[][] {{x0, y0, z0, z0, 16 - y0}, {x0, y0, z1, z1, 16 - y0}, {x0, y1, z1, z1, 16 - y1}, {x0, y1, z0, z0, 16 - y1}});
            quad(sprite, Direction.EAST, new float[][] {{x1, y0, z1, 16 - z1, 16 - y0}, {x1, y0, z0, 16 - z0, 16 - y0}, {x1, y1, z0, 16 - z0, 16 - y1}, {x1, y1, z1, 16 - z1, 16 - y1}});
            return this;
        }

        /** The mesh; its GPU buffer is made on first draw. */
        public InstancedMesh build() {
            if (quads.isEmpty()) {
                throw new IllegalStateException("a mesh needs at least one quad");
            }
            material.pipeline();
            float[] vertices = new float[quads.size() * 32];
            for (int i = 0; i < quads.size(); i++) {
                System.arraycopy(quads.get(i), 0, vertices, i * 32, 32);
            }
            return new InstancedMesh(vertices, quads.size(), material);
        }
    }
}
