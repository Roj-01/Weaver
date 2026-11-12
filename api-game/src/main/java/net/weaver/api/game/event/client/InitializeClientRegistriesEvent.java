package net.weaver.api.game.event.client;

import net.minecraft.data.DataGenerator;
import net.weaver.api.event.Event;

/**
 * Fired when the vanilla client data generator ({@code net.minecraft.client.data.Main},
 * run with {@code --client} or {@code --all}) has bootstrapped the game and the
 * client and is about to add its providers. Mods set up client-side state their
 * own generators need here (for example registering model loaders) and may add
 * providers to {@link #generator()}. It never fires in a normal game launch.
 */
public final class InitializeClientRegistriesEvent extends Event {
    private final DataGenerator generator;
    private final boolean clientGenerators;

    public InitializeClientRegistriesEvent(DataGenerator generator, boolean clientGenerators) {
        this.generator = generator;
        this.clientGenerators = clientGenerators;
    }

    public DataGenerator generator() {
        return generator;
    }

    /** Whether the client generators were requested. */
    public boolean clientGenerators() {
        return clientGenerators;
    }
}
