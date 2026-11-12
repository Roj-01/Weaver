package net.weaver.patchedproof;

import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.client.Minecraft;
import net.weaver.api.client.ClientRegistry;

/** Client-only probe registration; this class is loaded from onClientSetup only. */
final class PatchedProofClient {

    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private PatchedProofClient() {
    }

    static void install() {
        if (!INSTALLED.compareAndSet(false, true)) {
            return;
        }
        try {
            ClientRegistry.registerTickListener(context -> {
                Minecraft minecraft;
                try {
                    minecraft = Minecraft.getInstance();
                } catch (Throwable failure) {
                    return;
                }
                if (minecraft == null || minecraft.player == null) {
                    return;
                }
                PatchedProofMod.verifyClientProbe();
            });
        } catch (Throwable failure) {
            PatchedProofMod.fail("client", "register:" + PatchedProofMod.describe(failure));
        }
    }
}
