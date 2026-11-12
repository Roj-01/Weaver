package net.weaver.api.network;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Shared argument validation for the networking value objects.
 */
final class NetworkValidation
{
    private static final Pattern MOD_ID = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern PATH = Pattern.compile("[a-z0-9_./-]+");

    private NetworkValidation()
    {
    }

    static String requireModId(String modId, String name)
    {
        Objects.requireNonNull(modId, name);
        if (modId.length() > NetworkProtocol.MAX_MOD_ID_CHARS)
        {
            throw new IllegalArgumentException(name + " must be at most "
                    + NetworkProtocol.MAX_MOD_ID_CHARS + " chars but was " + modId.length());
        }
        if (!MOD_ID.matcher(modId).matches())
        {
            throw new IllegalArgumentException(name + " must match [a-z0-9_.-]+ but was '"
                    + modId + "'");
        }
        return modId;
    }

    static String requirePath(String path)
    {
        Objects.requireNonNull(path, "path");
        if (path.length() > NetworkProtocol.MAX_CHANNEL_CHARS)
        {
            throw new IllegalArgumentException("path must be at most "
                    + NetworkProtocol.MAX_CHANNEL_CHARS + " chars but was " + path.length());
        }
        if (path.startsWith("/") || path.endsWith("/"))
        {
            throw new IllegalArgumentException("path must not start or end with '/' but was '"
                    + path + "'");
        }
        if (!PATH.matcher(path).matches())
        {
            throw new IllegalArgumentException("path must match [a-z0-9_./-]+ but was '"
                    + path + "'");
        }
        return path;
    }

    static String requireNonBlank(String value, String name)
    {
        Objects.requireNonNull(value, name);
        if (value.isBlank())
        {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    static String requireMaxChars(String value, String name, int maxChars)
    {
        Objects.requireNonNull(value, name);
        if (value.length() > maxChars)
        {
            throw new IllegalArgumentException(name + " must be at most " + maxChars
                    + " chars but was " + value.length());
        }
        return value;
    }

    static String requireNoIsoControl(String value, String name)
    {
        Objects.requireNonNull(value, name);
        for (int index = 0; index < value.length(); index++)
        {
            if (isIsoControl(value.charAt(index)))
            {
                throw new IllegalArgumentException(name
                        + " must not contain ISO control characters but contained U+"
                        + String.format("%04X", (int) value.charAt(index)));
            }
        }
        return value;
    }

    static boolean isIsoControl(char value)
    {
        return value <= 0x1F || (value >= 0x7F && value <= 0x9F);
    }

    static List<NetworkModInfo> copyMods(List<NetworkModInfo> mods)
    {
        Objects.requireNonNull(mods, "mods");
        List<NetworkModInfo> copy = List.copyOf(mods);
        if (copy.size() > NetworkProtocol.MAX_MODS)
        {
            throw new IllegalArgumentException("mods must contain at most "
                    + NetworkProtocol.MAX_MODS + " entries but contained " + copy.size());
        }
        Set<String> ids = new HashSet<>();
        for (NetworkModInfo mod : copy)
        {
            if (!ids.add(mod.id()))
            {
                throw new IllegalArgumentException("mods must not contain duplicate id '"
                        + mod.id() + "'");
            }
        }
        return copy;
    }

    static List<String> copyChannels(List<String> channels)
    {
        Objects.requireNonNull(channels, "channels");
        List<String> copy = List.copyOf(channels);
        if (copy.size() > NetworkProtocol.MAX_CHANNELS)
        {
            throw new IllegalArgumentException("channels must contain at most "
                    + NetworkProtocol.MAX_CHANNELS + " entries but contained " + copy.size());
        }
        Set<String> unique = new HashSet<>();
        List<String> validated = new ArrayList<>(copy.size());
        for (String channel : copy)
        {
            Objects.requireNonNull(channel, "channel");
            if (channel.isBlank())
            {
                throw new IllegalArgumentException("channel ids must not be blank");
            }
            if (channel.length() > NetworkProtocol.MAX_CHANNEL_CHARS)
            {
                throw new IllegalArgumentException("channel id must be at most "
                        + NetworkProtocol.MAX_CHANNEL_CHARS + " chars but was "
                        + channel.length());
            }
            if (!unique.add(channel))
            {
                throw new IllegalArgumentException("channels must not contain duplicate id '"
                        + channel + "'");
            }
            validated.add(channel);
        }
        return List.copyOf(validated);
    }
}
