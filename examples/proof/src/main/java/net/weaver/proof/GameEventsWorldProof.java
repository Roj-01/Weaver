package net.weaver.proof;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import net.weaver.api.game.event.player.DatapackSyncEvent;
import net.weaver.api.game.event.world.ChunkDataSaveEvent;
import net.weaver.api.game.event.world.ChunkTicketLevelUpdatedEvent;
import net.weaver.api.game.event.world.GameRuleChangedEvent;
import net.weaver.api.game.event.world.PotentialSpawnsEvent;
import net.weaver.api.game.event.world.VanillaGameEvent;

/**
 * Batch 5 (server, levels, data) part of {@link GameEventsProof}. Events that
 * fire only at world creation, at a level's first tick, on a connected
 * player's chunk stream, after a night's sleep, or during a village siege are
 * covered by the mixin-applied check.
 */
final class GameEventsWorldProof {
    private final ServerLevel level;
    private final BlockPos base;
    private final GameEventsProof proof;

    GameEventsWorldProof(ServerLevel level, BlockPos base, GameEventsProof proof) {
        this.level = level;
        this.base = base;
        this.proof = proof;
    }

    void run() {
        guard("game rule", this::checkGameRule);
        guard("vanilla game event", this::checkVanillaGameEvent);
        guard("potential spawns", this::checkPotentialSpawns);
        guard("chunk save", this::checkChunkSave);
        guard("chunk ticket", this::checkChunkTicket);
        guard("datapack sync", this::checkDatapackSync);
        guard("level sound", this::checkLevelSound);
        guard("server reload listeners", this::checkServerReloadListeners);
        guard("creative tab contents", this::checkCreativeTab);
        guard("mod creative tab", this::checkModCreativeTab);
        guard("data registries", () -> ProofDataRegistries.check(proof));
        guard("mod packs", () -> ProofDataRegistries.checkModPack(proof, level.getServer()));
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

    private void checkGameRule() {
        AtomicInteger fired = new AtomicInteger();
        proof.listen(GameRuleChangedEvent.class, event -> {
            if (event.rule() == GameRules.SPAWN_PHANTOMS) {
                fired.incrementAndGet();
            }
        });
        boolean before = level.getGameRules().get(GameRules.SPAWN_PHANTOMS);
        level.getGameRules().set(GameRules.SPAWN_PHANTOMS, !before, level.getServer());
        level.getGameRules().set(GameRules.SPAWN_PHANTOMS, before, level.getServer());
        proof.check(fired.get() == 2, "game rule changed fired=" + fired.get());
    }

    private void checkVanillaGameEvent() {
        AtomicInteger fired = new AtomicInteger();
        Vec3 where = Vec3.atCenterOf(base);
        proof.listen(VanillaGameEvent.class, event -> {
            if (event.gameEvent().is(GameEvent.BLOCK_PLACE) && event.position().equals(where)) {
                fired.incrementAndGet();
                event.setCanceled(true);
            }
        });
        level.gameEvent(GameEvent.BLOCK_PLACE, where, GameEvent.Context.of(Blocks.STONE.defaultBlockState()));
        proof.check(fired.get() == 1, "vanilla game event fired=" + fired.get());
    }

    private void checkPotentialSpawns() throws ReflectiveOperationException {
        AtomicInteger fired = new AtomicInteger();
        proof.listen(PotentialSpawnsEvent.class, event -> {
            if (event.pos().equals(base) && event.category() == MobCategory.MONSTER) {
                fired.incrementAndGet();
                event.spawns().clear();
            }
        });
        Method mobsAt = NaturalSpawner.class.getDeclaredMethod("mobsAt", ServerLevel.class, StructureManager.class,
                ChunkGenerator.class, MobCategory.class, BlockPos.class);
        mobsAt.setAccessible(true);
        WeightedList<?> spawns = (WeightedList<?>) mobsAt.invoke(null, level, level.structureManager(),
                level.getChunkSource().getGenerator(), MobCategory.MONSTER, base);
        proof.check(fired.get() == 1 && spawns.isEmpty(),
                "potential spawns fired=" + fired.get() + " empty=" + spawns.isEmpty());
    }

    private void checkChunkSave() {
        AtomicInteger fired = new AtomicInteger();
        proof.listen(ChunkDataSaveEvent.class, event -> {
            if (event.level() == level && event.chunk().getPos().equals(level.getChunkAt(base).getPos())) {
                fired.incrementAndGet();
            }
        });
        BlockPos marker = base.east(2);
        BlockState before = level.getBlockState(marker);
        level.setBlock(marker, Blocks.GLASS.defaultBlockState(), 3);
        try {
            level.getChunkSource().save(false);
        } finally {
            level.setBlock(marker, before, 3);
        }
        proof.check(fired.get() >= 1, "chunk data save fired=" + fired.get());
    }

    private void checkChunkTicket() {
        int chunkX = (base.getX() >> 4) + 64;
        int chunkZ = (base.getZ() >> 4) + 64;
        AtomicInteger fired = new AtomicInteger();
        proof.listen(ChunkTicketLevelUpdatedEvent.class, event -> {
            if (event.chunkPos().x() == chunkX && event.chunkPos().z() == chunkZ) {
                fired.incrementAndGet();
            }
        });
        level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, true);
        proof.check(fired.get() >= 1, "chunk ticket level fired=" + fired.get());
    }

