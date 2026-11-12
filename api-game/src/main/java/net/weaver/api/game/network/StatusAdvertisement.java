package net.weaver.api.game.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.network.protocol.status.ServerStatus;
import net.weaver.api.WeaverApi;
import net.weaver.api.WeaverMods;
import net.weaver.api.annotation.Internal;
import net.weaver.api.network.ModFingerprints;

/**
 * The {@code weaver} field a Weaver server adds to its status reply (the server
 * list ping), so a launcher knows, before joining, which mods the server needs:
 *
 * <pre>{@code
 * "weaver": {
 *   "loader": "2.3.2", "api": "2.1.0",
 *   "mods": [ { "id": "aoso_mobs", "version": "1.11.0", "sha256": "…" }, … ],
 *   "truncated": false
 * }
 * }</pre>
 *
 * <p>Only mods that must be on both sides are listed (client-only and server-only
 * mods are not). Vanilla clients ignore the field. The list is cut to fit the
 * status packet's size limit, and {@code truncated} says so.</p>
 */
@Internal
public final class StatusAdvertisement
{
    /** Leaves room under the 32767-character status JSON limit for the vanilla fields and the favicon-free rest. */
    static final int MAX_CHARS = 20_000;

    private StatusAdvertisement()
    {
    }

    /** The status codec with the {@code weaver} field added when encoding. */
    public static Codec<ServerStatus> wrap(Codec<ServerStatus> codec)
    {
        return new Codec<>()
        {
            @Override
            public <T> DataResult<Pair<ServerStatus, T>> decode(DynamicOps<T> ops, T input)
            {
                return codec.decode(ops, input);
            }

            @Override
            public <T> DataResult<T> encode(ServerStatus input, DynamicOps<T> ops, T prefix)
            {
                DataResult<T> encoded = codec.encode(input, ops, prefix);
                JsonObject weaver;
                try
                {
                    weaver = json();
                }
                catch (RuntimeException failure)
                {
                    return encoded;
                }
                T value = JsonOps.INSTANCE.convertTo(ops, weaver);
                return encoded.flatMap(map -> ops.mergeToMap(map, ops.createString("weaver"), value));
            }

            @Override
            public String toString()
            {
                return "Weaver" + codec;
            }
        };
    }

    /** The advertisement for this server, from the loaded mods. */
    public static JsonObject json()
    {
        JsonObject weaver = new JsonObject();
        weaver.addProperty("loader", ModFingerprints.loaderVersion());
        weaver.addProperty("api", WeaverApi.VERSION);
        JsonArray mods = new JsonArray();
        int chars = 64;
        boolean truncated = false;
        for (WeaverMods.Mod mod : WeaverMods.loaded())
        {
            if (!"both".equals(mod.side()))
            {
                continue;
            }
            JsonObject entry = new JsonObject();
            entry.addProperty("id", mod.id());
            entry.addProperty("version", mod.version());
            String sha = ModFingerprints.sha256(mod.id());
            if (sha != null)
            {
                entry.addProperty("sha256", sha);
            }
            int size = entry.toString().length() + 1;
            if (chars + size > MAX_CHARS)
            {
                truncated = true;
                break;
            }
            chars += size;
            mods.add(entry);
        }
        weaver.add("mods", mods);
        weaver.addProperty("truncated", truncated);
        return weaver;
    }

    /** Reads a status reply's {@code weaver} field, or null for a server without Weaver. */
    public static JsonObject read(JsonElement status)
    {
        if (status != null && status.isJsonObject() && status.getAsJsonObject().get("weaver") instanceof JsonObject weaver)
        {
            return weaver;
        }
        return null;
    }
}
