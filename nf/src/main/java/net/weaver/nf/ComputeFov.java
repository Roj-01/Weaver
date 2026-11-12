package net.weaver.nf;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.weaver.api.game.client.camera.FovContext;
import net.weaver.api.game.client.camera.FovModifier;
import net.weaver.api.game.client.camera.FovModifierRegistry;

/**
 * The field of view of the frame, as NeoForge's {@code ViewportEvent.ComputeFov}:
 * listeners read and set it in degrees. Backed by Weaver's
 * {@code FovModifierRegistry}.
 */
public final class ComputeFov {
    private static final List<Consumer<ComputeFov>> LISTENERS = new CopyOnWriteArrayList<>();
    private static boolean installed;

    private final float partialTick;
    private double fov;

    private ComputeFov(float partialTick, double fov) {
        this.partialTick = partialTick;
        this.fov = fov;
    }

    public static synchronized void listen(Consumer<ComputeFov> listener) {
        LISTENERS.add(listener);
        if (!installed) {
            installed = true;
            FovModifierRegistry.register(new FovModifier() {
                @Override
                public String id() {
                    return "weaver_nf:compute_fov";
                }

                @Override
                public float fovMultiplier(FovContext context) {
                    ComputeFov event = new ComputeFov(context.partialTick(), context.baseFov());
                    for (Consumer<ComputeFov> each : LISTENERS) {
                        each.accept(event);
                    }
                    return context.baseFov() == 0.0F ? 1.0F : (float) (event.fov / context.baseFov());
                }
            });
        }
    }

    public float getPartialTick() {
        return partialTick;
    }

    public double getFOV() {
        return fov;
    }

    public void setFOV(double fov) {
        this.fov = fov;
    }
}
