package net.weaver.api.game.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.weaver.api.WeaverMods;
import net.weaver.api.annotation.Internal;
import net.weaver.api.network.NetworkModInfo;

/**
 * Tells Weaver Hub, the launcher that started the game, when joining a server
 * needs a different set of mods. Only active when the Hub started the game
 * ({@code -Dweaver.hub=true}); otherwise joins behave as before.
 *
 * <p>Each request is one line on standard output, which the Hub reads live:</p>
 * <pre>{@code
 * [weaver-hub] {"type":"server-mods","server":"play.example.com:25565","missing":[…],"different":[…]}
 * [weaver-hub] {"type":"vanilla-server","server":"play.example.com:25565"}
 * }</pre>
 * <p>The Hub then asks the player, fetches the server's mods (whose exact files it
 * reads from the server's status reply), and restarts the game into the server.
 * Nothing is downloaded or run by the game itself.</p>
 */
@Internal
public final class HubLink
{
    /** Set by Weaver Hub on the games it starts. */
    public static final String PROPERTY = "weaver.hub";
    /** The prefix of every request line. */
    public static final String PREFIX = "[weaver-hub] ";

    private HubLink()
    {
    }

    /** Whether Weaver Hub started this game. */
    public static boolean isHubManaged()
    {
        return Boolean.getBoolean(PROPERTY);
    }

    /** {@code host:port} of a connection's peer, or an empty string. */
    public static String address(SocketAddress remote)
    {
        if (remote instanceof InetSocketAddress inet)
        {
            String host = inet.getHostString();
            return (host.contains(":") ? "[" + host + "]" : host) + ":" + inet.getPort();
        }
        return "";
    }

    /** The client's mods that must also be on the server: those with side {@code both}. */
    public static List<WeaverMods.Mod> serverBoundMods()
    {
        List<WeaverMods.Mod> mods = new ArrayList<>();
        for (WeaverMods.Mod mod : WeaverMods.loaded())
        {
            if ("both".equals(mod.side()))
            {
                mods.add(mod);
            }
        }
        return mods;
    }

    /**
     * Compares the server's announced mods with this client's. Returns the request
     * for the Hub when the client lacks a server mod or has another version of one,
     * or null when the sets match.
     */
    public static JsonObject compare(String server, List<NetworkModInfo> serverMods)
    {
        Map<String, String> local = new HashMap<>();
        for (WeaverMods.Mod mod : serverBoundMods())
        {
            local.put(mod.id(), mod.version());
        }
        JsonArray missing = new JsonArray();
        JsonArray different = new JsonArray();
        for (NetworkModInfo mod : serverMods)
        {
            String version = local.get(mod.id());
            if (version == null)
            {
                missing.add(entry(mod.id(), mod.version(), null));
            }
            else if (!WeaverMods.networkCompatible(mod.id(), version, mod.version())
                    || !WeaverMods.clientAcceptsServer(mod.id(), version, mod.version()))
            {
                different.add(entry(mod.id(), mod.version(), version));
            }
        }
        if (missing.isEmpty() && different.isEmpty())
        {
            return null;
        }
        JsonObject request = new JsonObject();
        request.addProperty("type", "server-mods");
        request.addProperty("server", server);
        request.add("missing", missing);
        request.add("different", different);
        return request;
    }

    /** The request for joining a vanilla server while mods that need a server are loaded, or null. */
    public static JsonObject vanilla(String server)
    {
        List<WeaverMods.Mod> needServer = serverBoundMods();
        if (needServer.isEmpty())
        {
            return null;
        }
        JsonObject request = new JsonObject();
        request.addProperty("type", "vanilla-server");
        request.addProperty("server", server);
        JsonArray paused = new JsonArray();
        for (WeaverMods.Mod mod : needServer)
        {
            paused.add(mod.id());
        }
        request.add("paused", paused);
        return request;
    }

    /** Sends a request to the Hub when the Hub started this game; returns whether it was sent. */
    public static boolean send(JsonObject request)
    {
        if (request == null || !isHubManaged())
        {
            return false;
        }
        System.out.println(PREFIX + request);
        System.out.flush();
        return true;
    }

    /** The peer address of each client session, recorded when the game opens it. */
    private static final Map<BridgeSession, String> PEERS = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    static void remember(BridgeSession session, SocketAddress remote)
    {
        if (session != null && session.side() == net.weaver.api.network.NetworkSide.CLIENT)
        {
            PEERS.put(session, address(remote));
        }
    }

    /** The server's HELLO arrived: tell the Hub when its mods differ from this client's. */
    static void onServerHello(BridgeSession session, List<NetworkModInfo> serverMods)
    {
        if (!isHubManaged())
        {
            return;
        }
        try
        {
            send(compare(PEERS.getOrDefault(session, ""), serverMods));
        }
        catch (RuntimeException failure)
        {
            System.out.println("[weaver] HUB LINK failed reason=" + failure);
        }
    }

    private static JsonObject entry(String id, String version, String local)
    {
        JsonObject entry = new JsonObject();
        entry.addProperty("id", id);
        entry.addProperty("version", version);
        if (local != null)
        {
            entry.addProperty("local", local);
        }
        return entry;
    }
}
