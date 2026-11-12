package net.weaver.mixin;

/**
 * The mixin engine contract the loader talks to.
 *
 * <p>Lifecycle: {@link #boot(ClassLoader)} once from {@code premain}, then any number of
 * {@link #addConfig} calls. Weaving is load-time only.
 */
public interface MixinService {

    /** Boots the engine. Must run before any game class is defined. Idempotent. */
    void boot(ClassLoader gameLoader);

    /**
     * Registers a {@code *.mixins.json} buffer against {@code modLoader}.
     *
     * <p>The config bytes are kept out of the system loader: the engine reads the config,
     * mixin classes and refmap through {@code modLoader}, so two mods with the same resource
     * names cannot collide.
     */
    void addConfig(String modId, String configName, byte[] config, ClassLoader modLoader);

    /** True when this config applied to that target class (bytes woven, not merely armed). */
    boolean isApplied(String modId, String targetClass);

    /** Counters plus measured timings for the current process. */
    MixinStats stats();
}
