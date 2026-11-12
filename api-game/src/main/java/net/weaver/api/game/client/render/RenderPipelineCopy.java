package net.weaver.api.game.client.render;

import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PolygonMode;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.resources.Identifier;

/**
 * A copy of a {@link RenderPipeline} with some settings changed (what
 * NeoForge's {@code RenderPipeline#toBuilder} gives): every setting not
 * changed is the original's. Made through the pipeline's own constructor, so
 * the copy is an ordinary pipeline to the game.
 *
 * <pre>{@code
 * RenderPipeline tinted = RenderPipelineCopy.of(pipeline)
 *         .location(copyName)
 *         .fragmentShader(Identifier.fromNamespaceAndPath("mymod", "core/tinted"))
 *         .build();
 * }</pre>
 */
public final class RenderPipelineCopy {
    private final RenderPipeline from;
    private Identifier location;
    private final Map<ShaderType, Identifier> shaders = new EnumMap<>(ShaderType.class);
    private ShaderDefines defines;
    private final List<BindGroupLayout> layouts;
    private final List<ColorTargetState> colorTargets;
    private DepthStencilState depthStencil;
    private PolygonMode polygonMode;
    private boolean cull;

    private RenderPipelineCopy(RenderPipeline from) {
        this.from = from;
        this.location = from.getLocation();
        this.shaders.putAll(from.getShaders());
        this.defines = from.getShaderDefines();
        this.layouts = new ArrayList<>(from.getBindGroupLayouts());
        this.colorTargets = new ArrayList<>(from.getColorTargetStates());
        this.depthStencil = from.getDepthStencilState();
        this.polygonMode = from.getPolygonMode();
        this.cull = from.isCull();
    }

    /** Starts a copy of {@code pipeline}. */
    public static RenderPipelineCopy of(RenderPipeline pipeline) {
        return new RenderPipelineCopy(Objects.requireNonNull(pipeline, "pipeline"));
    }

    /** The copy's name; give each copy its own. */
    public RenderPipelineCopy location(Identifier location) {
        this.location = Objects.requireNonNull(location, "location");
        return this;
    }

    public RenderPipelineCopy vertexShader(Identifier shader) {
        this.shaders.put(ShaderType.VERTEX, Objects.requireNonNull(shader, "shader"));
        return this;
    }

    public RenderPipelineCopy fragmentShader(Identifier shader) {
        this.shaders.put(ShaderType.FRAGMENT, Objects.requireNonNull(shader, "shader"));
        return this;
    }

    public RenderPipelineCopy shaderDefines(ShaderDefines defines) {
        this.defines = Objects.requireNonNull(defines, "defines");
        return this;
    }

    /** Adds a bind group layout after the original's. */
    public RenderPipelineCopy addBindGroupLayout(BindGroupLayout layout) {
        this.layouts.add(Objects.requireNonNull(layout, "layout"));
        return this;
    }

    /** Replaces the color target {@code index} (blending, write mask). */
    public RenderPipelineCopy colorTarget(int index, ColorTargetState state) {
        this.colorTargets.set(index, Objects.requireNonNull(state, "state"));
        return this;
    }

    /** Depth and stencil; {@code null} for none. */
    public RenderPipelineCopy depthStencil(DepthStencilState state) {
        this.depthStencil = state;
        return this;
    }

    public RenderPipelineCopy polygonMode(PolygonMode mode) {
        this.polygonMode = Objects.requireNonNull(mode, "mode");
        return this;
    }

    public RenderPipelineCopy cull(boolean cull) {
        this.cull = cull;
        return this;
    }

    /** Makes the copy. */
    public RenderPipeline build() {
        return new Copied(location, Map.copyOf(shaders), defines, List.copyOf(layouts),
                colorTargets.toArray(new ColorTargetState[0]), depthStencil, polygonMode, cull,
                from.getVertexFormatBindings().toArray(new VertexFormat[0]), from.getPrimitiveTopology(),
                from.pushConstantSize(), from.getSortKey());
    }

    private static final class Copied extends RenderPipeline {
        private Copied(Identifier location, Map<ShaderType, Identifier> shaders, ShaderDefines defines,
                       List<BindGroupLayout> layouts, ColorTargetState[] colorTargets, DepthStencilState depthStencil,
                       PolygonMode polygonMode, boolean cull, VertexFormat[] formats,
                       com.mojang.renderpearl.api.pipeline.PrimitiveTopology topology, int pushConstants, int sortKey) {
            super(location, shaders, defines, layouts, colorTargets, depthStencil, polygonMode, cull, formats,
                    topology, pushConstants, sortKey);
        }
    }
}
