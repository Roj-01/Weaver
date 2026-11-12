package net.weaver.api.game.client.model;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.HumanoidArm;

/**
 * How arm poses that mods add to {@code HumanoidModel.ArmPose} (see
 * {@code net.weaver.api.EnumExtensions}) move the arms. Vanilla poses its own
 * arm poses; for an added pose it does nothing, and the registered transformer
 * runs instead, after vanilla set the arm up.
 *
 * <pre>{@code
 * ArmPose AIM = EnumExtensions.get(HumanoidModel.ArmPose.class, "EXAMPLEMOD_AIM");
 * ArmPoses.register(AIM, (model, state, arm) -> {
 *     var part = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
 *     part.xRot = -1.5F + model.head.xRot;
 * });
 * }</pre>
 *
 * <p>Items choose a pose with {@code ClientItemExtension.getArmPose}.</p>
 */
public final class ArmPoses {
    /** Moves one arm for a pose. */
    @FunctionalInterface
    public interface Transformer {
        void apply(HumanoidModel<?> model, HumanoidRenderState state, HumanoidArm arm);
    }

    private static final Map<HumanoidModel.ArmPose, Transformer> TRANSFORMERS = new ConcurrentHashMap<>();

    private ArmPoses() {
    }

    /** Registers how {@code pose} moves an arm. A later registration for the same pose replaces it. */
    public static void register(HumanoidModel.ArmPose pose, Transformer transformer) {
        TRANSFORMERS.put(Objects.requireNonNull(pose, "pose"), Objects.requireNonNull(transformer, "transformer"));
    }

    /** The transformer of {@code pose}, or null. */
    public static Transformer get(HumanoidModel.ArmPose pose) {
        return pose == null ? null : TRANSFORMERS.get(pose);
    }
}
