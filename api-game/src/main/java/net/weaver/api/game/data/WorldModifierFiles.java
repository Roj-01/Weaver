package net.weaver.api.game.data;

import com.google.gson.JsonElement;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.weaver.api.game.loot.LootModifiers;
import net.weaver.api.game.worldgen.BiomeModifiers;

/**
 * Reads {@code weaver/loot_modifiers}, {@code weaver/biome_modifiers},
 * {@code weaver/structure_modifiers}, and the
 * {@link DataMaps} files from the server's data packs on every data pack load
 * and hands them to {@link LootModifiers}, {@link BiomeModifiers}, and
 * {@link DataMaps}.
 *
 * <p>The files are read by a reload listener but parsed in {@link #finish},
 * once vanilla has bound the reloaded tags and item components: before that,
 * a {@code #tag} or an item stack in a file cannot be resolved. On the first
 * load that is still before any level exists.</p>
 */
public final class WorldModifierFiles {
    static final String LOOT = "weaver/loot_modifiers";
    static final String BIOME = "weaver/biome_modifiers";
    static final String STRUCTURE = "weaver/structure_modifiers";

    private WorldModifierFiles() {
    }

    /** The reload listener for one data pack load. */
    public static PreparableReloadListener listener(ReloadableServerResources resources) {
        return new Listener(resources);
    }

    private static final java.util.Map<ReloadableServerResources, Files> PENDING = new java.util.WeakHashMap<>();

    private record Files(Map<Identifier, JsonElement> loot, Map<Identifier, JsonElement> biome,
                         Map<Identifier, JsonElement> structure, Map<Identifier, java.util.List<JsonElement>> dataMaps) {
    }

    private static final class Listener extends SimplePreparableReloadListener<Files> {
        private final ReloadableServerResources resources;

        Listener(ReloadableServerResources resources) {
            this.resources = resources;
        }

        @Override
        protected Files prepare(ResourceManager manager, ProfilerFiller profiler) {
            return new Files(read(manager, LOOT), read(manager, BIOME), read(manager, STRUCTURE), DataMaps.read(manager));
        }

        @Override
        protected void apply(Files files, ResourceManager manager, ProfilerFiller profiler) {
            synchronized (PENDING) {
                PENDING.put(resources, files);
            }
        }
    }

    /**
     * Parses and installs the files read for {@code resources}. Called when
     * vanilla has bound the reloaded tags; does nothing when this load read
     * no files (another loader drove it).
     */
    public static void finish(ReloadableServerResources resources) {
        Files files;
        synchronized (PENDING) {
            files = PENDING.remove(resources);
        }
        if (files == null) {
            return;
        }
        var lookup = resources.fullRegistries().lookup();
        LootModifiers.install(LootModifiers.parse(files.loot(), lookup, System.out::println));
        BiomeModifiers.load(files.biome(), lookup, System.out::println);
        net.weaver.api.game.worldgen.StructureModifiers.load(files.structure(), lookup, System.out::println);
        DataMaps.load(files.dataMaps(), lookup);
        System.out.println("[weaver] WORLD MODIFIERS loot=" + LootModifiers.loaded().size()
                + " biome=" + BiomeModifiers.loaded().ids().size()
                + " structure=" + net.weaver.api.game.worldgen.StructureModifiers.loaded().size());
    }

    /** JSON files under {@code directory}, keyed {@code namespace:path} without the directory and extension. */
    static Map<Identifier, JsonElement> read(ResourceManager manager, String directory) {
        Map<Identifier, JsonElement> files = new HashMap<>();
        Map<Identifier, Resource> found = manager.listResources(directory, id -> id.getPath().endsWith(".json"));
        for (Map.Entry<Identifier, Resource> entry : found.entrySet()) {
            Identifier file = entry.getKey();
            String path = file.getPath();
            String name = path.substring(directory.length() + 1, path.length() - ".json".length());
            Identifier id = Identifier.fromNamespaceAndPath(file.getNamespace(), name);
            try {
                files.put(id, ModifierFiles.readJson(entry.getValue()));
            } catch (IOException | RuntimeException failure) {
                System.out.println("[weaver] MODIFIER FILE UNREADABLE id=" + id + " reason=" + failure.getMessage());
            }
        }
        return files;
    }
}
