package net.weaver.nf;

import net.minecraft.network.RegistryFriendlyByteBuf;

/**
 * An entity that sends extra data to a client when the client starts seeing it,
 * as NeoForge's {@code IEntityWithComplexSpawn}. The server writes the data right
 * after the entity's spawn packet; the client reads it into its copy.
 *
 * <p>The data travels on a channel of the entity type's mod, which
 * {@link ComplexSpawn#init} (or the mod's entity {@code DeferredRegister}) opens.</p>
 */
public interface IEntityWithComplexSpawn {
    /** Server: writes the data for a client that starts seeing this entity. */
    void writeSpawnData(RegistryFriendlyByteBuf buffer);

    /** Client: reads the data the server wrote. */
    void readSpawnData(RegistryFriendlyByteBuf buffer);
}
