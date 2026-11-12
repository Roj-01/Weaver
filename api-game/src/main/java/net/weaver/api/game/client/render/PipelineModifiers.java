package net.weaver.api.game.client.render;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.Identifier;

/**
 * Render pipeline modifiers, as NeoForge has them: while a modifier is pushed,
 * every pipeline the game asks to draw with ({@code RenderSystem
 * #getCompiledPipelineNullable}, which {@code getCompiledPipeline} also goes
 * through) is first given to it, and the game draws with the pipeline it gives
 * back. A modifier typically swaps a shader or a shader define, or changes
 * blending or depth, for everything drawn between {@link #push(Identifier)}
 * and {@link #pop()}; {@link RenderPipelineCopy} makes the changed pipeline.
 *
 * <p>Each modifier is asked once per pipeline; the result is kept, so the game
 * compiles it once. A pipeline a modifier returned is never modified again.
 * When pushed modifiers nest, the most recently pushed one is asked first.
 * {@link #invalidate()} forgets the kept results (for example when what a
 * modifier makes depends on reloaded resources).</p>
 *
 * <p>{@link #register} may be called from any thread; {@link #push},
 * {@link #pop} and the drawing they surround belong to the render thread.</p>
 */
public final class PipelineModifiers {
    /** Makes the pipeline to draw with instead of {@code pipeline}. */
    @FunctionalInterface
    public interface Modifier {
        /**
         * @param pipeline the pipeline the game asked for
         * @param copyName a unique name for a changed copy
         *                 ({@code <modifier namespace>:<modifier path>/<pipeline namespace>/<pipeline path>})
         * @return the pipeline to draw with: {@code pipeline} itself to leave it, or a copy;
         *         {@code null} is read as {@code pipeline}
         */
        RenderPipeline modify(RenderPipeline pipeline, Identifier copyName);
    }

    private static final Map<Identifier, Modifier> MODIFIERS = new ConcurrentHashMap<>();
    private static final Map<Identifier, Map<RenderPipeline, RenderPipeline>> MADE = new ConcurrentHashMap<>();
    private static final Set<RenderPipeline> PRODUCED = Collections.synchronizedSet(
            Collections.newSetFromMap(new IdentityHashMap<>()));
    private static final ArrayDeque<Identifier> STACK = new ArrayDeque<>();

    private PipelineModifiers() {
    }

    /**
     * Registers (or replaces) the modifier {@code key}. A replaced modifier's
     * kept results are forgotten.
     */
    public static void register(Identifier key, Modifier modifier) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(modifier, "modifier");
        if (MODIFIERS.put(key, modifier) != null) {
            MADE.remove(key);
        }
    }

    /** Whether a modifier is registered as {@code key}. */
    public static boolean isRegistered(Identifier key) {
        return MODIFIERS.containsKey(key);
    }

    /** Starts modifying with {@code key} (render thread); pair with {@link #pop()}. */
    public static void push(Identifier key) {
        STACK.push(Objects.requireNonNull(key, "key"));
    }

    /**
     * Stops the most recently pushed modifier (render thread).
     *
     * @throws IllegalStateException when nothing is pushed
     */
    public static void pop() {
        if (STACK.isEmpty()) {
            throw new IllegalStateException("No pipeline modifier is pushed");
        }
        STACK.pop();
    }

    /** Whether any modifier is pushed. */
    public static boolean active() {
        return !STACK.isEmpty();
    }

    /** Forgets every kept result; modifiers are asked again. */
    public static void invalidate() {
        MADE.clear();
        PRODUCED.clear();
    }

    /**
     * The pipeline to draw with instead of {@code pipeline}. Integration entry
     * point for the built-in mixin; mods use {@link #push(Identifier)}.
     */
    public static RenderPipeline apply(RenderPipeline pipeline) {
        if (STACK.isEmpty() || pipeline == null || PRODUCED.contains(pipeline)) {
            return pipeline;
        }
        RenderPipeline current = pipeline;
        for (Identifier key : STACK) {
            Modifier modifier = MODIFIERS.get(key);
            if (modifier == null) {
                continue;
            }
            RenderPipeline input = current;
            current = MADE.computeIfAbsent(key, ignored -> new ConcurrentHashMap<>()).computeIfAbsent(input,
                    asked -> make(key, modifier, asked));
        }
        return current;
    }

    private static RenderPipeline make(Identifier key, Modifier modifier, RenderPipeline asked) {
        Identifier location = asked.getLocation();
        Identifier name = Identifier.fromNamespaceAndPath(key.getNamespace(),
                key.getPath() + "/" + location.getNamespace() + "/" + location.getPath());
        RenderPipeline made;
        try {
            made = modifier.modify(asked, name);
        } catch (RuntimeException failure) {
            System.err.println("[weaver] PIPELINE MODIFIER FAILED key=" + key + " pipeline=" + location
                    + " reason=" + failure);
            made = null;
        }
        if (made == null || made == asked) {
            return asked;
        }
        PRODUCED.add(made);
        return made;
    }
}
