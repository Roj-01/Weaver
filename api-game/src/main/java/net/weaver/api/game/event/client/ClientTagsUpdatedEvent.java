package net.weaver.api.game.event.client;

import net.minecraft.core.RegistryAccess;
import net.weaver.api.event.Event;

/**
 * Fired on the client after the server's tags were received and bound: on
 * joining, when the configuration phase hands over to play
 * ({@code ClientConfigurationPacketListenerImpl#handleConfigurationFinished}),
 * and after {@code /reload} ({@code ClientPacketListener#handleUpdateTags}).
 * In single player the client shares the server's registries;
 * {@link #integrated()} is then {@code true}.
 */
public final class ClientTagsUpdatedEvent extends Event {
    private final RegistryAccess registries;
    private final boolean integrated;

    public ClientTagsUpdatedEvent(RegistryAccess registries, boolean integrated) {
        this.registries = registries;
        this.integrated = integrated;
    }

    public RegistryAccess registries() {
        return registries;
    }

    /** Whether the connection is to the integrated (single-player) server. */
    public boolean integrated() {
        return integrated;
    }
}
