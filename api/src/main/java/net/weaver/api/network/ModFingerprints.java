package net.weaver.api.network;

import java.util.Map;
import java.util.Objects;
import net.weaver.api.annotation.Internal;

/**
 * The SHA-256 of each loaded mod jar, and the loader build, recorded by the
 * loader at boot. A server advertises them in its status reply so a launcher
 * (Weaver Hub) can fetch exactly the files the server runs before joining.
 */
@Internal
public final class ModFingerprints
{
    private static volatile Map<String, String> sha256ById = Map.of();
    private static volatile String loaderVersion = "";

    private ModFingerprints()
    {
    }

    /** Called by the loader once the mods are discovered. */
    public static void install(String loader, Map<String, String> sha256)
    {
        loaderVersion = Objects.requireNonNull(loader, "loader");
        sha256ById = Map.copyOf(Objects.requireNonNull(sha256, "sha256"));
    }

    /** The lower-case hex SHA-256 of the mod's jar, or null when unknown. */
    public static String sha256(String modId)
    {
        return sha256ById.get(modId);
    }

    /** The loader build version, such as {@code 2.3.2}; empty before boot. */
    public static String loaderVersion()
    {
        return loaderVersion;
    }
}
