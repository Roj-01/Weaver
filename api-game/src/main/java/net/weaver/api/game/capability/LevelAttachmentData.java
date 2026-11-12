package net.weaver.api.game.capability;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Saved data holding one server level's capability values, in
 * {@code data/weaver/level_capabilities.dat} of the dimension folder. The
 * contents are the {@code weaver:capabilities} compound that entities and
 * block entities also use.
 */
public final class LevelAttachmentData extends SavedData {
    private static final Codec<LevelAttachmentData> CODEC =
            CompoundTag.CODEC.xmap(LevelAttachmentData::new, LevelAttachmentData::values);

    /**
     * The saved-data type. Vanilla needs a data-fix type; command storage's
     * fixes do not touch this layout.
     */
    public static final SavedDataType<LevelAttachmentData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("weaver", "level_capabilities"),
            LevelAttachmentData::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private CompoundTag values;

    public LevelAttachmentData() {
        this(new CompoundTag());
    }

    private LevelAttachmentData(CompoundTag values) {
        this.values = values;
    }

    /** The stored compound; empty when the level has no values. */
    public CompoundTag values() {
        return values;
    }

    /** Replaces the stored values and marks the data dirty when they differ. */
    void update(CompoundTag current) {
        CompoundTag next = current == null ? new CompoundTag() : current;
        if (!next.equals(values)) {
            values = next;
            setDirty();
        }
    }
}
