package net.weaver.patcher;

import java.util.List;

/**
 * The Weaver 26.3 patch set. This is intentionally Java code rather than an
 * external JSON descriptor: the operation set is part of the patcher, is
 * reviewed with it, and cannot drift from the verifier.
 */
public final class PatchSet {
    public static final String ID = "weaver-26.3-v1";
    public static final int PATCH_LEVEL = 1;

    public static final String HOLDER_INTERFACE = "net/weaver/patched/WeaverAttachmentHolder";
    public static final String PATCHED_GAME_INTERFACE = "net/weaver/patched/PatchedGame";
    public static final String STORE_CLASS = "net/weaver/patched/WeaverAttachmentStore";
    public static final String STORE_GET = "get";
    public static final String STORE_SET = "set";
    public static final String STORE_REMOVE = "remove";
    public static final String STORE_HAS = "has";

    public static final String GET = "weaver$getAttachment";
    public static final String SET = "weaver$setAttachment";
    public static final String REMOVE = "weaver$removeAttachment";
    public static final String HAS = "weaver$hasAttachment";
    public static final String PATCH_LEVEL_METHOD = "weaver$patchLevel";

    public static final String GET_DESC = "(Ljava/lang/String;)Ljava/lang/Object;";
    public static final String SET_DESC = "(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;";
    public static final String REMOVE_DESC = "(Ljava/lang/String;)Ljava/lang/Object;";
    public static final String HAS_DESC = "(Ljava/lang/String;)Z";
    public static final String PATCH_LEVEL_DESC = "()I";

    public static final String STORE_GET_DESC = "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;";
    public static final String STORE_SET_DESC = "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;";
    public static final String STORE_REMOVE_DESC = "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;";
    public static final String STORE_HAS_DESC = "(Ljava/lang/Object;Ljava/lang/String;)Z";
    public static final String STORE_CLEAR_DESC = "(Ljava/lang/Object;)V";
    public static final String STORE_SIZE_DESC = "()I";

    public static final String METADATA_ENTRY = "META-INF/weaver/patch.json";

    private PatchSet() {
    }

    /**
     * The five core targets. Every target receives the holder interface and
     * delegating attachment methods. PatchedGame is added everywhere too:
     * there are no pre-existing members with these names or descriptors.
     */
    public static List<Target> weaver263v1() {
        return List.of(
                new Target("net/minecraft/world/item/ItemStack", true),
                new Target("net/minecraft/world/entity/Entity", true),
                new Target("net/minecraft/world/level/block/entity/BlockEntity", true),
                new Target("net/minecraft/world/level/Level", true),
                new Target("net/minecraft/world/level/chunk/LevelChunk", true)
        );
    }

    public record Target(String className, boolean patchLevel) {
        public Target {
            if (className == null || className.isBlank()) {
                throw new IllegalArgumentException("target class name is blank");
            }
            if (className.indexOf('.') >= 0 || className.startsWith("/") || className.endsWith("/")) {
                throw new IllegalArgumentException("target class name must be an internal name: " + className);
            }
        }

        public String entryName() {
            return className + ".class";
        }

        public String binaryName() {
            return className.replace('/', '.');
        }
    }
}
