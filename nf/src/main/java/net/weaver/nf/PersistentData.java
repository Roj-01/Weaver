package net.weaver.nf;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.weaver.api.ModContext;
import net.weaver.api.capability.AttachmentRegistry;
import net.weaver.api.capability.AttachmentType;
import net.weaver.api.capability.CapabilityCodec;
import net.weaver.api.capability.CapabilitySync;

/**
 * A free-form tag every entity carries, saved with it, as NeoForge's
 * {@code Entity#getPersistentData()}. It is a Weaver attachment: saved with the
 * entity, never sent to clients, up to 1 MiB. As on NeoForge, a player who
 * respawns (after a death or from the End) keeps only the {@value #PERSISTED}
 * tag inside it; the rest starts empty.
 */
public final class PersistentData {
    /** The attachment id. */
    public static final String KEY = "weaver_nf:persistent_data";

    /** The tag a player keeps when he respawns, as NeoForge's {@code Player.PERSISTED_NBT_TAG}. */
    public static final String PERSISTED = "PlayerPersisted";

    private static final int MAX_BYTES = 1024 * 1024;
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private static final AttachmentType<CompoundTag> TYPE = AttachmentType.builder(KEY, CompoundTag.class)
            .defaultSupplier(CompoundTag::new)
            .codec(new TagCodec(), MAX_BYTES)
            .copyOnDeath(false)
            .sync(CapabilitySync.NONE)
            .build();

    static {
        register();
    }

    private PersistentData() {
    }

    /** Kept for mods that call it from {@code onInitialize}; the attachment registers itself. */
    public static void init(ModContext context) {
        register();
    }

    private static void register() {
        if (REGISTERED.compareAndSet(false, true)) {
            AttachmentRegistry registry = AttachmentRegistry.global();
            registry.register(TYPE);
            registry.attach(TYPE, Entity.class, entity -> new CompoundTag());
            net.weaver.api.event.EventBus.GAME.addListener(net.weaver.api.game.event.player.PlayerCloneEvent.class, event -> {
                CompoundTag old = of(event.original());
                if (old != null && old.contains(PERSISTED)) {
                    of(event.player()).put(PERSISTED, old.get(PERSISTED).copy());
                }
            });
        }
    }

    /** The entity's tag; changes to it are saved with the entity. */
    public static CompoundTag of(Entity entity) {
        return AttachmentRegistry.global().get(entity, TYPE);
    }

    /** Kept for code written against the copies; Weaver saves the tag with the entity by itself. */
    public static void save(Entity entity, ValueOutput output) {
    }

    /** Kept for code written against the copies; Weaver loads the tag with the entity by itself. */
    public static void load(Entity entity, ValueInput input) {
    }

    private static final class TagCodec implements CapabilityCodec<CompoundTag> {
        @Override
        public byte[] encode(CompoundTag value) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                NbtIo.write(value, out);
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
            return bytes.toByteArray();
        }

        @Override
        public CompoundTag decode(byte[] encoded) {
            try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(encoded))) {
                return NbtIo.read(in, NbtAccounter.create(MAX_BYTES * 2L));
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        }
    }
}
