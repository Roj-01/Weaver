package net.weaver.proof;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.weaver.api.capability.AttachmentRegistry;
import net.weaver.api.capability.AttachmentType;
import net.weaver.api.capability.Capability;
import net.weaver.api.data.DataCodecs;
import net.weaver.api.game.capability.HolderAttachments;
import net.weaver.api.game.capability.LevelAttachmentData;

/**
 * Level and chunk capability values persist through the real save paths, a
 * proto chunk's values reach its full chunk, and player values follow the
 * copy-on-death flag on respawn.
 */
final class GameEventsAttachmentsProof {
    private static final Capability<Integer> CHUNK_COUNT = AttachmentType.builder("weaverproof:chunk_count", Integer.class)
            .codec(DataCodecs.varInt(), 8).build();
    private static final Capability<String> LEVEL_NOTE = AttachmentType.builder("weaverproof:level_note", String.class)
            .codec(DataCodecs.utf8String(64), 80).build();
    private static final Capability<Integer> KEPT_ON_DEATH = AttachmentType.builder("weaverproof:kept_on_death", Integer.class)
            .codec(DataCodecs.varInt(), 8).copyOnDeath(true).build();
    private static final Capability<Integer> LOST_ON_DEATH = AttachmentType.builder("weaverproof:lost_on_death", Integer.class)
            .codec(DataCodecs.varInt(), 8).build();

    private final ServerLevel level;
    private final BlockPos base;
    private final GameEventsProof proof;
    private final AttachmentRegistry registry = AttachmentRegistry.global();

    GameEventsAttachmentsProof(ServerLevel level, BlockPos base, GameEventsProof proof) {
        this.level = level;
        this.base = base;
        this.proof = proof;
    }

    void run() {
        for (Capability<?> capability : new Capability<?>[] {CHUNK_COUNT, LEVEL_NOTE, KEPT_ON_DEATH, LOST_ON_DEATH}) {
            if (registry.find(capability.id()).isEmpty()) {
                registry.register(capability);
            }
        }
        guard("chunk values", this::checkChunk);
        guard("proto chunk values", this::checkProtoChunk);
        guard("level values", this::checkLevel);
        guard("player values on respawn", this::checkRespawn);
    }

    private void checkChunk() {
        LevelChunk chunk = level.getChunkAt(base);
        chunk.tryMarkSaved();
        registry.set(chunk, CHUNK_COUNT, 41);
        boolean dirty = chunk.isUnsaved();
        CompoundTag tag = SerializableChunkData.copyOf(level, chunk).write();
        boolean written = tag.getCompound(HolderAttachments.ROOT_KEY)
                .map(values -> values.contains(CHUNK_COUNT.id())).orElse(false);
        ChunkPos pos = chunk.getPos();
        SerializableChunkData parsed = SerializableChunkData.parse(level, level.palettedContainerFactory(), tag);
        ProtoChunk read = parsed.read(level, level.getPoiManager(),
                new RegionStorageInfo("weaver-proof", level.dimension(), "chunk"), pos);
        ChunkAccess loaded = HolderAttachments.unwrap(read);
        Integer value = registry.get(loaded, CHUNK_COUNT);
        boolean imposter = read instanceof ImposterProtoChunk && registry.get(read, CHUNK_COUNT) != null
                && registry.get(read, CHUNK_COUNT) == 41;
        registry.remove(chunk, CHUNK_COUNT);
        proof.check(dirty && written && value != null && value == 41 && loaded != chunk && imposter,
                "chunk values dirty=" + dirty + " written=" + written + " loaded=" + value + " imposter=" + imposter);
    }

    private void checkProtoChunk() {
        ChunkPos pos = new ChunkPos((base.getX() >> 4) + 200, (base.getZ() >> 4) + 200);
        ProtoChunk proto = new ProtoChunk(pos, UpgradeData.EMPTY, level, level.palettedContainerFactory(), null);
        registry.set(proto, CHUNK_COUNT, 7);
        LevelChunk full = new LevelChunk(level, proto, null);
        Integer value = registry.get(full, CHUNK_COUNT);
        proof.check(value != null && value == 7, "proto chunk values moved=" + value);
    }

    private void checkLevel() {
        registry.set(level, LEVEL_NOTE, "kept");
        level.save(null, true, false);
        LevelAttachmentData data = level.getDataStorage().get(LevelAttachmentData.TYPE);
        boolean stored = data != null && data.values().contains(LEVEL_NOTE.id()) && !data.isDirty();
        registry.remove(level, LEVEL_NOTE);
        HolderAttachments.loadLevel(level);
        String restored = registry.get(level, LEVEL_NOTE);
        registry.remove(level, LEVEL_NOTE);
        level.save(null, true, false);
        boolean cleared = data != null && !data.values().contains(LEVEL_NOTE.id());
        proof.check(stored && "kept".equals(restored) && cleared,
                "level values stored=" + stored + " restored=" + restored + " cleared=" + cleared);
    }

    private void checkRespawn() {
        ServerPlayer original = player("attach-original");
        registry.set(original, KEPT_ON_DEATH, 3);
        registry.set(original, LOST_ON_DEATH, 4);
        ServerPlayer afterDeath = player("attach-died");
        afterDeath.restoreFrom(original, false);
        ServerPlayer afterEnd = player("attach-end");
        afterEnd.restoreFrom(original, true);
        Integer keptDeath = registry.get(afterDeath, KEPT_ON_DEATH);
        Integer lostDeath = registry.get(afterDeath, LOST_ON_DEATH);
        Integer keptEnd = registry.get(afterEnd, KEPT_ON_DEATH);
        Integer lostEnd = registry.get(afterEnd, LOST_ON_DEATH);
        boolean ok = keptDeath != null && keptDeath == 3 && lostDeath == null
                && keptEnd != null && keptEnd == 3 && lostEnd != null && lostEnd == 4;
        proof.check(ok, "player values on respawn death=" + keptDeath + "/" + lostDeath
                + " end=" + keptEnd + "/" + lostEnd);
    }

    private ServerPlayer player(String name) {
        UUID id = UUID.nameUUIDFromBytes(("weaver-proof:" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ServerPlayer player = new ServerPlayer(level.getServer(), level, new GameProfile(id, name),
                ClientInformation.createDefault());
        player.setPos(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
        return player;
    }

    private void guard(String name, ThrowingCheck check) {
        try {
            check.run();
        } catch (Throwable failure) {
            proof.check(false, name + " unexpected " + failure);
        } finally {
            proof.clearListeners();
        }
    }

    private interface ThrowingCheck {
        void run() throws Exception;
    }
}
