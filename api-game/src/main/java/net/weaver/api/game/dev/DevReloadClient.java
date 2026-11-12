package net.weaver.api.game.dev;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelStorageSource;

/** The client half of {@link DevReload}; loaded only when the client classes exist. */
final class DevReloadClient
{
    private DevReloadClient()
    {
    }

    static void reloadAssets()
    {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null)
        {
            return;
        }
        minecraft.execute(() -> minecraft.reloadResourcePacks()
                .whenComplete((ok, failure) -> System.out.println("[weaver] HOTSWAP ASSETS RELOADED ok=" + (failure == null))));
    }

    static void restart(String why, Path note)
    {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null)
        {
            return;
        }
        minecraft.execute(() ->
        {
            String target = target(minecraft);
            DevReload.writeRestart(target);
            System.out.println("[weaver] HOTSWAP RESTART client target=" + target + " why=" + why);
            try
            {
                net.weaver.api.game.client.command.ClientCommands.feedback(Component.literal(
                        "Weaver: restarting to apply your changes (" + why + ") and coming back here…"));
            }
            catch (RuntimeException noChat)
            {
                // the title screen has no chat; the restart still happens
            }
            // a moment for the player to read it, then a normal quit that saves the world
            CompletableFuture.delayedExecutor(1500, TimeUnit.MILLISECONDS).execute(() -> minecraft.execute(minecraft::stop));
        });
    }

    /** Where the player is: the singleplayer world folder or the server address. */
    private static String target(Minecraft minecraft)
    {
        IntegratedServer local = minecraft.getSingleplayerServer();
        if (local != null)
        {
            try
            {
                Field field = MinecraftServer.class.getDeclaredField("storageSource");
                field.setAccessible(true);
                LevelStorageSource.LevelStorageAccess access = (LevelStorageSource.LevelStorageAccess) field.get(local);
                return "{\"side\":\"client\",\"quickPlayWorld\":" + DevReload.quote(access.getLevelId()) + "}";
            }
            catch (ReflectiveOperationException | RuntimeException failure)
            {
                System.out.println("[weaver] HOTSWAP RESTART world unknown reason=" + failure);
            }
        }
        ServerData remote = minecraft.getCurrentServer();
        if (remote != null && remote.ip != null && !remote.ip.isBlank())
        {
            return "{\"side\":\"client\",\"quickPlayServer\":" + DevReload.quote(remote.ip) + "}";
        }
        return "{\"side\":\"client\"}";
    }
}
