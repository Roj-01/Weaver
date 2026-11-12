package net.weaver.mixin;

/**
 * The single authoritative Mixin runtime contract for one launch.
 *
 * <p>This is the boundary the loader and diagnostics use. It extends
 * {@link MixinService} and exposes Mixin's generated inner classes.
 */
public interface MixinRuntime extends MixinService {

    /** Generates a Mixin synthetic/inner class by internal name, or {@code null}. */
    byte[] generateSynthetic(String internalName);

    /** Defines every registered synthetic class in the given runtime loader. */
    void defineSynthetics(ClassLoader loader);
}
