package net.weaver.nf;

import net.minecraft.core.RegistryAccess;
import net.minecraft.world.entity.player.Player;

/**
 * The client pieces Weaver NF needs, reached only when the client is present: the
 * client classes are touched inside {@link Bridge}, which is loaded on first use
 * on the client and never on a dedicated server.
 */
final class NfClient {
    private static final boolean PRESENT = present();

    private NfClient() {
    }

    private static boolean present() {
        try {
            Class.forName("net.minecraft.client.Minecraft", false, NfClient.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError absent) {
            return false;
        }
    }

    /** Whether this is a client. */
    static boolean isClient() {
        return PRESENT;
    }

    /** The client level's registry access, or null (no client, no level). */
    static RegistryAccess registryAccess() {
        return PRESENT ? Bridge.registryAccess() : null;
    }

    /** The local player, or null. */
    static Player player() {
        return PRESENT ? Bridge.player() : null;
    }

    /** Runs {@code work} on the client thread (right away where there is no client). */
    static void execute(Runnable work) {
        if (PRESENT) {
            Bridge.execute(work);
        } else {
            work.run();
        }
    }

    private static final class Bridge {
        static RegistryAccess registryAccess() {
            var minecraft = net.minecraft.client.Minecraft.getInstance();
            return minecraft == null || minecraft.level == null ? null : minecraft.level.registryAccess();
        }

        static Player player() {
            var minecraft = net.minecraft.client.Minecraft.getInstance();
            return minecraft == null ? null : minecraft.player;
        }

        static void execute(Runnable work) {
            var minecraft = net.minecraft.client.Minecraft.getInstance();
            if (minecraft == null) {
                work.run();
            } else {
                minecraft.execute(work);
            }
        }
    }
}
