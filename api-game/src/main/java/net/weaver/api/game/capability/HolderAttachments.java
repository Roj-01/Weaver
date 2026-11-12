package net.weaver.api.game.capability;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.weaver.api.capability.AttachmentChange;
import net.weaver.api.capability.AttachmentRegistry;
import net.weaver.api.capability.Capability;

/**
 * Automatic persistence for level and chunk capability values, and the copy
 * of player values on respawn. Driven by the {@code api-game} mixins:
 *
 * <ul>
 *   <li>chunk values are written into the chunk's save data under
 *       {@code weaver:capabilities} and read back when the chunk loads; a
 *       proto chunk's values move to the full chunk built from it;</li>
 *   <li>server level values are kept in {@link LevelAttachmentData}, one
 *       saved-data file per dimension, written whenever the level saves;</li>
 *   <li>on respawn after death, values whose capability has
 *       {@link Capability#copyOnDeath()} move to the new player; returning
 *       from the End keeps every value.</li>
 * </ul>
 *
 * <p>Setting or removing a chunk value marks the chunk unsaved. A value object
 * changed in place is saved only when the chunk is saved for another reason,
 * so set it again after changing it. Level values are compared on every level
 * save, so in-place changes are saved too.</p>
 */
public final class HolderAttachments {
    /** Key of the Weaver child in a holder's save data. */
    public static final String ROOT_KEY = "weaver:capabilities";

    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private HolderAttachments() {
    }

    /** Registers the change listener that marks chunks unsaved. Idempotent. */
    public static void install() {
        if (INSTALLED.compareAndSet(false, true)) {
            AttachmentRegistry.global().addChangeListener(HolderAttachments::changed);
        }
    }

    private static void changed(AttachmentChange change) {
        if (change.holder() instanceof ChunkAccess chunk) {
            unwrap(chunk).markUnsaved();
        }
    }

    /** The chunk values are stored on: the full chunk behind an imposter. */
    public static ChunkAccess unwrap(ChunkAccess chunk) {
        return chunk instanceof ImposterProtoChunk imposter ? imposter.getWrapped() : chunk;
    }

    /**
     * Encodes {@code holder}'s persistent values (and the retained bytes of
     * ids this session cannot read).
     *
     * @return the {@code weaver:capabilities} compound, or {@code null} when empty
     */
    public static CompoundTag save(HolderLookup.Provider lookup, Object holder) {
        try {
            // Most chunks and levels hold nothing; skip building a carrier for them.
            if (!AttachmentRegistry.global().hasPersistentData(holder)) {
                return null;
            }
            TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, lookup);
            if (!AttachmentRegistry.global().saveHolder(holder, output)) {
                return null;
            }
            Optional<CompoundTag> values = output.buildResult().getCompound(ROOT_KEY);
            return values.isPresent() && !values.get().isEmpty() ? values.get() : null;
        } catch (RuntimeException | LinkageError | StackOverflowError failure) {
            // A world save must never fail because of one mod's attachment data.
            report("save", holder, failure);
            return null;
        }
    }

    private static void report(String operation, Object holder, Throwable failure) {
        System.out.println("[weaver] ATTACHMENTS " + operation.toUpperCase(java.util.Locale.ROOT)
                + " FAILED holder=" + (holder == null ? "null" : holder.getClass().getSimpleName())
                + " reason=" + failure + " action=skipped");
    }

    /** Restores values written by {@link #save}; {@code values} may be {@code null}. */
    public static void load(HolderLookup.Provider lookup, Object holder, CompoundTag values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        try {
            CompoundTag carrier = new CompoundTag();
            carrier.put(ROOT_KEY, values.copy());
            AttachmentRegistry.global().loadHolder(holder,
                    TagValueInput.create(ProblemReporter.DISCARDING, lookup, carrier));
        } catch (RuntimeException | LinkageError | StackOverflowError failure) {
            report("load", holder, failure);
        }
    }

    /**
     * Moves every value from {@code from} to {@code to}: persistent values
     * and retained bytes through a save and load, runtime-only values by
     * reference.
     */
    public static void transfer(HolderLookup.Provider lookup, Object from, Object to) {
        load(lookup, to, save(lookup, from));
        AttachmentRegistry registry = AttachmentRegistry.global();
        for (Capability<?> capability : registry.registeredCapabilities()) {
            if (!capability.persistent()) {
                copy(registry, capability, from, to);
            }
        }
    }

    /** Called when {@code fresh} replaces {@code original} on respawn. */
    public static void copyPlayer(ServerPlayer original, ServerPlayer fresh, boolean keepEverything) {
        if (keepEverything) {
            transfer(fresh.level().registryAccess(), original, fresh);
            return;
        }
        AttachmentRegistry registry = AttachmentRegistry.global();
        for (Capability<?> capability : registry.registeredCapabilities()) {
            if (capability.copyOnDeath()) {
                copy(registry, capability, original, fresh);
            }
        }
    }

    /** Loads a server level's saved values; called once when the level is created. */
    public static void loadLevel(ServerLevel level) {
        install();
        try {
            LevelAttachmentData data = level.getDataStorage().computeIfAbsent(LevelAttachmentData.TYPE);
            load(level.registryAccess(), level, data.values());
        } catch (RuntimeException | LinkageError | StackOverflowError failure) {
            report("load", level, failure);
        }
    }

    /** Stores a server level's current values for the coming save, if they changed. */
    public static void saveLevel(ServerLevel level) {
        try {
            LevelAttachmentData data = level.getDataStorage().computeIfAbsent(LevelAttachmentData.TYPE);
            data.update(save(level.registryAccess(), level));
        } catch (RuntimeException | LinkageError | StackOverflowError failure) {
            report("save", level, failure);
        }
    }

    private static <T> void copy(AttachmentRegistry registry, Capability<T> capability, Object from, Object to) {
        Optional<T> value = registry.find(from, capability);
        if (value.isPresent()) {
            registry.set(to, capability, value.get());
        }
    }
}
