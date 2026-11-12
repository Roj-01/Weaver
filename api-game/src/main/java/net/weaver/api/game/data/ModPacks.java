package net.weaver.api.game.data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.zip.ZipFile;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.flag.FeatureFlagSet;

/**
 * Turns installed Weaver mods into built-in packs, the way vanilla offers its
 * own: a mod jar with a {@code data/} folder becomes the data pack
 * {@code mod/<modid>} on every server (dedicated and single player), and one
 * with an {@code assets/} folder becomes the resource pack {@code mod/<modid>}
 * on the client. So a mod can ship recipes, loot tables, tags, Weaver data
 * registry files, textures, models, and translations inside its jar.
 *
 * <p>Mod packs are required (always enabled, not listed as optional in the
 * pack screens) and sit just above vanilla's pack: a world data pack or a
 * user resource pack placed above them can still override a mod's files.
 * Mods keep loader order among themselves. A jar needs no {@code pack.mcmeta};
 * mod packs are always reported as compatible.</p>
 *
 * <p>The loader supplies the installed mods once at startup
 * ({@link #setMods(List)}); {@code BuiltInPackSourceMixin} adds the packs when
 * a pack repository discovers its built-in packs.</p>
 */
public final class ModPacks {
    /** One installed mod. {@code location} is its jar file or, for a development mod, a directory. */
    public record ModPack(String modId, String name, Path location, boolean hasData, boolean hasAssets) {
        public ModPack {
            Objects.requireNonNull(modId, "modId");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(location, "location");
        }

        /** The pack id, {@code mod/<modid>}. */
        public String packId() {
            return "mod/" + modId;
        }
    }

    private static volatile List<ModPack> mods = List.of();

    /** An optional pack from a folder inside a mod (see {@link #addExtraPack}). */
    private record ExtraPack(PackType type, Path location, String folder, String id, Component title, boolean required) {
    }

    private static final List<ExtraPack> EXTRA = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static final java.util.Map<Path, java.nio.file.FileSystem> JAR_SYSTEMS = new java.util.concurrent.ConcurrentHashMap<>();

    private ModPacks() {
    }

    /**
     * Sets the installed mods, in loader order. Each entry is
     * {@code [modId, displayName, path]}; the jar (or directory) is checked for
     * {@code data/} and {@code assets/} once, here. Called by the loader
     * through reflection, so it takes plain lists.
     *
     * @param entries the mods; entries that cannot be read are skipped
     */
    @net.weaver.api.annotation.Internal
    public static synchronized void setMods(List<List<String>> entries) {
        List<ModPack> packs = new ArrayList<>();
        for (List<String> entry : entries) {
            if (entry.size() < 3) {
                continue;
            }
            Path location = Path.of(entry.get(2));
            boolean[] contents = contents(location);
            if (contents[0] || contents[1]) {
                packs.add(new ModPack(entry.get(0), entry.get(1), location, contents[0], contents[1]));
            }
        }
        mods = List.copyOf(packs);
    }

    /** The mods that have a data or asset folder. */
    public static List<ModPack> mods() {
        return mods;
    }

    /** Emits a pack for every mod that has files of {@code type}. */
    public static void addPacks(PackType type, Consumer<Pack> output) {
        for (ModPack mod : mods) {
            boolean relevant = type == PackType.SERVER_DATA ? mod.hasData() : mod.hasAssets();
            if (relevant) {
                output.accept(pack(mod));
            }
        }
        for (ExtraPack extra : EXTRA) {
            if (extra.type() == type) {
                Pack pack = extraPack(extra);
                if (pack != null) {
                    output.accept(pack);
                }
            }
        }
        net.weaver.api.game.gametest.GameTests.pack(type).ifPresent(output);
        ModPack weaver = weaverPack();
        if (weaver != null && (type == PackType.SERVER_DATA ? weaver.hasData() : weaver.hasAssets())) {
            output.accept(pack(weaver));
        }
    }

    /**
     * Weaver's own pack, {@code mod/weaver}: as data, the conventional {@code c:}
     * tags (ingots, ores, storage blocks, gems, dyes, tools, ...) with the vanilla
     * entries, so mods can share materials by tag (see {@code ConventionalTags});
     * as resources, Weaver's shaders (instanced meshes). It is this jar; null when
     * the jar cannot be located.
     */
    private static ModPack weaverPack() {
        try {
            java.security.CodeSource source = ModPacks.class.getProtectionDomain().getCodeSource();
            if (source == null) {
                return null;
            }
            Path location = Path.of(source.getLocation().toURI());
            boolean[] contents = contents(location);
            return contents[0] || contents[1] ? new ModPack("weaver", "Weaver", location, contents[0], contents[1]) : null;
        } catch (java.net.URISyntaxException | RuntimeException unavailable) {
            System.out.println("[weaver] CONVENTIONS PACK UNAVAILABLE reason=" + unavailable);
            return null;
        }
    }

