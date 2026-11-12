package net.weaver.api.game.data;

import com.google.gson.JsonElement;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

/** The client side of {@link DataMaps} sync; loaded only when a sync arrives. */
final class DataMapsClient {
    private DataMapsClient() {
    }

    static void apply(DataMaps.Sync sync) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        DynamicOps<JsonElement> ops = connection != null
                ? connection.registryAccess().createSerializationContext(JsonOps.INSTANCE) : JsonOps.INSTANCE;
        DataMaps.applyOnClient(sync, ops);
    }
}
