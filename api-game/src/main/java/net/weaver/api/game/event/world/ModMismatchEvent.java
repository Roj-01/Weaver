package net.weaver.api.game.event.world;

import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.weaver.api.event.Event;

/**
 * Fired when a world loads with a different set of mods than it last saved
 * with: a mod added, removed, or at another version. Weaver records the mod
 * list in the overworld's data ({@code data/weaver/mod_list.dat}) and compares
 * it when the overworld loads; a new world, or one saved before Weaver kept the
 * list, fires nothing. After the event the current list is recorded.
 *
 * <p>Use it to warn, migrate data, or back up. It cannot stop the load.
 * Server side (also in single player).</p>
 */
public final class ModMismatchEvent extends Event {
    /**
     * One mod that differs.
     *
     * @param modId           the mod id
     * @param previousVersion the version the world last saved with, or {@code null} for a mod that is new
     * @param currentVersion  the version loaded now, or {@code null} for a mod that is gone
     */
    public record Mismatch(String modId, String previousVersion, String currentVersion) {
        public boolean added() {
            return previousVersion == null;
        }

        public boolean removed() {
            return currentVersion == null;
        }
    }

    private final MinecraftServer server;
    private final List<Mismatch> mismatches;

    public ModMismatchEvent(MinecraftServer server, List<Mismatch> mismatches) {
        super(false);
        this.server = server;
        this.mismatches = List.copyOf(mismatches);
    }

    public MinecraftServer server() {
        return server;
    }

    /** The mods that differ, in mod id order. */
    public List<Mismatch> mismatches() {
        return mismatches;
    }
}
