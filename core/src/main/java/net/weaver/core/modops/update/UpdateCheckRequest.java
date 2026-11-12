package net.weaver.core.modops.update;

import java.util.List;
import java.util.Objects;

/**
 * Immutable request passed to an {@link UpdateCheck}.
 *
 * @param loaderApiVersion loader API version the game is running against
 * @param gameVersion      Minecraft version
 * @param side             process side, normally {@code client} or {@code server}
 * @param installed        installed mods; copied into an immutable list
 */
public record UpdateCheckRequest(
        String loaderApiVersion,
        String gameVersion,
        String side,
        List<InstalledMod> installed) {

    /**
     * Creates a request and copies the installed-mod list.
     *
     * @throws NullPointerException if any component is {@code null}
     */
    public UpdateCheckRequest {
        Objects.requireNonNull(loaderApiVersion, "loaderApiVersion");
        Objects.requireNonNull(gameVersion, "gameVersion");
        Objects.requireNonNull(side, "side");
        installed = List.copyOf(Objects.requireNonNull(installed, "installed"));
    }
}
