package net.weaver.api.game.event.client;

import java.util.function.Function;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleResources;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.weaver.api.event.Event;
import net.weaver.api.game.event.support.ParticleRegistrations;

/**
 * Fired once on the client while the game starts, after vanilla registered
 * its particle providers ({@code ParticleResources#registerProviders}).
 * Register a provider for each mod particle type here. Register the event
 * listener during mod setup.
 */
public final class RegisterParticleProvidersEvent extends Event {
    private final ParticleResources resources;

    public RegisterParticleProvidersEvent(ParticleResources resources) {
        this.resources = resources;
    }

    public ParticleResources resources() {
        return resources;
    }

    /** Registers a provider that needs no sprites from a particle definition. */
    public <T extends ParticleOptions> void register(ParticleType<T> type, ParticleProvider<T> provider) {
        ParticleRegistrations.register(resources, type, provider);
    }

    /**
     * Registers a provider that draws with the sprites listed in the
     * particle's definition file ({@code assets/<ns>/particles/<name>.json});
     * the factory receives the sprite set, filled on every resource reload.
     */
    public <T extends ParticleOptions> void registerSprites(ParticleType<T> type,
                                                            Function<SpriteSet, ParticleProvider<T>> factory) {
        ParticleRegistrations.registerSprites(resources, type, factory);
    }
}
