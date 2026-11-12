package net.weaver.api.game.event.support;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.weaver.api.WeaverMods;
import net.weaver.api.game.event.world.ModMismatchEvent;

/**
 * Records the mod list in the overworld's data and fires
 * {@link ModMismatchEvent} when a world loads with a different one.
 */
public final class ModMismatchCheck {
    /** The recorded mod list: mod id to version. */
    public static final class ModListData extends SavedData {
        static final Codec<ModListData> CODEC = Codec.unboundedMap(Codec.STRING, Codec.STRING)
                .xmap(ModListData::new, ModListData::mods);
        static final SavedDataType<ModListData> TYPE = new SavedDataType<>(
                Identifier.fromNamespaceAndPath("weaver", "mod_list"), ModListData::new, CODEC,
                DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

        private Map<String, String> mods;
        private boolean recorded;

        public ModListData() {
            this.mods = Map.of();
            this.recorded = false;
        }

        private ModListData(Map<String, String> mods) {
            this.mods = Map.copyOf(mods);
            this.recorded = true;
        }

        Map<String, String> mods() {
            return mods;
        }
    }

    private ModMismatchCheck() {
    }

    /** Compares and records the mod list; called when a server level is created. */
    public static void check(ServerLevel level) {
        if (level.dimension() != Level.OVERWORLD) {
            return;
        }
        ModListData data = level.getDataStorage().computeIfAbsent(ModListData.TYPE);
        Map<String, String> current = new TreeMap<>();
        for (WeaverMods.Mod mod : WeaverMods.loaded()) {
            current.put(mod.id(), mod.version());
        }
        if (data.recorded) {
            List<ModMismatchEvent.Mismatch> mismatches = compare(data.mods, current);
            if (!mismatches.isEmpty()) {
                System.out.println("[weaver] MOD MISMATCH world=" + level.getServer().getWorldData().getLevelName()
                        + " mods=" + mismatches);
                GameEvents.post(new ModMismatchEvent(level.getServer(), mismatches), level);
            }
        }
        if (!data.recorded || !data.mods.equals(current)) {
            data.mods = Map.copyOf(current);
            data.recorded = true;
            data.setDirty();
        }
    }

    /** The differences between a recorded mod list and the current one, by mod id. */
    public static List<ModMismatchEvent.Mismatch> compare(Map<String, String> before, Map<String, String> now) {
        List<ModMismatchEvent.Mismatch> mismatches = new ArrayList<>();
        TreeSet<String> ids = new TreeSet<>(before.keySet());
        ids.addAll(now.keySet());
        for (String id : ids) {
            String previous = before.get(id);
            String current = now.get(id);
            if (previous == null || !previous.equals(current)) {
                mismatches.add(new ModMismatchEvent.Mismatch(id, previous, current));
            }
        }
        return List.copyOf(mismatches);
    }

    /** The mod list recorded in this server's overworld, mod id to version. */
    public static Map<String, String> recorded(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(ModListData.TYPE).mods();
    }
}
