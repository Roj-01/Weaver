package net.weaver.nf;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.weaver.api.game.client.camera.CameraRenderContext;
import net.weaver.api.game.client.camera.CameraRenderListener;
import net.weaver.api.game.client.camera.CameraRenderRegistry;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * The camera angles of the frame, as NeoForge's
 * {@code ViewportEvent.ComputeCameraAngles}: listeners read and set the yaw, the
 * pitch, and the roll in degrees. Backed by Weaver's {@code CameraRenderRegistry};
 * the camera's orientation and view matrix are rebuilt from the new angles the way
 * vanilla builds them.
 */
public final class CameraAngles {
    private static final float DEGREES = (float) (Math.PI / 180.0);
    private static final List<Consumer<CameraAngles>> LISTENERS = new CopyOnWriteArrayList<>();
    private static boolean installed;

    private final Camera camera;
    private final float partialTick;
    private float yaw;
    private float pitch;
    private float roll;

    private CameraAngles(Camera camera, float partialTick, float yaw, float pitch, float roll) {
        this.camera = camera;
        this.partialTick = partialTick;
        this.yaw = yaw;
        this.pitch = pitch;
        this.roll = roll;
    }

    public static void listen(Consumer<CameraAngles> listener) {
        synchronized (CameraAngles.class) {
            LISTENERS.add(listener);
            if (installed) {
                return;
            }
            installed = true;
        }
        CameraRenderRegistry.register(new CameraRenderListener() {
            @Override
            public String id() {
                return "weaver_nf:camera_angles";
            }

            @Override
            public void onExtractCamera(CameraRenderContext context) {
                apply(context.camera(), context.partialTick(), context.state());
            }
        });
    }

    /** Runs the listeners on the angles; returns {yaw, pitch, roll} after them. */
    public static float[] fire(Camera camera, float partialTick, float yaw, float pitch) {
        CameraAngles event = new CameraAngles(camera, partialTick, yaw, pitch, 0.0F);
        for (Consumer<CameraAngles> listener : LISTENERS) {
            listener.accept(event);
        }
        return new float[] {event.yaw, event.pitch, event.roll};
    }

    private static void apply(Camera camera, float partialTick, CameraRenderState state) {
        float[] angles = fire(camera, partialTick, state.yRot, state.xRot);
        if (angles[0] == state.yRot && angles[1] == state.xRot && angles[2] == 0.0F) {
            return;
        }
        state.yRot = angles[0];
        state.xRot = angles[1];
        state.orientation.rotationYXZ((float) Math.PI - angles[0] * DEGREES, -angles[1] * DEGREES, -angles[2] * DEGREES);
        state.viewRotationMatrix.set(new Matrix4f().rotation(state.orientation.conjugate(new Quaternionf())));
    }

    public Camera getCamera() {
        return camera;
    }

    public float getPartialTick() {
        return partialTick;
    }

    public float getYaw() {
        return yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }

    public float getRoll() {
        return roll;
    }

    public void setRoll(float roll) {
        this.roll = roll;
    }
}
