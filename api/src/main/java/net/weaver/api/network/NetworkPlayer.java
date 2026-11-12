package net.weaver.api.network;

import java.util.Objects;
import java.util.UUID;

/**
 * A game-independent snapshot of one player identity.
 *
 * <p>The id is the stable account or profile id and the name is the display
 * name at the time the value was created. Both are captured at handshake time;
 * a rename later in the session does not update existing instances.</p>
 *
 * @param id   the unique player id, not {@code null}
 * @param name the non-blank display name, at most
 *             {@link NetworkProtocol#MAX_PLAYER_NAME_CHARS} chars and free of
 *             ISO control characters
 */
public record NetworkPlayer(UUID id, String name)
{
    /**
     * Validates and normalizes the components.
     *
     * @throws NullPointerException     when {@code id} or {@code name} is
     *                                  {@code null}
     * @throws IllegalArgumentException when {@code name} is blank, too long, or
     *                                  contains an ISO control character
     */
    public NetworkPlayer
    {
        Objects.requireNonNull(id, "id");
        NetworkValidation.requireNonBlank(name, "name");
        NetworkValidation.requireMaxChars(name, "name", NetworkProtocol.MAX_PLAYER_NAME_CHARS);
        NetworkValidation.requireNoIsoControl(name, "name");
    }

    /**
     * Creates a player snapshot.
     *
     * @param id   the unique player id, not {@code null}
     * @param name the non-blank display name, not {@code null}
     * @return a new player value, never {@code null}
     * @throws NullPointerException     when {@code id} or {@code name} is
     *                                  {@code null}
     * @throws IllegalArgumentException when {@code name} is invalid
     */
    public static NetworkPlayer of(UUID id, String name)
    {
        return new NetworkPlayer(id, name);
    }
}
