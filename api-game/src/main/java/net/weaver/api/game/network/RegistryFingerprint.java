package net.weaver.api.game.network;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.weaver.api.network.NetworkProtocol;
import net.weaver.api.network.PayloadBuffer;
import net.weaver.api.network.PayloadCodec;

/**
 * The modded part of the game registries: for every registry that holds
 * entries outside the {@code minecraft} namespace, how many there are and a
 * SHA-256 digest of their ids in numeric id order. Two game instances with
 * the same game version agree on vanilla entries, so equal fingerprints mean
 * equal numeric ids for every entry.
 *
 * <p>The full id lists travel too while they fit {@link #DETAIL_BYTES}, so a
 * mismatch can name the entries that differ; past that budget only the
 * count and digest are sent, so the check still works for any number of
 * entries. This class is game-free; {@code RegistrySyncCheck} builds it from
 * the live registries.</p>
 *
 * @param registries registry id to its part; iteration order is the registry order
 */
public record RegistryFingerprint(Map<String, Part> registries) {
    /** Upper bound on registries in one fingerprint. */
    public static final int MAX_REGISTRIES = 4096;
    /** Upper bound on listed ids in one decoded fingerprint. */
    public static final int MAX_LISTED_IDS = 65_536;
    /** Roughly how many bytes of id lists are sent, to stay well inside the payload limit. */
    public static final int DETAIL_BYTES = 512 * 1024;

    /**
     * One registry's modded entries.
     *
     * @param count  the number of modded entries
     * @param digest the lower-case hex SHA-256 of the ids in numeric order, one per line
     * @param ids    the ids in numeric order, or empty when they were not sent
     */
    public record Part(int count, String digest, List<String> ids) {
        public Part {
            Objects.requireNonNull(digest, "digest");
            ids = List.copyOf(ids);
            if (count < 0) {
                throw new IllegalArgumentException("negative entry count");
            }
        }

        /** Whether {@link #ids} holds every entry. */
        public boolean listed() {
            return ids.size() == count;
        }

        static Part of(List<String> ids) {
            return new Part(ids.size(), RegistryFingerprint.digest(ids), ids);
        }

        Part withoutIds() {
            return new Part(count, digest, List.of());
        }
    }

    private static final Part NONE = Part.of(List.of());

    public static final PayloadCodec<RegistryFingerprint> CODEC = new PayloadCodec<>() {
        @Override
        public void encode(RegistryFingerprint value, PayloadBuffer out) {
            out.writeVarInt(value.registries().size());
            int budget = DETAIL_BYTES;
            for (Map.Entry<String, Part> registry : value.registries().entrySet()) {
                Part part = registry.getValue();
                int size = 0;
                for (String id : part.ids()) {
                    size += id.length() + 2;
                }
                boolean listed = part.listed() && size <= budget;
                if (listed) {
                    budget -= size;
                }
                out.writeString(registry.getKey());
                out.writeVarInt(part.count());
                out.writeString(part.digest());
                out.writeBoolean(listed);
                if (listed) {
                    for (String id : part.ids()) {
                        out.writeString(id);
                    }
                }
            }
        }

        @Override
        public RegistryFingerprint decode(PayloadBuffer in) {
            int registryCount = in.readVarInt();
            if (registryCount < 0 || registryCount > MAX_REGISTRIES) {
                throw new IllegalArgumentException("registry count out of range: " + registryCount);
            }
            Map<String, Part> registries = new LinkedHashMap<>();
            int listedTotal = 0;
            for (int i = 0; i < registryCount; i++) {
                String registry = in.readString();
                int count = in.readVarInt();
                if (count < 0) {
                    throw new IllegalArgumentException("registry entry count out of range");
                }
                String digest = in.readString();
                List<String> ids = List.of();
                if (in.readBoolean()) {
                    listedTotal += count;
                    if (listedTotal > MAX_LISTED_IDS) {
                        throw new IllegalArgumentException("too many listed registry ids");
                    }
                    List<String> read = new ArrayList<>(count);
                    for (int j = 0; j < count; j++) {
                        read.add(in.readString());
                    }
                    ids = read;
                }
                registries.put(registry, new Part(count, digest, ids));
            }
            return new RegistryFingerprint(registries);
        }
    };

