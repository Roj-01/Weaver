package net.weaver.api.network;

import java.util.Objects;

/**
 * One mod identity advertised during a handshake.
 *
 * <p>The id uses the same namespace pattern as a {@link PayloadType}: lower-case
 * letters, digits, dots, underscores, and hyphens. The version is the mod's own
 * version string and is intentionally opaque to Weaver; only its length and
 * character policy are enforced.</p>
 *
 * @param id      the mod id matching {@code [a-z0-9_.-]+}, not {@code null}
 * @param version the non-blank mod version, at most
 *                {@link NetworkProtocol#MAX_MOD_VERSION_CHARS} chars and free
 *                of ISO control characters
 */
public record NetworkModInfo(String id, String version)
{
    /**
     * Validates the components.
     *
     * @throws NullPointerException     when {@code id} or {@code version} is
     *                                  {@code null}
     * @throws IllegalArgumentException when the id or version is invalid
     */
    public NetworkModInfo
    {
        NetworkValidation.requireModId(id, "id");
        NetworkValidation.requireNonBlank(version, "version");
        NetworkValidation.requireMaxChars(version, "version",
                NetworkProtocol.MAX_MOD_VERSION_CHARS);
        NetworkValidation.requireNoIsoControl(version, "version");
    }

    /**
     * Creates a mod identity.
     *
     * @param id      the mod id, not {@code null}
     * @param version the non-blank mod version, not {@code null}
     * @return a new mod identity, never {@code null}
     * @throws NullPointerException     when either argument is {@code null}
     * @throws IllegalArgumentException when the id or version is invalid
     */
    public static NetworkModInfo of(String id, String version)
    {
        return new NetworkModInfo(id, version);
    }
}
