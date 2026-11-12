package net.weaver.nf;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.RepositorySource;
import net.weaver.api.game.data.ModPacks;

/**
 * Optional packs from folders inside a mod, as NeoForge's
 * {@code AddPackFindersEvent}: {@code addClientPack(MyMod.class, "resourcepacks/dark", "examplemod:dark", title)}.
 * The anchor class locates the mod's jar. Backed by Weaver's {@code ModPacks}.
 */
public final class ExtraPacks {
    private ExtraPacks() {
    }

    /** Offers {@code folder} of the anchor's mod as an optional resource pack. */
    public static synchronized void addClientPack(Class<?> anchor, String folder, String id, Component title) {
        ModPacks.addExtraPack(PackType.CLIENT_RESOURCES, location(anchor), folder, id, title);
    }

    /** Offers {@code folder} of the anchor's mod as an optional data pack. */
    public static synchronized void addDataPack(Class<?> anchor, String folder, String id, Component title) {
        ModPacks.addExtraPack(PackType.SERVER_DATA, location(anchor), folder, id, title);
    }

    /** Kept for code written against the copies; Weaver adds the packs to the built-in source itself. */
    public static RepositorySource sourceFor(Set<RepositorySource> sources) {
        return output -> {
        };
    }

    private static Path location(Class<?> anchor) {
        CodeSource source = anchor.getProtectionDomain().getCodeSource();
        if (source == null) {
            throw new IllegalArgumentException("cannot locate the jar of " + anchor.getName());
        }
        try {
            return Path.of(source.getLocation().toURI());
        } catch (URISyntaxException failure) {
            throw new IllegalArgumentException("cannot locate the jar of " + anchor.getName(), failure);
        }
    }
}
