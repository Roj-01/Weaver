package net.weaver.api.game.event.support;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.concurrent.atomic.AtomicBoolean;
import net.weaver.api.game.event.mixin.client.PoseStackAccessor;

/**
 * Keeps render listeners from leaving a pose stack unbalanced: vanilla checks
 * the stack is empty after each frame and crashes the client otherwise. Client only.
 */
public final class PoseStacks {
    private static final AtomicBoolean WARNED = new AtomicBoolean();

    private PoseStacks() {
    }

    public static int depth(PoseStack stack) {
        return ((PoseStackAccessor) stack).weaver$depth();
    }

    /** Pops back to {@code depth}; a listener that pushed without popping is reported once. */
    public static void restore(PoseStack stack, int depth, String event) {
        int current = depth(stack);
        if (current == depth) {
            return;
        }
        if (WARNED.compareAndSet(false, true)) {
            System.out.println("[weaver] POSE STACK UNBALANCED event=" + event + " expected=" + depth
                    + " found=" + current + " action=" + (current > depth ? "popped" : "left")
                    + " (a listener pushed or popped without its pair; reported once)");
        }
        while (depth(stack) > depth) {
            stack.popPose();
        }
    }
}
