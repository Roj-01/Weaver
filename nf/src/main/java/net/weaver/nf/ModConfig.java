package net.weaver.nf;

/** Config kinds, as NeoForge's {@code ModConfig.Type}. */
public final class ModConfig {
    private ModConfig() {
    }

    /**
     * Where a config applies. {@code SERVER} and {@code SYNCED} values are sent
     * from the server to clients that join; the others stay local.
     */
    public enum Type {
        CLIENT,
        COMMON,
        SERVER,
        SYNCED,
        LOCAL,
        STARTUP
    }
}
