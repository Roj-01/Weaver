package net.weaver.nf;

import net.weaver.api.WeaverMods;

/** Which mods are installed, as NeoForge's {@code ModList.get().isLoaded(id)}. */
public final class Mods {
    private Mods() {
    }

    /** Whether the mod {@code id} is installed. */
    public static boolean isLoaded(String id) {
        return WeaverMods.find(id).isPresent();
    }
}
