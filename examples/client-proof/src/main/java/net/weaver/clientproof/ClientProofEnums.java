package net.weaver.clientproof;

/** Arguments of the constants the client proof adds to vanilla enums (META-INF/enumextensions.json). */
final class ClientProofEnums {
    private ClientProofEnums() {
    }

    /** HumanoidModel.ArmPose(boolean twoHanded, boolean affectsOffhandPose). */
    static Object[] steady() {
        return new Object[] {false, false};
    }
}