    private void checkDatapackSync() {
        AtomicInteger fired = new AtomicInteger();
        proof.listen(DatapackSyncEvent.class, event -> {
            if (event.player() == null) {
                fired.incrementAndGet();
            }
        });
        level.getServer().getPlayerList().reloadResources();
        proof.check(fired.get() == 1, "datapack sync fired=" + fired.get());
    }

    private void checkLevelSound() {
        AtomicInteger fired = new AtomicInteger();
        proof.listen(net.weaver.api.game.event.world.PlayLevelSoundEvent.class, event -> {
            if (event.level() == level && event.sound().is(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BELL)) {
                fired.incrementAndGet();
                event.setCanceled(true);
            }
        });
        level.playSound(null, base, net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BELL.value(), net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
        proof.check(fired.get() == 1, "level sound fired=" + fired.get());
    }

    private void checkServerReloadListeners() throws ReflectiveOperationException {
        net.minecraft.server.packs.resources.PreparableReloadListener marker =
                (state, prepareExecutor, barrier, applyExecutor) -> java.util.concurrent.CompletableFuture.completedFuture(null);
        proof.listen(net.weaver.api.game.event.world.AddServerReloadListenersEvent.class, event -> event.addListener(marker));
        java.lang.reflect.Field field = net.minecraft.server.MinecraftServer.class.getDeclaredField("resources");
        field.setAccessible(true);
        Object resources = field.get(level.getServer());
        java.lang.reflect.Method accessor = resources.getClass().getDeclaredMethod("managers");
        accessor.setAccessible(true);
        net.minecraft.server.ReloadableServerResources managers =
                (net.minecraft.server.ReloadableServerResources) accessor.invoke(resources);
        java.util.List<?> listeners = managers.listeners();
        // Mod listeners run after vanilla's, then Weaver's modifier files, then its data registry listener.
        boolean ordered = listeners.size() >= 3 && listeners.get(listeners.size() - 3) == marker
                && listeners.get(listeners.size() - 1) == net.weaver.api.game.data.PackDataRegistries.LISTENER;
        proof.check(ordered, "server reload listener added=" + listeners.contains(marker) + " ordered=" + ordered);
    }

    private void checkCreativeTab() {
        net.minecraft.world.item.CreativeModeTab tab = net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB
                .getValue(net.minecraft.resources.Identifier.withDefaultNamespace("building_blocks"));
        AtomicInteger fired = new AtomicInteger();
        proof.listen(net.weaver.api.game.event.world.BuildCreativeTabContentsEvent.class, event -> {
            if (event.tab() == tab) {
                fired.incrementAndGet();
                event.accept(net.minecraft.world.item.Items.DIAMOND);
            }
        });
        tab.buildContents(new net.minecraft.world.item.CreativeModeTab.ItemDisplayParameters(
                level.enabledFeatures(), false, level.registryAccess()));
        boolean added = tab.getDisplayItems().stream().anyMatch(stack -> stack.is(net.minecraft.world.item.Items.DIAMOND))
                && tab.getSearchTabDisplayItems().stream().anyMatch(stack -> stack.is(net.minecraft.world.item.Items.DIAMOND));
        proof.check(fired.get() == 1 && added, "creative tab contents fired=" + fired.get() + " added=" + added);
    }
    private void checkModCreativeTab() {
        net.minecraft.world.item.CreativeModeTab tab = net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB
                .getValue(net.minecraft.resources.Identifier.fromNamespaceAndPath("weaverproof", "proof_tab"));
        net.minecraft.world.item.CreativeModeTab building = net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB
                .getValue(net.minecraft.resources.Identifier.withDefaultNamespace("building_blocks"));
        net.minecraft.world.item.CreativeModeTab search = net.minecraft.world.item.CreativeModeTabs.searchTab();
        // Vanilla validation rejects a tab that shares a slot; Weaver lets mod tabs through.
        net.minecraft.world.item.CreativeModeTabs.validate();
        boolean placed = tab != null && net.weaver.api.game.creative.CreativeTabPages.isModTab(tab)
                && net.weaver.api.game.creative.CreativeTabPages.pageOf(tab) == 1
                && tab.row() == net.minecraft.world.item.CreativeModeTab.Row.TOP && tab.column() == 0 && !tab.isAlignedRight()
                && building.row() == net.minecraft.world.item.CreativeModeTab.Row.TOP && building.column() == 0
                && net.weaver.api.game.creative.CreativeTabPages.pageCount() == 2;
        java.util.List<net.minecraft.world.item.CreativeModeTab> all = java.util.List.of(building, tab, search);
        net.weaver.api.game.creative.CreativeTabPages.setCurrentPage(1);
        java.util.List<net.minecraft.world.item.CreativeModeTab> second = net.weaver.api.game.creative.CreativeTabPages.visible(all);
        net.weaver.api.game.creative.CreativeTabPages.setCurrentPage(0);
        java.util.List<net.minecraft.world.item.CreativeModeTab> first = net.weaver.api.game.creative.CreativeTabPages.visible(all);
        boolean paged = second.equals(java.util.List.of(tab, search)) && first.equals(java.util.List.of(building, search));
        tab.buildContents(new net.minecraft.world.item.CreativeModeTab.ItemDisplayParameters(
                level.enabledFeatures(), false, level.registryAccess()));
        boolean filled = tab.getDisplayItems().stream().anyMatch(stack -> stack.getItem() == tab.getIconItem().getItem());
        proof.check(placed && paged && filled, "mod creative tab placed=" + placed + " paged=" + paged + " filled=" + filled);
    }
}