    /**
     * Moves the mod packs in {@code selected} to just above the {@code vanilla}
     * pack (or to the bottom when there is none), in loader order, keeping
     * every other pack's relative order.
     *
     * @return the reordered list, or {@code null} when nothing moves
     */
    public static List<Pack> placeAboveVanilla(List<Pack> selected) {
        List<ModPack> installed = mods;
        if (installed.isEmpty() || selected == null || selected.isEmpty()) {
            return null;
        }
        java.util.Map<String, Integer> loaderOrder = new java.util.HashMap<>();
        for (int i = 0; i < installed.size(); i++) {
            loaderOrder.put(installed.get(i).packId(), i);
        }
        List<Pack> modPacks = new ArrayList<>();
        List<Pack> others = new ArrayList<>();
        for (Pack pack : selected) {
            (loaderOrder.containsKey(pack.getId()) ? modPacks : others).add(pack);
        }
        if (modPacks.isEmpty()) {
            return null;
        }
        modPacks.sort(java.util.Comparator.comparingInt(pack -> loaderOrder.get(pack.getId())));
        int vanilla = -1;
        for (int i = 0; i < others.size(); i++) {
            if ("vanilla".equals(others.get(i).getId())) {
                vanilla = i;
                break;
            }
        }
        List<Pack> ordered = new ArrayList<>(others);
        ordered.addAll(vanilla + 1, modPacks);
        return ordered.equals(selected) ? null : List.copyOf(ordered);
    }

    /**
     * The known-pack identity of a mod pack: namespace {@code weaver}, the pack id,
     * and the mod's version. Vanilla treats world data (biomes, structures,
     * dimensions) from packs it does not know as experimental: every world using a
     * mod's worldgen would open behind the "experimental settings" warning. A known
     * pack's entries are stable, as vanilla's own. A client with the same mod
     * version also loads that data from its own copy instead of receiving it.
     */
    static net.minecraft.server.packs.repository.KnownPack knownPack(ModPack mod) {
        String version = "weaver".equals(mod.modId()) ? net.weaver.api.WeaverApi.VERSION
                : net.weaver.api.WeaverMods.find(mod.modId()).map(net.weaver.api.WeaverMods.Mod::version).orElse("0");
        return new net.minecraft.server.packs.repository.KnownPack("weaver", mod.packId(), version);
    }

    private static Pack pack(ModPack mod) {
        PackLocationInfo location = new PackLocationInfo(mod.packId(), Component.literal(mod.name()),
                PackSource.BUILT_IN, Optional.of(knownPack(mod)));
        Pack.ResourcesSupplier resources = Files.isDirectory(mod.location())
                ? new PathPackResources.PathResourcesSupplier(mod.location())
                : new FilePackResources.FileResourcesSupplier(mod.location());
        Pack.Metadata metadata = new Pack.Metadata(Component.literal(mod.name() + " (mod)"),
                PackCompatibility.COMPATIBLE, FeatureFlagSet.of(), List.of());
        return new Pack(location, resources, metadata, new PackSelectionConfig(true, Pack.Position.BOTTOM, false));
    }

    /**
     * Offers the folder {@code folder} of the mod at {@code location} (its jar, or
     * its directory in development) as an optional pack {@code id}: a resource pack
     * for {@code CLIENT_RESOURCES}, a data pack for {@code SERVER_DATA}. Players
     * turn it on in the pack screens; it is not on by default. Call it before the
     * pack screens or the world load list the packs (in {@code onInitialize}).
     */
    public static void addExtraPack(PackType type, Path location, String folder, String id, Component title) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(folder, "folder");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        EXTRA.add(new ExtraPack(type, location, folder, id, title, false));
    }

    /**
     * A pack that is always enabled and sits on top (the developer hot reload
     * uses it for a project's resource folder, so its files override the jar's).
     */
    @net.weaver.api.annotation.Internal
    public static void addRequiredPack(PackType type, Path folder, String id, Component title) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(folder, "folder");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        EXTRA.add(new ExtraPack(type, folder, "", id, title, true));
    }

    private static Pack extraPack(ExtraPack extra) {
        try {
            Path root;
            if (Files.isDirectory(extra.location())) {
                root = extra.location().resolve(extra.folder());
            } else {
                java.nio.file.FileSystem jar = JAR_SYSTEMS.computeIfAbsent(extra.location(), path -> {
                    try {
                        return java.nio.file.FileSystems.newFileSystem(path);
                    } catch (IOException failure) {
                        throw new java.io.UncheckedIOException(failure);
                    }
                });
                root = jar.getPath(extra.folder());
            }
            if (!Files.isDirectory(root)) {
                System.out.println("[weaver] EXTRA PACK MISSING id=" + extra.id() + " folder=" + extra.folder()
                        + " in=" + extra.location());
                return null;
            }
            PackLocationInfo location = new PackLocationInfo(extra.id(), extra.title(), PackSource.BUILT_IN, Optional.empty());
            Pack.Metadata metadata = new Pack.Metadata(extra.title(), PackCompatibility.COMPATIBLE, FeatureFlagSet.of(), List.of());
            return new Pack(location, new PathPackResources.PathResourcesSupplier(root), metadata,
                    new PackSelectionConfig(extra.required(), Pack.Position.TOP, false));
        } catch (RuntimeException failure) {
            System.out.println("[weaver] EXTRA PACK FAILED id=" + extra.id() + " reason=" + failure);
            return null;
        }
    }

    private static boolean[] contents(Path location) {
        if (Files.isDirectory(location)) {
            return new boolean[] {Files.isDirectory(location.resolve("data")), Files.isDirectory(location.resolve("assets"))};
        }
        boolean data = false;
        boolean assets = false;
        try (ZipFile zip = new ZipFile(location.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements() && !(data && assets)) {
                String name = entries.nextElement().getName();
                data |= name.startsWith("data/");
                assets |= name.startsWith("assets/");
            }
        } catch (IOException | RuntimeException unreadable) {
            return new boolean[] {false, false};
        }
        return new boolean[] {data, assets};
    }
}