    public RegistryFingerprint {
        Objects.requireNonNull(registries, "registries");
        Map<String, Part> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Part> registry : registries.entrySet()) {
            copy.put(Objects.requireNonNull(registry.getKey(), "registry id"),
                    Objects.requireNonNull(registry.getValue(), "part"));
        }
        registries = java.util.Collections.unmodifiableMap(copy);
    }

    /** A fingerprint of full id lists: registry id to its modded ids in numeric order. */
    public static RegistryFingerprint of(Map<String, List<String>> ids) {
        Map<String, Part> parts = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> registry : ids.entrySet()) {
            parts.put(registry.getKey(), Part.of(registry.getValue()));
        }
        return new RegistryFingerprint(parts);
    }

    /** The same fingerprint without id lists, as a peer past the detail budget sees it. */
    public RegistryFingerprint withoutIds() {
        Map<String, Part> parts = new LinkedHashMap<>();
        registries.forEach((id, part) -> parts.put(id, part.withoutIds()));
        return new RegistryFingerprint(parts);
    }

    /** The number of modded entries across all registries. */
    public int entryCount() {
        int total = 0;
        for (Part part : registries.values()) {
            total += part.count();
        }
        return total;
    }

    /**
     * Compares the server's fingerprint with the client's.
     *
     * @return {@code null} when they match, otherwise a reason a player can
     *         read, at most {@link NetworkProtocol#MAX_REASON_CHARS} characters,
     *         naming the first registry that differs and how
     */
    public static String compare(RegistryFingerprint server, RegistryFingerprint client) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(client, "client");
        Set<String> names = new java.util.LinkedHashSet<>(server.registries().keySet());
        names.addAll(client.registries().keySet());
        for (String registry : names) {
            Part serverPart = server.registries().getOrDefault(registry, NONE);
            Part clientPart = client.registries().getOrDefault(registry, NONE);
            if (serverPart.count() == clientPart.count() && serverPart.digest().equals(clientPart.digest())) {
                continue;
            }
            String how = serverPart.listed() && clientPart.listed()
                    ? describe(serverPart.ids(), clientPart.ids())
                    : serverPart.count() + " modded entries on the server, " + clientPart.count() + " here"
                            + (serverPart.count() == clientPart.count() ? " (different entries or order)" : "");
            return clip("registry " + registry + " differs from the server: " + how);
        }
        return null;
    }

    private static String describe(List<String> serverIds, List<String> clientIds) {
        Set<String> clientSet = new HashSet<>(clientIds);
        Set<String> serverSet = new HashSet<>(serverIds);
        List<String> missing = new ArrayList<>();
        for (String id : serverIds) {
            if (!clientSet.contains(id)) {
                missing.add(id);
            }
        }
        List<String> extra = new ArrayList<>();
        for (String id : clientIds) {
            if (!serverSet.contains(id)) {
                extra.add(id);
            }
        }
        StringBuilder reason = new StringBuilder();
        if (!missing.isEmpty()) {
            reason.append("missing here ").append(sample(missing));
        }
        if (!extra.isEmpty()) {
            if (reason.length() > 0) {
                reason.append("; ");
            }
            reason.append("not on the server ").append(sample(extra));
        }
        if (reason.length() == 0) {
            for (int i = 0; i < Math.min(serverIds.size(), clientIds.size()); i++) {
                if (!serverIds.get(i).equals(clientIds.get(i))) {
                    reason.append("same entries in a different order (first: ").append(serverIds.get(i))
                            .append(" is entry ").append(i + 1).append(" of the modded entries on the server, ")
                            .append(clientIds.get(i)).append(" here)");
                    break;
                }
            }
        }
        return reason.toString();
    }

    private static String sample(List<String> ids) {
        int shown = Math.min(3, ids.size());
        String listed = String.join(", ", ids.subList(0, shown));
        return ids.size() > shown ? listed + " and " + (ids.size() - shown) + " more" : listed;
    }

    private static String clip(String reason) {
        int max = NetworkProtocol.MAX_REASON_CHARS;
        return reason.length() <= max ? reason : reason.substring(0, max - 3) + "...";
    }

    private static String digest(List<String> ids) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            for (String id : ids) {
                sha.update(id.getBytes(StandardCharsets.UTF_8));
                sha.update((byte) '\n');
            }
            return HexFormat.of().formatHex(sha.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
