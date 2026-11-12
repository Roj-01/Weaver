package net.weaver.core;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Guards the boot sequence against activating optional game features before
 * the class-file transformers that they depend on are installed.
 *
 * <p>The bridge and capability sync load game classes during activation. If
 * they run before the hook, registry, or lifecycle transformers are registered,
 * those classes are defined without the transforms and the mistake is silent.
 * The loader marks this state only after every transformer has been added.
 */
public final class BootPhaseOrder {

    private static final AtomicBoolean TRANSFORMERS_INSTALLED = new AtomicBoolean();

    private BootPhaseOrder() {
    }

    /**
     * Records that every class-file transformer has been installed.
     */
    public static void markTransformersInstalled() {
        TRANSFORMERS_INSTALLED.set(true);
    }

    /**
     * Reports whether the transformer phase has completed.
     *
     * @return {@code true} after {@link #markTransformersInstalled()}
     */
    public static boolean transformersInstalled() {
        return TRANSFORMERS_INSTALLED.get();
    }

    /**
     * Fails when an optional game feature is activated before the transformer
     * phase completed.
     *
     * @param phase the operation that is about to load game classes
     * @throws IllegalStateException when the transformer phase has not completed
     */
    public static void requireTransformersInstalled(String phase) {
        if (!TRANSFORMERS_INSTALLED.get()) {
            throw new IllegalStateException("Weaver boot-order violation: " + phase
                    + " ran before all class-file transformers were installed");
        }
    }

    /**
     * Clears the guard state for an isolated test or embedding run.
     */
    public static void resetForTests() {
        TRANSFORMERS_INSTALLED.set(false);
    }
}
