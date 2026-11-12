package net.weaver.minimap.map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Derives stable directory names for the current world and dimension.
 *
 * <p>Singleplayer worlds use the level name, multiplayer uses the server
 * address, and anything else falls back to {@code unknown}. Names are reduced
 * to lowercase letters, digits, dot, underscore, and dash so they are safe as
 * directory components.</p>
 */
public final class MinimapWorlds
{
    private static final Pattern UNSAFE = Pattern.compile("[^a-z0-9._-]+");

    private MinimapWorlds()
    {
    }

    /**
     * Returns a stable identifier for the current world.
     *
     * @param minecraft the client instance
     * @return a safe directory name, never {@code null} or blank
     */
    public static String worldKey(Minecraft minecraft)
    {
        if (minecraft == null)
        {
            return "unknown";
        }
        if (minecraft.hasSingleplayerServer())
        {
            IntegratedServer server = minecraft.getSingleplayerServer();
            if (server != null)
            {
                String name = server.getWorldData().getLevelName();
                if (name != null && !name.isBlank())
                {
                    return safe(name);
                }
            }
        }
        ServerData server = minecraft.getCurrentServer();
        if (server != null)
        {
            if (server.ip != null && !server.ip.isBlank())
            {
                return safe(server.ip);
            }
            if (server.name != null && !server.name.isBlank())
            {
                return safe(server.name);
            }
        }
        return "unknown";
    }

    /**
     * Returns the dimension identifier used by game resources.
     *
     * @param level the client level
     * @return the dimension id, never {@code null}
     */
    public static String dimension(ClientLevel level)
    {
        return level.dimension().identifier().toString();
    }

    /**
     * Converts arbitrary text into a filesystem-safe component.
     *
     * @param value the text, possibly {@code null}
     * @return a safe component, never {@code null} or blank
     */
    public static String safe(String value)
    {
        if (value == null)
        {
            return "world";
        }
        String normalized = UNSAFE.matcher(value.trim().toLowerCase(Locale.ROOT))
                .replaceAll("_");
        while (normalized.startsWith("_"))
        {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("_"))
        {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.isEmpty())
        {
            return "world";
        }
        return normalized.length() > 80 ? normalized.substring(0, 80) : normalized;
    }
}
