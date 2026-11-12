package net.weaver.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The mods the loader loaded this session, in load order.
 *
 * <p>The loader fills this once at boot, before any mod initializes, so
 * {@link #loaded()} is complete from {@code onInitialize} on. In tools and
 * tests without the loader it is empty.</p>
 */
public final class WeaverMods {
    /**
     * One loaded mod.
     *
     * @param id      the mod id
     * @param version the mod version as declared
     * @param name    the display name
     * @param side    {@code both}, {@code client}, or {@code server}
     * @param description the description, or empty
     * @param authors the authors, possibly empty
     * @param license the license, or empty
     * @param homepage the homepage URL, or empty
     * @param compatibleVersions the version range of this mod that may join each other, or empty for the same version only
     */
    public record Mod(String id, String version, String name, String side, String description,
                      List<String> authors, String license, String homepage, String compatibleVersions) {
        public Mod {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(version, "version");
            name = name == null ? id : name;
            side = side == null ? "both" : side;
            description = description == null ? "" : description;
            authors = authors == null ? List.of() : List.copyOf(authors);
            license = license == null ? "" : license;
            homepage = homepage == null ? "" : homepage;
            compatibleVersions = compatibleVersions == null ? "" : compatibleVersions;
        }

        /** A mod with display fields and no compatible-version range. */
        public Mod(String id, String version, String name, String side, String description,
                   List<String> authors, String license, String homepage) {
            this(id, version, name, side, description, authors, license, homepage, "");
        }

        /** A mod with no display fields beyond its name. */
        public Mod(String id, String version, String name, String side) {
            this(id, version, name, side, "", List.of(), "", "", "");
        }
    }

    private static volatile List<Mod> loaded = List.of();

    private WeaverMods() {
    }

    /** The loaded mods, in load order. */
    public static List<Mod> loaded() {
        return loaded;
    }

    /** The loaded mod with {@code id}, if any. */
    public static Optional<Mod> find(String id) {
        for (Mod mod : loaded) {
            if (mod.id().equals(id)) {
                return Optional.of(mod);
            }
        }
        return Optional.empty();
    }

    /**
     * Whether a peer with {@code peerVersion} of mod {@code id} may connect to this
     * side, which has {@code localVersion}: the versions are equal, or this side's
     * copy of the mod declares a {@code compatibleVersions} range that contains the
     * peer's version.
     *
     * @param id           the mod id
     * @param localVersion this side's version
     * @param peerVersion  the other side's version
     * @return whether the two may connect
     */
    public static boolean networkCompatible(String id, String localVersion, String peerVersion) {
        if (localVersion.equals(peerVersion)) {
            return true;
        }
        String range = find(id).map(Mod::compatibleVersions).orElse("");
        return !range.isBlank() && net.weaver.api.version.VersionRange.matches(peerVersion, range)
                && net.weaver.api.version.VersionRange.matches(localVersion, range);
    }

    /**
     * The client's side of the same check, after the server accepted it: the server
     * already applied its own copy's range. The client refuses only when its own copy
     * declares a range that leaves out the server's version (or its own); a client
     * copy without a range (a release from before the range existed) accepts.
     *
     * @param id            the mod id
     * @param clientVersion the client's version
     * @param serverVersion the server's version
     * @return whether the client keeps the connection
     */
    public static boolean clientAcceptsServer(String id, String clientVersion, String serverVersion) {
        if (clientVersion.equals(serverVersion)) {
            return true;
        }
        String range = find(id).map(Mod::compatibleVersions).orElse("");
        return range.isBlank() || net.weaver.api.version.VersionRange.matches(serverVersion, range)
                && net.weaver.api.version.VersionRange.matches(clientVersion, range);
    }

    /**
     * Sets the loaded mods. Called by the loader at boot; a later call
     * replaces the list.
     *
     * @param mods the loaded mods, in load order
     */
    @net.weaver.api.annotation.Internal
    public static void install(List<Mod> mods) {
        loaded = List.copyOf(mods);
    }
}
