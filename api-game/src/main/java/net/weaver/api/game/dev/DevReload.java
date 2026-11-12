package net.weaver.api.game.dev;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.PackType;
import net.weaver.api.annotation.Internal;
import net.weaver.api.event.EventBus;
import net.weaver.api.game.data.ModPacks;
import net.weaver.api.game.event.ServerStartingEvent;
import net.weaver.api.game.event.ServerStoppedEvent;

/**
 * The game side of the developer hot reload (core's {@code HotSwapService}): the
 * project's resource folders as packs over the mod's own, resource reloads when
 * they change, and restarts into the same world or server for changes the JVM
 * cannot swap. Called by the loader through reflection; only active in dev runs.
 */
@Internal
public final class DevReload
{
    /** The file a restarting game leaves for its launcher: where to come back to. */
    public static final String RESTART_FILE = "dev-restart.json";

    private static volatile MinecraftServer server;
    private static volatile Path dataDir;

    private DevReload()
    {
    }

    /** Registers the resource folders as always-on packs and starts tracking the server. */
    public static void install(Path data, List<String> resourceRoots)
    {
        dataDir = data;
        EventBus.GAME.addListener(ServerStartingEvent.class, event -> server = event.server());
        EventBus.GAME.addListener(ServerStoppedEvent.class, event -> server = null);
        int n = 0;
        for (String root : resourceRoots)
        {
            Path dir = Path.of(root);
            n++;
            if (Files.isDirectory(dir.resolve("assets")))
            {
                ModPacks.addRequiredPack(PackType.CLIENT_RESOURCES, dir, "weaver_dev_assets_" + n,
                        Component.literal("Dev resources " + n));
            }
            // data/ may appear later; the data pack is cheap when empty
            ModPacks.addRequiredPack(PackType.SERVER_DATA, dir, "weaver_dev_data_" + n, Component.literal("Dev data " + n));
        }
        System.out.println("[weaver] HOTSWAP RESOURCES packs=" + n + " (the project's resource folders override the jars)");
    }

    /** Reloads client assets and/or server data. */
    public static void reloadResources(boolean assets, boolean data)
    {
        if (data)
        {
            MinecraftServer running = server;
            if (running != null)
            {
                running.execute(() -> running.reloadResources(running.getPackRepository().getSelectedIds())
                        .whenComplete((ok, failure) -> System.out.println("[weaver] HOTSWAP DATA RELOADED ok=" + (failure == null)
                                + (failure == null ? "" : " reason=" + failure))));
            }
        }
        if (assets && client())
        {
            DevReloadClient.reloadAssets();
        }
    }

    /** Saves and stops the game, leaving the launcher a note to start it again in the same place. */
    public static void restart(String why)
    {
        if (client())
        {
            DevReloadClient.restart(why, restartFile());
            return;
        }
        MinecraftServer running = server;
        writeRestart("{\"side\":\"server\"}");
        System.out.println("[weaver] HOTSWAP RESTART server why=" + why);
        if (running != null)
        {
            running.execute(() -> running.halt(false));
        }
    }

    static Path restartFile()
    {
        Path dir = dataDir != null ? dataDir : Path.of(".");
        return dir.resolve(RESTART_FILE);
    }

    static void writeRestart(String json)
    {
        try
        {
            Files.createDirectories(restartFile().getParent());
            Files.writeString(restartFile(), json);
        }
        catch (Exception failure)
        {
            System.out.println("[weaver] HOTSWAP RESTART NOTE FAILED reason=" + failure);
        }
    }

    private static boolean client()
    {
        try
        {
            Class.forName("net.minecraft.client.Minecraft", false, DevReload.class.getClassLoader());
            return true;
        }
        catch (ClassNotFoundException dedicated)
        {
            return false;
        }
    }

    /** Quotes a string for the restart note. */
    static String quote(String value)
    {
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray())
        {
            if (c == '"' || c == '\\')
            {
                out.append('\\');
            }
            if (c >= 0x20)
            {
                out.append(c);
            }
        }
        return out.append('"').toString();
    }
}
