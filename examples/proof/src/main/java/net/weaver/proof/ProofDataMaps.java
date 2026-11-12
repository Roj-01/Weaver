package net.weaver.proof;

import com.mojang.serialization.Codec;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.weaver.api.game.data.DataMapType;
import net.weaver.api.game.data.DataMaps;
import net.weaver.api.network.PayloadBuffer;

/**
 * Data maps from two packs: the mod jar gives base values, the world data
 * pack (higher) overrides and removes some.
 */
final class ProofDataMaps {
    static final DataMapType<Item, Integer> SELL_VALUE = DataMapType.create(
            Identifier.fromNamespaceAndPath("weaverproof", "sell_value"), Registries.ITEM, Codec.INT).synced();
    static final DataMapType<Biome, Integer> BIOME_DANGER = DataMapType.create(
            Identifier.fromNamespaceAndPath("weaverproof", "biome_danger"), Registries.BIOME, Codec.INT);

    private ProofDataMaps() {
    }

    static void register() {
        DataMaps.register(SELL_VALUE);
        DataMaps.register(BIOME_DANGER);
    }

    @SuppressWarnings("deprecation")
    static void check(GameEventsProof proof, ServerLevel level, BlockPos base) {
        Integer oak = SELL_VALUE.get(Items.OAK_LOG.builtInRegistryHolder());
        Integer birch = SELL_VALUE.get(Items.BIRCH_LOG.builtInRegistryHolder());
        Integer diamond = SELL_VALUE.get(Items.DIAMOND.builtInRegistryHolder());
        Integer emerald = SELL_VALUE.get(Items.EMERALD.builtInRegistryHolder());
        Integer stick = SELL_VALUE.get(Items.STICK.builtInRegistryHolder());
        Integer apple = SELL_VALUE.get(Items.APPLE.builtInRegistryHolder());
        boolean merged = oak != null && oak == 4 && birch == null && diamond != null && diamond == 250
                && emerald != null && emerald == 50 && stick == null && apple == null;
        Integer danger = BIOME_DANGER.get(level.getBiome(base));
        boolean biome = danger != null && danger == 2;

        DataMaps.Sync sync = DataMaps.lastSync();
        PayloadBuffer out = PayloadBuffer.writer();
        DataMaps.Sync.CODEC.encode(sync, out);
        DataMaps.Sync decoded = DataMaps.Sync.CODEC.decode(PayloadBuffer.reader(out.toByteArray()));
        Map<String, String> maps = decoded.maps();
        boolean synced = decoded.equals(sync) && maps.containsKey(SELL_VALUE.id().toString())
                && !maps.containsKey(BIOME_DANGER.id().toString())
                && maps.get(SELL_VALUE.id().toString()).contains("\"minecraft:diamond\":250");
        proof.check(merged && biome && synced, "data maps oak=" + oak + " birch=" + birch + " diamond=" + diamond
                + " emerald=" + emerald + " stick=" + stick + " apple=" + apple + " danger=" + danger + " synced=" + synced);
    }
}
