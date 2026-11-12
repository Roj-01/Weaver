package net.weaver.sourceproof;

import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.client.Minecraft;
import net.weaver.api.client.ClientRegistry;

/** Client-only probe registration; this class is loaded from onClientSetup only. */
final class SourcePatchProofClient {

    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private SourcePatchProofClient() {
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
                SourcePatchProofMod.verifyClientProbe();
            });
        } catch (Throwable failure) {
            SourcePatchProofMod.fail("client", "register:" + SourcePatchProofMod.describe(failure));
        }
    }
}
