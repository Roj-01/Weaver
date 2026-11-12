package net.weaver.api.game.client.render;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.weaver.api.game.client.mixin.RenderTypeInvoker;

/**
 * Custom shaders, render types, and full-screen post effects.
 *
 * <ul>
 *   <li><b>Pipelines.</b> Build a {@code RenderPipeline} with your own shaders
 *       ({@code RenderPipeline.builder(...).withLocation(id).withVertexShader(id)
 *       .withFragmentShader(id)...build()}); the shader files live in your jar
 *       under {@code assets/<namespace>/shaders/}. Register it with
 *       {@link #precompile} so it is compiled during resource loading instead of
 *       on its first use (which would stall one frame).</li>
 *   <li><b>Render types.</b> {@link #renderType} wraps a pipeline and its setup
 *       ({@code RenderSetup.builder(pipeline).withTexture(...).useLightmap()...})
 *       into a render type usable with {@link WorldGeometry} and {@link QuadMesh}.
 *       Create each once and keep it.</li>
 *   <li><b>Post effects.</b> {@link #enablePostEffect} applies a post chain from
 *       {@code assets/<namespace>/post_effect/<path>.json} to the whole screen every
 *       frame until {@link #disablePostEffect} (like the spectator creeper view).</li>
 * </ul>
 */
public final class CustomRendering {
    private static final List<RenderPipeline> PRECOMPILED = new CopyOnWriteArrayList<>();
    private static final Set<Identifier> POST_EFFECTS = ConcurrentHashMap.newKeySet();

    private CustomRendering() {
    }

    /** Compiles {@code pipeline} with the vanilla ones at every resource reload. */
    public static RenderPipeline precompile(RenderPipeline pipeline) {
        if (!PRECOMPILED.contains(Objects.requireNonNull(pipeline, "pipeline"))) {
            PRECOMPILED.add(pipeline);
        }
        return pipeline;
    }

    /** The pipelines registered with {@link #precompile}; read by the loader. */
    public static List<RenderPipeline> precompiled() {
        return List.copyOf(PRECOMPILED);
    }

    /** A render type named {@code name} drawing with {@code setup}. */
    public static RenderType renderType(String name, RenderSetup setup) {
        return RenderTypeInvoker.weaver$create(Objects.requireNonNull(name, "name"), Objects.requireNonNull(setup, "setup"));
    }

    /** Applies a post chain to the screen every frame. Client thread. */
    public static void enablePostEffect(Identifier postEffect) {
        POST_EFFECTS.add(Objects.requireNonNull(postEffect, "postEffect"));
    }

    public static void disablePostEffect(Identifier postEffect) {
        POST_EFFECTS.remove(postEffect);
    }

    public static boolean postEffectEnabled(Identifier postEffect) {
        return POST_EFFECTS.contains(postEffect);
    }

    /** The enabled post effects; read by the loader each frame. */
    public static Set<Identifier> postEffects() {
        return POST_EFFECTS;
    }
}
