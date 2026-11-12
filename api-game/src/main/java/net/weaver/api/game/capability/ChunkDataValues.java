package net.weaver.api.game.capability;

import net.minecraft.nbt.CompoundTag;

/**
 * Added to {@code SerializableChunkData} by a mixin: the chunk's capability
 * values, carried from the main-thread copy to the write, and from the parse to
 * the read. Internal to Weaver.
 */
public interface ChunkDataValues {
    CompoundTag weaver$capabilityValues();

    void weaver$setCapabilityValues(CompoundTag values);
}
