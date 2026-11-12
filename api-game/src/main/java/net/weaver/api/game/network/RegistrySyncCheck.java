package net.weaver.api.game.network;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.weaver.api.network.NetworkPhase;
import net.weaver.api.network.NetworkRegistry;
import net.weaver.api.network.PayloadType;

/**
 * Refuses a join when the client's game registries do not match the
 * server's. Right after the bridge handshake verifies, still in the
 * configuration phase and before any world data, the server sends its
 * {@link RegistryFingerprint} on {@code weaver:registry_check}; the client
 * compares it with its own and disconnects with the first difference ("registry minecraft:item differs
 * from the server: missing here examplemod:ruby"). Numeric ids are not
 * remapped; a mismatch is refused instead.
 *
 * <p>Vanilla peers never see this channel: the bridge only talks to peers
 * that answered its handshake.</p>
 */
public final class RegistrySyncCheck {
    public static final PayloadType<RegistryFingerprint> TYPE =
            PayloadType.of("weaver", "registry_check", RegistryFingerprint.CODEC);

    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static volatile String lastResult;

    private RegistrySyncCheck() {
    }

    /**
     * Registers the channel and the server-side sender. Loads no game class;
     * the registries are read only when a connection is made.
     */
    static void install(java.util.function.Consumer<String> log) {
        if (!INSTALLED.compareAndSet(false, true)) {
            return;
        }
        NetworkRegistry.global().registerClientbound("weaver", TYPE, NetworkPhase.CONFIGURATION, (server, context) -> {
            RegistryFingerprint local;
            String reason;
            try {
                local = local();
                reason = RegistryFingerprint.compare(server, local);
            } catch (RuntimeException failure) {
                // A fingerprint this client cannot compare must not let it in unchecked.
                lastResult = "check failed: " + failure;
                log.accept("[weaver] REGISTRY CHECK ok=false reason=check-failed " + failure);
                context.connection().disconnect("Registry check failed: " + failure.getMessage());
                return;
            }
            if (reason == null) {
                lastResult = "ok";
                log.accept("[weaver] REGISTRY CHECK ok=true registries=" + local.registries().size()
                        + " entries=" + local.entryCount());
            } else {
                lastResult = reason;
                log.accept("[weaver] REGISTRY CHECK ok=false reason=" + reason);
                context.connection().disconnect(reason);
            }
        });
        BridgeSession.onServerVerified(session -> {
            try {
                session.send(TYPE, local());
            } catch (RuntimeException failure) {
                // Refuse rather than let an unchecked client in.
                log.accept("[weaver] REGISTRY CHECK send FAILED reason=" + failure);
                session.disconnect("Registry check failed on the server: " + failure.getMessage());
            }
        });
    }

    /**
     * The client's last check result: {@code "ok"}, the disconnect reason, or
     * {@code null} when no server has sent a fingerprint yet. For diagnostics.
     */
    public static String lastResult() {
        return lastResult;
    }

    /**
     * The fingerprint of this game instance's registries. Only ids from mods
     * that must be installed on both sides (side {@code both}) count: server-only
     * and client-only mods may register things the other side never has, and the
     * handshake already allows such mods to differ.
     */
    public static RegistryFingerprint local() {
        java.util.Set<String> shared = new java.util.HashSet<>();
        for (net.weaver.api.WeaverMods.Mod mod : net.weaver.api.WeaverMods.loaded()) {
            if ("both".equals(mod.side())) {
                shared.add(mod.id());
            }
        }
        Map<String, List<String>> registries = new LinkedHashMap<>();
        for (Registry<?> registry : BuiltInRegistries.REGISTRY) {
            List<String> modded = moddedIds(registry, shared);
            if (!modded.isEmpty()) {
                registries.put(registry.key().identifier().toString(), modded);
            }
        }
        return RegistryFingerprint.of(registries);
    }

    private static <T> List<String> moddedIds(Registry<T> registry, java.util.Set<String> shared) {
        List<String> ids = new ArrayList<>();
        int size = registry.size();
        for (int numeric = 0; numeric < size; numeric++) {
            T value = registry.byId(numeric);
            if (value == null) {
                continue;
            }
            Identifier id = registry.getKey(value);
            if (id != null && shared.contains(id.getNamespace())) {
                ids.add(id.toString());
            }
        }
        return ids;
    }
}
