package net.weaver.api.game.client.level;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.commands.RenderPass;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.weaver.api.client.ClientRegistration;
import net.weaver.api.game.client.util.OrderedClientListener;
import net.weaver.api.game.client.util.OrderedListenerRegistry;

/**
 * Listeners that draw at fixed points of the frame's GPU work
 * ({@link LevelDrawStage}): after the sky, after solid geometry, after translucent
 * geometry, and after the whole level. This is the fine-grained counterpart of
 * {@link LevelRenderRegistry}, for effects that must sit at an exact depth in the
 * frame (a force field over solid blocks but under water, a sky effect behind
 * terrain).
 *
 * <p>Inside the main pass the context carries the open {@link RenderPass}: set a
 * pipeline, bind buffers, and draw with it; do not close it. Outside it, draw
 * into {@link LevelDrawContext#target()} with your own pass. Listeners run on the
 * render thread; one that throws is reported once and the frame goes on.</p>
 */
public final class LevelDrawRegistry {
    /** One listener at one stage. */
    public interface LevelDrawListener extends OrderedClientListener {
        LevelDrawStage stage();

        void onDraw(LevelDrawContext context);
    }

    /**
     * What a listener gets.
     *
     * @param stage     the stage
     * @param pass      the open main render pass, or null outside it
     * @param target    the main render target the level is drawn into
     * @param camera    the camera state of the frame, or null when unknown
     * @param level     the level render state of the frame, or null when unknown
     * @param minecraft the client
     * @param oitLayer  with improved transparency, the transparency layer being drawn
     *                  (the translucent stages fire once per layer); null otherwise
     * @param modelView the level's view (model-view) matrix for this frame, valid in every
     *                  stage (at {@code AFTER_LEVEL} vanilla has already reset its own)
     */
    public record LevelDrawContext(LevelDrawStage stage, RenderPass pass, RenderTarget target,
                                   CameraRenderState camera, LevelRenderState level, Minecraft minecraft,
                                   String oitLayer, org.joml.Matrix4fc modelView) {
        /** A context outside improved transparency's layers. */
        public LevelDrawContext(LevelDrawStage stage, RenderPass pass, RenderTarget target,
                                CameraRenderState camera, LevelRenderState level, Minecraft minecraft) {
            this(stage, pass, target, camera, level, minecraft, null, null);
        }
    }

    private static final Map<LevelDrawStage, OrderedListenerRegistry<LevelDrawListener>> LISTENERS =
            new EnumMap<>(LevelDrawStage.class);
    private static final Map<LevelDrawStage, AtomicLong> DISPATCHES = new EnumMap<>(LevelDrawStage.class);

    static {
        for (LevelDrawStage stage : LevelDrawStage.values()) {
            LISTENERS.put(stage, new OrderedListenerRegistry<>("level draw listener " + stage));
            DISPATCHES.put(stage, new AtomicLong());
        }
    }

    private LevelDrawRegistry() {
    }

    /** Registers a listener for its stage. */
    public static ClientRegistration register(LevelDrawListener listener) {
        Objects.requireNonNull(listener, "listener");
        return LISTENERS.get(Objects.requireNonNull(listener.stage(), "stage")).register(listener);
    }

    /** Registers {@code action} under {@code id} at {@code stage}. */
    public static ClientRegistration register(String id, LevelDrawStage stage, Consumer<LevelDrawContext> action) {
        Objects.requireNonNull(action, "action");
        return register(new LevelDrawListener() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public LevelDrawStage stage() {
                return stage;
            }

            @Override
            public void onDraw(LevelDrawContext context) {
                action.accept(context);
            }
        });
    }

    /** How many times {@code stage} has been reached since start (for diagnostics and proofs). */
    public static long dispatchCount(LevelDrawStage stage) {
        return DISPATCHES.get(stage).get();
    }

    /** Whether any listener waits at {@code stage}; the hooks skip building a context otherwise. */
    public static boolean hasListeners(LevelDrawStage stage) {
        return LISTENERS.get(stage).listenerCount() > 0;
    }

    private static final java.util.concurrent.atomic.AtomicBoolean REPORTED = new java.util.concurrent.atomic.AtomicBoolean();
    /** The level's view matrix of the frame, taken while the level is drawn (vanilla resets it after render returns). */
    private static final org.joml.Matrix4f FRAME_MODEL_VIEW = new org.joml.Matrix4f();
    private static volatile LevelRenderState frameState;

    /**
     * Integration entry point for the built-in mixins: builds the context of
     * {@code stage} and dispatches it. {@code state} is the frame's level render
     * state, or null to reuse the one of the frame's earlier stages (hooks outside
     * {@code LevelRenderer}, the particle renderer). Mod code must not call it.
     */
    @net.weaver.api.annotation.Internal
    public static void hook(LevelDrawStage stage, RenderPass pass, String oitLayer, LevelRenderState state) {
        try {
            if (state != null) {
                frameState = state;
            } else {
                state = frameState;
            }
            if (stage != LevelDrawStage.AFTER_LEVEL) {
                FRAME_MODEL_VIEW.set(com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrixCopy());
            }
            Minecraft minecraft = Minecraft.getInstance();
            dispatch(new LevelDrawContext(stage, pass,
                    minecraft.gameRenderer == null ? null : minecraft.gameRenderer.mainRenderTarget(),
                    state == null ? null : state.cameraRenderState, state, minecraft, oitLayer,
                    new org.joml.Matrix4f(FRAME_MODEL_VIEW)));
        } catch (Throwable failure) {
            if (REPORTED.compareAndSet(false, true)) {
                System.out.println("[weaver] LEVEL DRAW HOOK FAILED stage=" + stage + " reason=" + failure);
            }
        }
    }

    /** Integration entry point for the built-in mixin. Mod code must not call it. */
    @net.weaver.api.annotation.Internal
    public static void dispatch(LevelDrawContext context) {
        DISPATCHES.get(context.stage()).incrementAndGet();
        LISTENERS.get(context.stage()).forEach("LEVEL DRAW LISTENER", listener -> listener.onDraw(context));
    }
}
