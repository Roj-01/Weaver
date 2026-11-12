package net.weaver.api.game.network;

import net.weaver.api.WeaverApi;
import net.weaver.api.network.HandshakeDecision;
import net.weaver.api.network.HandshakePolicy;
import net.weaver.api.network.HandshakeRequest;
import net.weaver.api.network.HandshakeResponse;
import net.weaver.api.network.NetworkException;
import net.weaver.api.network.NetworkModInfo;
import net.weaver.api.network.NetworkProtocol;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Validates bridge handshake messages without depending on the core transport.
 *
 * <p>The game bridge lives in {@code api-game}, which only depends on the API
 * artifact, so it cannot call {@code net.weaver.core.network.HandshakeVerifier}.
 * This class implements the same built-in checks, in the same order, with the
 * same rejection messages. The wire codecs are the API
 * {@link HandshakeRequest#CODEC} and {@link HandshakeResponse#CODEC}; only the
 * validation is repeated here.</p>
 *
 * <p>Both methods are pure: they do not read files, system properties, or game
 * state. Invalid peer data produces a rejected response rather than an
 * exception. Invalid local configuration throws
 * {@link IllegalArgumentException}.</p>
 */
final class BridgeHandshakeVerifier
{
    private BridgeHandshakeVerifier()
    {
        throw new AssertionError("No BridgeHandshakeVerifier instances");
    }

    /**
     * Validates a client request and produces the server response.
     *
     * @param request                 the client's handshake request, not
     *                                {@code null}
     * @param localMods               the server's mods in registration order
     * @param localChannels           the server's advertised channel ids
     * @param policy                  the optional handshake policy, or
     *                                {@code null}
     * @param expectedProtocolVersion the server's positive protocol version
     * @param localWeaverVersion      the server's Weaver version
     * @param localGameVersion        the server's game version
     * @return an accepted or rejected response, never {@code null}
     * @throws NullPointerException     when a required argument is {@code null}
     * @throws IllegalArgumentException when the local configuration is invalid
     */
    static HandshakeResponse verify(HandshakeRequest request, List<NetworkModInfo> localMods,
                                    List<String> localChannels, HandshakePolicy policy,
                                    int expectedProtocolVersion, String localWeaverVersion,
                                    String localGameVersion)
    {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(localMods, "localMods");
        Objects.requireNonNull(localChannels, "localChannels");
        Objects.requireNonNull(localWeaverVersion, "localWeaverVersion");
        Objects.requireNonNull(localGameVersion, "localGameVersion");
        if (expectedProtocolVersion < 1)
        {
            throw new IllegalArgumentException("expectedProtocolVersion must be >= 1 but was "
                    + expectedProtocolVersion);
        }
        requireLocalVersion(localWeaverVersion, "localWeaverVersion");
        requireLocalVersion(localGameVersion, "localGameVersion");
        List<NetworkModInfo> serverMods = validateLocalMods(localMods);
        List<String> serverChannels = validateLocalChannels(localChannels);

        if (request.protocolVersion() != expectedProtocolVersion)
        {
            return reject("protocol mismatch: client=" + request.protocolVersion()
                            + ", server=" + expectedProtocolVersion,
                    expectedProtocolVersion, localWeaverVersion, localGameVersion,
                    serverMods, serverChannels);
        }
        if (!request.gameVersion().equals(localGameVersion))
        {
            return reject("game version mismatch: client=" + request.gameVersion()
                            + ", server=" + localGameVersion,
                    expectedProtocolVersion, localWeaverVersion, localGameVersion,
                    serverMods, serverChannels);
        }
        if (!WeaverApi.isCompatibleApi(request.weaverVersion()))
        {
            return reject("weaver version mismatch: client=" + request.weaverVersion()
                            + ", server=" + localWeaverVersion + " (requires API "
                            + WeaverApi.MIN_SUPPORTED_API + " or newer within major "
                            + WeaverApi.API_MAJOR + ")",
                    expectedProtocolVersion, localWeaverVersion, localGameVersion,
                    serverMods, serverChannels);
        }

        Map<String, String> clientModVersions = new HashMap<>();
        for (NetworkModInfo clientMod : request.mods())
        {
            clientModVersions.put(clientMod.id(), clientMod.version());
        }
        for (NetworkModInfo serverMod : serverMods)
        {
            String clientVersion = clientModVersions.get(serverMod.id());
            if (clientVersion == null)
            {
                return reject("missing required mod: " + serverMod.id()
                                + " (server=" + serverMod.version() + ")",
                        expectedProtocolVersion, localWeaverVersion, localGameVersion,
                        serverMods, serverChannels);
            }
            if (!net.weaver.api.WeaverMods.networkCompatible(serverMod.id(), serverMod.version(), clientVersion))
            {
                return reject("mod version mismatch: " + serverMod.id()
                                + " (client=" + clientVersion + ", server="
                                + serverMod.version() + ")",
                        expectedProtocolVersion, localWeaverVersion, localGameVersion,
                        serverMods, serverChannels);
            }
        }

        if (policy != null)
        {
            HandshakeDecision decision = policy.evaluate(request, serverMods);
            if (decision == null)
            {
                return reject("handshake policy returned no decision",
                        expectedProtocolVersion, localWeaverVersion, localGameVersion,
                        serverMods, serverChannels);
            }
            if (!decision.accepted())
            {
                String reason = decision.reason();
                if (reason == null || reason.isBlank())
                {
                    reason = "handshake policy rejected the client";
                }
                return reject(reason, expectedProtocolVersion, localWeaverVersion,
                        localGameVersion, serverMods, serverChannels);
            }
        }

        return HandshakeResponse.accept(expectedProtocolVersion, localWeaverVersion,
                localGameVersion, serverMods, serverChannels);
    }

    /**
     * Verifies an accepted server response against the client's request.
     *
     * @param response the server response, not {@code null}
     * @param request  the client request, not {@code null}
     * @throws NullPointerException when an argument is {@code null}
     * @throws NetworkException     when the server rejected the client or the
     *                              advertised versions or mods are incompatible
     */
    static void checkResponse(HandshakeResponse response, HandshakeRequest request)
    {
        Objects.requireNonNull(response, "response");
        Objects.requireNonNull(request, "request");
        if (!response.accepted())
        {
            throw new NetworkException("handshake rejected: " + response.reason());
        }
        if (response.protocolVersion() != request.protocolVersion())
        {
            throw new NetworkException("protocol mismatch: client="
                    + request.protocolVersion() + ", server=" + response.protocolVersion());
        }
        if (!response.gameVersion().equals(request.gameVersion()))
        {
            throw new NetworkException("game version mismatch: client="
                    + request.gameVersion() + ", server=" + response.gameVersion());
        }
        if (!WeaverApi.isCompatibleApi(response.weaverVersion()))
        {
            throw new NetworkException("weaver version mismatch: client="
                    + request.weaverVersion() + ", server=" + response.weaverVersion()
                    + " (requires API " + WeaverApi.MIN_SUPPORTED_API
                    + " or newer within major " + WeaverApi.API_MAJOR + ")");
        }

        Map<String, String> clientModVersions = new HashMap<>();
        for (NetworkModInfo clientMod : request.mods())
        {
            clientModVersions.put(clientMod.id(), clientMod.version());
        }
        for (NetworkModInfo serverMod : response.mods())
        {
            String clientVersion = clientModVersions.get(serverMod.id());
            if (clientVersion == null)
            {
                throw new NetworkException("server requires mod: " + serverMod.id()
                        + " (server=" + serverMod.version() + ")");
            }
            if (!net.weaver.api.WeaverMods.clientAcceptsServer(serverMod.id(), clientVersion, serverMod.version()))
            {
                throw new NetworkException("mod version mismatch: " + serverMod.id()
                        + " (client=" + clientVersion + ", server="
                        + serverMod.version() + ")");
            }
        }
    }

    private static String requireLocalVersion(String version, String name)
    {
        if (version.isBlank())
        {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        if (version.length() > NetworkProtocol.MAX_STRING_CHARS)
        {
            throw new IllegalArgumentException(name + " must be at most "
                    + NetworkProtocol.MAX_STRING_CHARS + " chars but was "
                    + version.length());
        }
        return version;
    }

    private static List<NetworkModInfo> validateLocalMods(List<NetworkModInfo> localMods)
    {
        if (localMods.size() > NetworkProtocol.MAX_MODS)
        {
            throw new IllegalArgumentException("localMods must contain at most "
                    + NetworkProtocol.MAX_MODS + " entries but contained "
                    + localMods.size());
        }
        List<NetworkModInfo> copy = new ArrayList<>(localMods.size());
        Set<String> ids = new HashSet<>();
        for (NetworkModInfo mod : localMods)
        {
            if (mod == null)
            {
                throw new IllegalArgumentException("localMods must not contain null entries");
            }
            if (!ids.add(mod.id()))
            {
                throw new IllegalArgumentException("localMods must not contain duplicate id '"
                        + mod.id() + "'");
            }
            copy.add(mod);
        }
        return List.copyOf(copy);
    }

    private static List<String> validateLocalChannels(List<String> localChannels)
    {
        if (localChannels.size() > NetworkProtocol.MAX_CHANNELS)
        {
            throw new IllegalArgumentException("localChannels must contain at most "
                    + NetworkProtocol.MAX_CHANNELS + " entries but contained "
                    + localChannels.size());
        }
        List<String> copy = new ArrayList<>(localChannels.size());
        Set<String> unique = new HashSet<>();
        for (String channel : localChannels)
        {
            if (channel == null)
            {
                throw new IllegalArgumentException(
                        "localChannels must not contain null entries");
            }
            if (channel.isBlank())
            {
                throw new IllegalArgumentException(
                        "localChannels must not contain blank entries");
            }
            if (channel.length() > NetworkProtocol.MAX_CHANNEL_CHARS)
            {
                throw new IllegalArgumentException("local channel id must be at most "
                        + NetworkProtocol.MAX_CHANNEL_CHARS + " chars but was "
                        + channel.length());
            }
            if (!unique.add(channel))
            {
                throw new IllegalArgumentException(
                        "localChannels must not contain duplicate id '" + channel + "'");
            }
            copy.add(channel);
        }
        return List.copyOf(copy);
    }

    private static HandshakeResponse reject(String reason, int protocolVersion,
                                            String weaverVersion, String gameVersion,
                                            List<NetworkModInfo> mods,
                                            List<String> channels)
    {
        String boundedReason = reason.length() > NetworkProtocol.MAX_REASON_CHARS
                ? reason.substring(0, NetworkProtocol.MAX_REASON_CHARS)
                : reason;
        return HandshakeResponse.reject(boundedReason, protocolVersion, weaverVersion,
                gameVersion, mods, channels);
    }
}
